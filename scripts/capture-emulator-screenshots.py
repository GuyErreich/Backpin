#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Install the debug APK, seed Today, enable the Today notice, and screenshot it.

The Today notice is not a debug intent and not a scheduled alarm. NotificationCoordinator
posts it while the process is up when:
  * Settings "Show current task" is on (DataStore current_notification_enabled)
  * POST_NOTIFICATIONS is granted
  * Today has at least one open task
This script drives that path, then waits on dumpsys / UI state instead of fixed sleeps.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "app.backpin.focus"
ACTIVITY = "app.backpin.focus/.MainActivity"
APK = Path("app/build/outputs/apk/debug/app-debug.apk")
OUT_DIR = Path("screenshots")
TASK_TITLE = "Walk the dog"
POLL = 0.25
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(args: list[str], check: bool = True) -> subprocess.CompletedProcess[bytes]:
    proc = subprocess.run(["adb", *args], capture_output=True)
    if proc.returncode != 0:
        err = proc.stderr.decode("utf-8", "replace").strip()
        out = proc.stdout.decode("utf-8", "replace").strip()
        print(f"adb {args[0]} rc={proc.returncode}: {err or out}")
        if check:
            proc.check_returncode()
    return proc


def adb_out(args: list[str]) -> str:
    proc = adb(args, check=False)
    return proc.stdout.decode("utf-8", "replace") + proc.stderr.decode("utf-8", "replace")


def wait_until(predicate, timeout: float, what: str, required: bool = True) -> bool:
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            if predicate():
                return True
        except Exception as exc:  # dump can race with animations
            last_error = exc
        time.sleep(POLL)
    extra = f" ({last_error})" if last_error else ""
    message = f"timed out waiting for {what}{extra}"
    if required:
        raise SystemExit(message)
    print(message)
    return False


def wait_boot() -> None:
    adb(["wait-for-device"])
    wait_until(
        lambda: adb_out(["shell", "getprop", "sys.boot_completed"]).strip() == "1",
        120,
        "sys.boot_completed",
    )


def set_412dp_display() -> None:
    # Same math as the workflow comment: 1030x2288 @ 400 dpi = 412 dp wide.
    adb(["shell", "wm", "size", "1030x2288"])
    adb(["shell", "wm", "density", "400"])

    def sized() -> bool:
        size = adb_out(["shell", "wm", "size"])
        density = adb_out(["shell", "wm", "density"])
        return "1030x2288" in size and "400" in density

    wait_until(sized, 30, "wm size 1030x2288 @ 400 dpi")


def screen_size() -> tuple[int, int]:
    text = adb_out(["shell", "wm", "size"])
    matches = re.findall(r"(\d+)x(\d+)", text)
    if not matches:
        return 1030, 2288
    width, height = matches[-1]
    return int(width), int(height)


def dump_ui() -> ET.Element:
    dest = "/sdcard/uidump.xml"
    proc = adb(["shell", "uiautomator", "dump", "--compressed", dest], check=False)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.decode("utf-8", "replace") or "uiautomator dump failed")
    xml_text = adb_out(["shell", "cat", dest])
    start = xml_text.find("<?xml")
    if start < 0:
        start = xml_text.find("<hierarchy")
    if start < 0:
        raise RuntimeError("uiautomator dump was empty")
    return ET.fromstring(xml_text[start:])


def parse_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(node.attrib.get("bounds", ""))
    if not match:
        return None
    return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def node_text(node: ET.Element) -> str:
    return " ".join(
        part
        for part in (
            node.attrib.get("text", ""),
            node.attrib.get("content-desc", ""),
        )
        if part
    )


def walk(root: ET.Element):
    yield root
    for child in list(root):
        yield from walk(child)


def find_nodes(root: ET.Element, needle: str) -> list[ET.Element]:
    needle_l = needle.casefold()
    found = []
    for node in walk(root):
        hay = node_text(node).casefold()
        if needle_l in hay:
            found.append(node)
    return found


