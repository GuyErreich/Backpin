#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Create or patch the emulator AVD used by .github/workflows/emulator-screenshots.yml.
#
# 1030x2288 px at 400 dpi is exactly 412 dp wide:
#   1030 * 160 / 400 = 412
#   2288 * 160 / 400 = 915.2 (about 20:9)
set -euo pipefail

AVD_NAME="backpin_412dp"
IMAGE="system-images;android-34;google_apis;x86_64"
AVD_DIR="${HOME}/.android/avd/${AVD_NAME}.avd"
CONFIG="${AVD_DIR}/config.ini"

create_avd() {
  mkdir -p "${HOME}/.android/avd"
  echo no | avdmanager create avd --force \
    --name "${AVD_NAME}" \
    --package "${IMAGE}"
}

patch_config() {
  if [[ ! -f "${CONFIG}" ]]; then
    echo "AVD config missing: ${CONFIG}" >&2
    exit 1
  fi
  python3 - "${CONFIG}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
values = {
    "hw.lcd.width": "1030",
    "hw.lcd.height": "2288",
    "hw.lcd.density": "400",
    "skin.name": "1030x2288",
    "skin.dynamic": "yes",
    "hw.gpu.enabled": "yes",
    "hw.gpu.mode": "swiftshader_indirect",
}
lines = path.read_text().splitlines()
keys_seen = set()
out = []
for line in lines:
    if "=" not in line or line.lstrip().startswith("#"):
        out.append(line)
        continue
    key = line.split("=", 1)[0].strip()
    if key in values:
        out.append(f"{key}={values[key]}")
        keys_seen.add(key)
    else:
        out.append(line)
for key, value in values.items():
    if key not in keys_seen:
        out.append(f"{key}={value}")
text = "\n".join(out) + "\n"
path.write_text(text)
print(f"patched {path} for 412 dp (1030x2288 @ 400 dpi)")
PY
}

mode="${1:---create}"
case "${mode}" in
  --create)
    create_avd
    patch_config
    ;;
  --patch-only)
    patch_config
    ;;
  *)
    echo "usage: $0 [--create|--patch-only]" >&2
    exit 2
    ;;
esac