def tap_bounds(box: tuple[int, int, int, int], x_ratio: float = 0.5) -> None:
    left, top, right, bottom = box
    x = int(left + (right - left) * x_ratio)
    y = int((top + bottom) / 2)
    adb(["shell", "input", "tap", str(x), str(y)])


def tap_text(needle: str, timeout: float = 20, x_ratio: float = 0.5) -> None:
    def found() -> bool:
        root = dump_ui()
        nodes = find_nodes(root, needle)
        if not nodes:
            return False
        box = parse_bounds(nodes[0])
        if box is None:
            return False
        tap_bounds(box, x_ratio=x_ratio)
        return True

    wait_until(found, timeout, f"tap {needle!r}")


def ui_has(needle: str) -> bool:
    try:
        return bool(find_nodes(dump_ui(), needle))
    except Exception:
        return False


def scroll_until(needle: str, timeout: float = 40) -> None:
    width, height = screen_size()
    x = width // 2
    y1 = int(height * 0.78)
    y0 = int(height * 0.28)

    def visible() -> bool:
        if ui_has(needle):
            return True
        adb(["shell", "input", "swipe", str(x), str(y1), str(x), str(y0), "300"])
        return False

    wait_until(visible, timeout, f"scroll to {needle!r}")


def hide_keyboard() -> None:
    # BACK can close the capture sheet; ESCAPE only dismisses IME when it is open.
    adb(["shell", "input", "keyevent", "111"], check=False)


def tap_title_field() -> None:
    root = dump_ui()
    for node in walk(root):
        cls = node.attrib.get("class", "")
        focused = node.attrib.get("focused") == "true"
        if "EditText" in cls or (focused and "Edit" in cls):
            box = parse_bounds(node)
            if box is not None:
                tap_bounds(box)
                return
    if ui_has("A stray thought"):
        tap_text("A stray thought")
    elif ui_has("Title"):
        tap_text("Title")


def type_title(title: str) -> None:
    if not ui_has("Save to Inbox"):
        tap_text("+ Capture")
        wait_until(lambda: ui_has("Save to Inbox"), 20, "capture sheet")
    tap_title_field()
    encoded = title.replace(" ", "%s")
    adb(["shell", "input", "text", encoded])
    wait_until(lambda: ui_has(title), 15, f"typed {title!r}")
    hide_keyboard()


def notification_dump() -> str:
    return adb_out(["shell", "dumpsys", "notification", "--noredact"])


def today_notice_posted() -> bool:
    # Channel definitions also mention focus_today_ongoing. A live notice is a
    # NotificationRecord for this package with ID_CURRENT (41).
    for line in notification_dump().splitlines():
        if "NotificationRecord" not in line:
            continue
        if PACKAGE in line and "id=41" in line:
            return True
    return False


def log_notice_state(label: str) -> None:
    dump = notification_dump()
    posted = PACKAGE in dump and (
        "focus_today_ongoing" in dump or "focus_current_ongoing" in dump
    )
    print(f"{label}: today_notice_posted={posted}")
    for line in dump.splitlines():
        if "NotificationRecord" in line and PACKAGE in line:
            print(f"  {line[:240]}")
        elif "android.title" in line or "android.text" in line:
            print(f"  {line.strip()[:240]}")


def grant_notifications() -> None:
    adb(["shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS"], check=False)
    adb(["shell", "appops", "set", PACKAGE, "POST_NOTIFICATION", "allow"], check=False)


def device_online() -> bool:
    return adb_out(["get-state"]).strip() == "device"


def pm_ready() -> bool:
    return "package:" in adb_out(["shell", "pm", "path", "android"])


def install_and_launch() -> None:
    if not APK.is_file():
        raise SystemExit(f"missing {APK}; run ./gradlew assembleDebug first")
    wait_until(device_online, 60, "adb device")
    wait_until(pm_ready, 60, "package manager")
    last_install = [0.0]

    def installed() -> bool:
        now = time.monotonic()
        if now - last_install[0] < 2:
            return False
        last_install[0] = now
        if not device_online():
            return False
        proc = subprocess.run(["adb", "install", "-r", "-g", str(APK)], capture_output=True)
        text = (proc.stdout + proc.stderr).decode("utf-8", "replace")
        print(f"adb install rc={proc.returncode}: {text[:400]}")
        return proc.returncode == 0

    wait_until(installed, 90, "debug APK installed")
    grant_notifications()
    adb(["shell", "am", "force-stop", PACKAGE], check=False)
    adb(["shell", "am", "start", "-W", "-n", ACTIVITY])
    wait_until(lambda: ui_has("Keep your place.") or ui_has("FOCUS STACK"), 60, "Home")
    wait_until(lambda: not ui_has("Loading your place…"), 30, "Home ready")


def place_today_task() -> None:
    if ui_has(TASK_TITLE):
        return
    tap_text("+ Capture")
    wait_until(lambda: ui_has("Save to Inbox"), 20, "capture destinations")
    type_title(TASK_TITLE)
    wait_until(lambda: ui_has("Today"), 15, "Today destination")
    # Prefer the destination button, not the Home tab or pile row.
    root = dump_ui()
    candidates = find_nodes(root, "Today")
    tapped = False
    for node in candidates:
        box = parse_bounds(node)
        if box is None:
            continue
        # Capture choices sit in the lower half while the Home tab sits in the nav bar.
        _left, top, _right, bottom = box
        _, height = screen_size()
        if top < height * 0.25:
            continue
        tap_bounds(box)
        tapped = True
        break
    if not tapped:
        tap_text("Today")
    wait_until(lambda: ui_has(TASK_TITLE) and not ui_has("Save to Inbox"), 30, "task on Today")


def enable_today_notice() -> None:
    tap_text("Settings")
    wait_until(lambda: ui_has("Kept on this phone.") or ui_has("SETTINGS"), 20, "Settings")
    if ui_has("Not now"):
        tap_text("Not now")
    scroll_until("Show current task")
    root = dump_ui()
    labels = find_nodes(root, "Show current task")
    if not labels:
        raise SystemExit("Show current task is not on Settings")
    label_box = parse_bounds(labels[0])
    if label_box is None:
        raise SystemExit("Show current task has no bounds")
    label_top, label_bottom = label_box[1], label_box[3]
    switch = None
    for node in walk(root):
        if node.attrib.get("checkable") != "true":
            continue
        box = parse_bounds(node)
        if box is None:
            continue
        left, top, right, bottom = box
        if bottom < label_top - 40 or top > label_bottom + 40:
            continue
        if left < label_box[0]:
            continue
        switch = box
        break
    width, _ = screen_size()
    label_cy = (label_box[1] + label_box[3]) // 2
    if switch is not None:
        tap_bounds(switch)
    else:
        adb(["shell", "input", "tap", str(width - 100), str(label_cy)])
    if not wait_until(today_notice_posted, 20, "Today notice record id=41", required=False):
        adb(["shell", "input", "tap", str(width - 100), str(label_cy)])
        wait_until(today_notice_posted, 25, "Today notice record id=41")
    log_notice_state("after enabling Show current task")


def screenshot(name: str) -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    dest = OUT_DIR / name
    png = adb(["exec-out", "screencap", "-p"]).stdout
    if not png.startswith(b"\x89PNG"):
        raise SystemExit(f"screencap for {name} was not a PNG ({png[:16]!r})")
    dest.write_bytes(png)
    print(f"wrote {dest} ({len(png)} bytes)")
    return dest


def shade_window_open() -> bool:
    dump = adb_out(["shell", "dumpsys", "window"])
    focus = [line for line in dump.splitlines() if "mCurrentFocus" in line or "mFocusedWindow" in line]
    for line in focus:
        if "NotificationShade" in line:
            return True
    bar = adb_out(["shell", "dumpsys", "statusbar"])
    lowered = bar.casefold()
    return "mexpandedvisible=true" in lowered or "mpanelexpanded: true" in lowered


def shade_has_notice_text() -> bool:
    try:
        root = dump_ui()
    except Exception as exc:
        print(f"shade dump failed: {exc}")
        return False
    texts = [node_text(node) for node in walk(root)]
    blob = " | ".join(t for t in texts if t)
    print(f"shade dump texts: {blob[:500]}")
    return "1 task" in blob or TASK_TITLE in blob or "Backpin" in blob


def open_shade() -> None:
    if not today_notice_posted():
        log_notice_state("open_shade")
        raise SystemExit("Today notice is not posted; cannot screenshot the shade")
    adb(["shell", "cmd", "statusbar", "collapse"], check=False)
    wait_until(lambda: not shade_window_open(), 8, "shade collapsed", required=False)
    adb(["shell", "cmd", "statusbar", "expand-notifications"])
    wait_until(shade_window_open, 12, "notification shade", required=False)
    width, height = screen_size()
    x = width // 2
    y1 = int(height * 0.72)
    y0 = int(height * 0.28)
    if not wait_until(shade_has_notice_text, 4, "Today notice in shade", required=False):
        for _ in range(5):
            adb(["shell", "input", "swipe", str(x), str(y1), str(x), str(y0), "300"])
            if shade_has_notice_text():
                break
        else:
            log_notice_state("shade missing Today notice")
            screenshot("shade-debug.png")
            raise SystemExit("Today notice is posted but not visible in the shade")
    log_notice_state("shade opened")


def notice_bounds() -> tuple[int, int, int, int] | None:
    try:
        root = dump_ui()
    except Exception:
        return None
    for needle in ("1 task", TASK_TITLE, "Anytime"):
        for node in find_nodes(root, needle):
            box = parse_bounds(node)
            if box is not None:
                return box
    return None


def expand_today_notice() -> None:
    width, height = screen_size()
    box = notice_bounds()
    if box is not None:
        left, top, right, bottom = box
        x = (left + right) // 2
        start_y = (top + bottom) // 2
    else:
        x = width // 2
        start_y = int(height * 0.16)
    end_y = min(height - 80, start_y + int(height * 0.28))
    print(f"expand swipe {x},{start_y} -> {x},{end_y}")
    adb(["shell", "input", "swipe", str(x), str(start_y), str(x), str(end_y), "400"])
    wait_until(
        lambda: shade_has_notice_text() or shade_window_open(),
        15,
        "expanded Today notice",
        required=False,
    )


def main() -> int:
    print(f"CHECKOUT_REF={os.environ.get('CHECKOUT_REF', '')}")
    wait_boot()
    set_412dp_display()
    install_and_launch()
    place_today_task()
    enable_today_notice()
    tap_text("Home")
    wait_until(lambda: ui_has("Keep your place.") and ui_has(TASK_TITLE), 20, "Home with task")
    adb(["shell", "cmd", "statusbar", "collapse"], check=False)
    wait_until(lambda: ui_has("Keep your place."), 10, "shade collapsed on Home")
    screenshot("home.png")
    open_shade()
    screenshot("today-collapsed.png")
    expand_today_notice()
    screenshot("today-expanded.png")
    adb(["shell", "cmd", "statusbar", "collapse"], check=False)
    for name in ("home.png", "today-collapsed.png", "today-expanded.png"):
        path = OUT_DIR / name
        if not path.is_file() or path.stat().st_size < 1000:
            raise SystemExit(f"{path} is missing or too small")
    print("captured Home and Today notice screenshots")
    return 0


if __name__ == "__main__":
    sys.exit(main())
