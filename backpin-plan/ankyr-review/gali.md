# Ankyr review — Backpin test suite (gali)

Read-only pass over `app/src/test` (76 Kotlin files) and `app/src/androidTest` (missing).
Reviewed against `ankyr-testing` (`GuyErreich/AI_Agents` `plugins/ankyr/skills/ankyr-testing/SKILL.md`) and Backpin’s clock / sleep / flow / Compose / kit rules.
Shared kit referenced below lives on `origin/cursor/test-fixtures-kit-cc36` (`FakeAppClock`, `DEFAULT_TEST_ZONE`, `TestFocusFixture`, `MainDispatcherRule`, `awaitState`, `sampleTask` / `sampleRepeat`). It is not on `dev` yet.

**Counts:** 2 blocker · 10 should · 5 nit

---

## Blocker

### B1. `BandBoundaryTickTest` synchronizes RepeatsService timers with `Thread.sleep`

- **Where:** `app/src/test/java/app/backpin/focus/repeats/BandBoundaryTickTest.kt:55`, `:69`, `:97`, `:117`, `:161`, `:216-220`
- **Rule:** Backpin — no fixed sleeps. ankyr — fake the clock seam; do not wait on wall time.
- **Why:** `RepeatsService.emitAtEachBandBoundary` waits with `withTimeoutOrNull(wait)` on `Dispatchers.Default` (`:146-154`). The test then sleeps 50–250 ms and polls with `System.currentTimeMillis()` + `Thread.sleep(10)`. Load, a slow CI host, or a 150 ms bound window makes this miss or double-fire. This is the only remaining sleep-driven timer suite after #49 moved HomeBands onto a test scheduler.
- **Fix:** Build the service through `TestFocusFixture.open(repeatsContext = context, repeatsScope = testScope)` (or the local harness already used in `RepeatsServiceDayRefreshTest`). Drive `clock.set` / `advanceBy` and `scheduler.advanceTimeBy(wait); scheduler.runCurrent()`. Delete `LOOP_ARMED_MS`, `AFTER_CANCEL_MS`, and `waitUntil`.

### B2. Merged-tree Compose asserts right after a ViewModel wait

- **Where (live flake):** `app/src/test/java/app/backpin/focus/ui/repeat/RepeatHomeSpecTest.kt:392-404` — `headerShowsLeftCountAndAllDoneWhenTheLastOpenRowIsCheckedOff` (issue #46)
- **Same pattern:** `RepeatHomeDayRefreshTest.kt:99-100`, `:140-141`, `:198-199`; `HomeLayoutBandsHeaderTest.kt:86-87`; `RepeatsMissedHeaderQaTest.kt:75-80`; `BandTimeSettingsTest.kt:190-195`; `ThemeLaunchNoFlashTest.kt:55-67`; plus the inventory at the end of this finding
- **Rule:** Backpin — no merged-tree `onNodeWithText` / `onNodeWithTag` / `assertIsDisplayed` / `onAllNodes` immediately after `waitUntil` / `await { viewModel… }` / `flow.first()`.
- **Why:** `await` returns when `uiState` / `repeatRows` has the new value. Composition, semantics, and the merged tree can still be a frame behind. #46 is this race on the REPEAT header. `useUnmergedTree = true` on a later lookup does not fix a wait that only watched the ViewModel.
- **Fix:** Wait for the node inside the predicate (`waitUntil { onAllNodes(…, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }`), or assert the ViewModel / `repeatsLeftLabel` and skip the merged tree. After a VM wait, advance one frame (`mainClock.advanceTimeByFrame()` + `waitForIdle`) before any merged assert. Prefer kit `awaitState` for snapshot/row flows.

Inventory of first merged-tree asserts after a ViewModel / snapshot wait:

```
ui/capture/ScheduleCalendarTapTest.kt:228, :251
ui/countdown/CountdownResumeStayTest.kt:64, :79, :98, :120, :129
ui/countdown/CountdownTapTest.kt:79, :91, :100, :110, :121, :142, :202, :210, :215, :223, :319, :334, :340, :382, :424, :467, :477
ui/countdown/ResumeChipSheetQaTest.kt:191, :204
ui/edit/EditTaskSurfacesTest.kt:202, :270, :280, :287, :338, :373
ui/home/CardLeadTest.kt:175, :181, :185
ui/home/HomeLayoutBandsHeaderTest.kt:87, :96, :127, :137
ui/home/InterruptButtonTapTest.kt:168
ui/home/RepeatHomeDayRefreshTest.kt:99, :100, :140, :198, :199
ui/repeat/MissedRepeatSheetQaTest.kt:81, :117, :151, :170, :205, :236, :245, :254
ui/repeat/RepeatCaptureFlowTest.kt:58, :62, :86
ui/repeat/RepeatCheckOffTest.kt:76, :97, :143, :155, :164
ui/repeat/RepeatsMissedHeaderQaTest.kt:79, :80
ui/repeat/RepeatHomeSpecTest.kt:95, :102, :305, :328, :335, :343, :351, :375, :393, :397, :403
ui/repeat/RepeatScreenQaTest.kt:162
ui/repeat/RepeatSheetPersistTest.kt:69, :107, :128, :147, :171, :175
ui/settings/BandTimeSettingsTest.kt:195, :196
ui/settings/ThemeSettingsTest.kt:83, :152, :159
ui/task/MissedTaskSheetQaTest.kt:74, :98
ui/task/TaskSheetTapTest.kt:74, :129
ui/theme/ThemeKeyResetTest.kt:57
ui/theme/ThemeLaunchNoFlashTest.kt:67
ui/theme/ThemeLiveFollowTest.kt:99
ui/theme/ThemeUntintedTextTest.kt:75
```

---

## Should

### S1. Wall-clock dates and `AppClock { System.currentTimeMillis() }`

- **Where:**
  - `LocalDate.now()` — `CaptureSheetActionsTest.kt:89, :182, :221`; `ScheduleCalendarTapTest.kt:101, :111, :156, :204, :211`; `EditTaskSheetTest.kt:104`; `EditTaskSurfacesTest.kt:390, :469, :470`; `InterruptButtonTapTest.kt:94`; `MissedTaskSheetQaTest.kt:66, :89, :129`; `CountdownResumeStayTest.kt:56, :111, :150`; `CountdownTapTest.kt:82, :97, :105, :107, :125, :141, :162, :173, :266, :288, :357, :403, :442`; `ResumeChipSheetQaTest.kt:107, :132, :182, :216, :218, :232`
  - `AppClock { System.currentTimeMillis() }` — `ScheduleCalendarTapTest.kt:274`; `EditTaskSurfacesTest.kt:439`; `CardLeadTest.kt:137`; `HomeActionTapTest.kt:67`; `RepeatCaptureFlowTest.kt:119`; `BandTimeSettingsTest.kt:150`; `MissedTaskSheetQaTest.kt:204`; `TaskSheetTapTest.kt:329`; `CountdownResumeStayTest.kt:176`; `CountdownTapTest.kt:511`; `ResumeChipSheetQaTest.kt:258`
- **Rule:** Backpin — no `LocalDate.now()` / real clock. ankyr — clock is true I/O; fake it.
- **Why:** Countdown and schedule expectations are computed from the host date while the repository clock is “now.” Near local midnight, or when the injected clock and `LocalDate.now()` disagree, `tomorrow` / `In 7 days` / “today is due” flip. `CountdownTapTest` is the largest surface.
- **Fix:** One `FakeAppClock` (or a file-level `LocalDate.of(2026, 10, 16)` + `clock.localEpochDay()`). Derive every “tomorrow” and chip label from that clock. Do not mix `LocalDate.now()` with `clock.nowEpochMillis()`.

### S2. `ZoneId.systemDefault()` in date-sensitive tests

- **Where:** `CountdownArrivalTest.kt:36`; `PileInterruptTest.kt:32`; `RepeatRepositoryTest.kt:36`; `ScheduledReleaseTest.kt:31`; `RepeatNotificationCoordinatorTest.kt:38`; `RepeatsServiceTest.kt:41`; `ThemeQa.kt:61`; `FocusStackViewModelHomeBandsTest.kt:355`; `FocusStackViewModelTest.kt:470`; `HomeLayoutBandsHeaderTest.kt:407`; `MissedRepeatSheetQaTest.kt:446`; `RepeatHomeSpecTest.kt:536`; `RepeatCheckOffTest.kt:282`; `RepeatScreenQaTest.kt:398`; `RepeatSheetPersistTest.kt:263`; `MissedTaskSheetQaTest.kt:105, :149`
- **Rule:** Backpin — no `ZoneId.systemDefault()`.
- **Why:** `at(hour, minute)` converts a fixed `LocalDate` through the host zone. A UTC vs `America/New_York` runner changes epoch millis, band status, and “did we cross midnight.” `ThemeQa` pins the calendar day then converts with the system zone, so the same helper can disagree with `RepeatsService`’s zone lambda.
- **Fix:** `DEFAULT_TEST_ZONE` (`America/New_York`) or an explicit `ZoneId.of(...)`. Pass `zone = clock::zoneId` into `RepeatsService` / `TestFocusFixture`. `HomeBandsTest` already uses `ZoneOffset.UTC` — copy that habit.

### S3. Hand-built clocks, dispatchers, and Room where the kit fits

- **Where (clocks):** `SettableClock` / `MutableClock` / `object : AppClock` in `CountdownArrivalTest.kt:367`; `RepeatRepositoryTest.kt:172`; `ScheduledReleaseTest.kt:300`; `FocusRepositoryTest.kt:663`; `RepeatNotificationCoordinatorTest.kt:111`; `BandBoundaryTickTest.kt:194`; `RepeatsServiceDayRefreshTest.kt:384`; `RepeatsServiceTest.kt:264`; `FocusStackViewModelHomeBandsTest.kt:347`; `RepeatHomeDayRefreshTest.kt:310`; `MissedRepeatSheetQaTest.kt:469`; `RepeatHomeSpecTest.kt:559`; plus ad-hoc `AppClock { millis }` in `ThemeQa.kt:62` and most UI harnesses
- **Where (Room):** `Room.inMemoryDatabaseBuilder` copied through data tests and every Compose env (`FocusRepositoryTest.kt:45`, `RepeatHomeSpecTest.kt:486`, `ThemeQa.kt:64`, …)
- **Where (dispatchers):** `Dispatchers.setMain(UnconfinedTestDispatcher())` in `FocusStackViewModelTest.kt:56` and `FocusStackViewModelHomeBandsTest.kt:62`; local `StandardTestDispatcher` in `RepeatsServiceDayRefreshTest.kt:284` and `RepeatHomeDayRefreshTest.kt:221`
- **Rule:** Backpin — no hand-built clock / dispatcher / database where the shared kit fits. ankyr — share setup; do not grow a second copy.
- **Why:** Each copy omits zone, forgets to pass the same clock into `RepeatsService`, or uses Unconfined (eager, hides missing awaits). That mismatch already hid a day-refresh bug; the kit exists to stop a repeat.
- **Fix:** After #50 merges, replace env builders with `TestFocusFixture.open` / `useTestFocus` / `InMemoryFocusRule`, `FakeAppClock`, and `MainDispatcherRule`. Keep a local dispatcher only when the test must own virtual time for midnight / band loops — and then pass that same scope as `repeatsScope`.

### S4. RepeatsService timers on `runBlocking` or `Dispatchers.Default`

- **Where:**
  - `BandBoundaryTickTest.kt:145-154` — `scope = CoroutineScope(job + Dispatchers.Default)` plus `context = context`, so `emitAtEachBandBoundary` / `emitAtEachLocalMidnight` run on Default while the test sleeps (see B1)
  - `RepeatsServiceDayRefreshTest.kt:67` (`= runBlocking`) + `:323-325` — `probe` is a `CoroutineScope` extension, so collectors launch on the **runBlocking** scope while timers use `StandardTestDispatcher` (`:284-304`)
  - `FocusStackViewModelHomeBandsTest.kt:73` — test body is `runBlocking`; service scope is a separate `TestScope` (`:245-264`). Correct injection, still a hang footgun if `advanceTimeBy` is skipped
- **Rule:** Flag RepeatsService timers on a `runBlocking` scope. Those loops are `while (true) { withTimeoutOrNull(wait) { … } }` (`RepeatsService.kt:328-352`). On `runBlocking`, `wait` is real milliseconds until the next local midnight or band bound (minutes to hours). The test parks. On Default, the test cannot advance virtual time and falls back to sleeps.
- **Why:** No current test passes `scope = this` from `runBlocking`. The Default-scoped tick test is the live offender. The day-refresh suite splits scopes correctly but collects on `runBlocking`, so a missed `scheduler.advanceTimeBy` deadlocks the timeout against an idle test dispatcher.
- **Fix:** Never pass a `runBlocking` scope into `RepeatsService`. For timer tests: `runTest` + `MainDispatcherRule` (or a dedicated `TestScope`) as `repeatsScope`, `repeatsContext` set, advance virtual time. For API-only tests (`RepeatsServiceTest`), keep `context = null` so timers never start. Kit default `repeatsScope = CoroutineScope(SupervisorJob())` is also Default — override it whenever `repeatsContext != null`.

### S5. `runBlocking` inside Compose `waitUntil`

- **Where:** `CaptureSheetActionsTest.kt:492-499`; `EditTaskSurfacesTest.kt:566`; `HomeActionTapTest.kt:228`; `InterruptButtonTapTest.kt:254`; `MissedTaskSheetQaTest.kt:324`; `TaskSheetTapTest.kt:458`; `CountdownResumeStayTest.kt:218`; `CountdownTapTest.kt:623`; `ResumeChipSheetQaTest.kt:426`; `CardLeadTest.kt:152, :258`; `ScheduleCalendarTapTest.kt:298, :310`
- **Rule:** Backpin — no flows left unawaited; do not nest `runBlocking` on the Compose test thread. `RunOffTestThread.kt:13-16` documents that this parks forever once a screen is collecting Room.
- **Why:** `await { runBlocking { useCases.observeSnapshot().first() } }` blocks the same scheduler the UI collector needs. That is why `runOffTestThread` exists — these helpers undo that fix.
- **Fix:** `runOffTestThread { useCases.observeSnapshot().awaitState { … } }`, or collect in `backgroundScope` before `setContent`. Do not call `runBlocking` from `waitUntil`.

### S6. `performClick` instead of center `performTouchInput`

- **Where:** `CaptureSheetActionsTest.kt:318`, `:330`
- **Rule:** Backpin — tap tests use `performTouchInput` on the visible center, not only `performClick`.
- **Why:** Speech mic taps are the only remaining `performClick` in the suite. Same file already uses `performTouchInput { click(center) }` for other actions (`:349+`).
- **Fix:** `onNodeWithContentDescription("Speak the title").performTouchInput { click(center) }`.

### S7. Tests that still pass if the covered behavior is deleted

- **Where:** `CurrentNoticeTest.kt:39-46` (`channelIdsLeaveTheLowImportanceChannelUnused`); `ScheduleDateTest.kt:23-25` (`theQuickDaysAreTomorrowAndInSevenDaysOnly`)
- **Rule:** ankyr — no test that still passes when the code under test is deleted. Test behavior at seams, not constant / enum inventory.
- **Why:** Deleting `ensureFocusChannels` or `scheduleDate` leaves these green. They assert string literals and `ScheduleOffset.entries` order. Channel registration and offset math are already covered by `ongoingNoticeStaysOnTheDefaultChannel` and `offsetsAreLocalDaysFromToday`.
- **Fix:** Drop both, or fold into a behavioral assert (`NotificationManager.getNotificationChannel`, table-driven `scheduleDate`).

### S8. Repository test asserts notification copy

- **Where:** `FocusRepositoryTest.kt:526-532`
- **Rule:** ankyr — test behavior at the seam you own.
- **Why:** Stack restore is already asserted on frames / buckets / return cue. `currentNoticeCopy` belongs in `CurrentNoticeTest`. A copy regression can hide here, or a repository fix can fail because notice wording moved.
- **Fix:** Keep `relevantFocus` / `todayNoticeRows` identity asserts if needed; move `copy.title` / `copy.body` to the notification suite with `sampleTask` / a fixture snapshot.

### S9. Nested `runBlocking` in `assertThrows`

- **Where:** `PileInterruptTest.kt:111`, `:127`; `FocusRepositoryTest.kt:569`
- **Rule:** Backpin / kit — one structured scope (`runTest` / single `runBlocking`). Nested blocking hides cancellation and differs from the rest of the file.
- **Fix:** `runTest { assertFailsWith<IllegalStateException> { repository.interruptFromPile(milk) } }`.

### S10. `RunOffTestThread` still sleeps on the test thread

- **Where:** `app/src/test/java/app/backpin/focus/RunOffTestThread.kt:29`
- **Rule:** Backpin — no fixed sleeps.
- **Why:** The helper is the right idea (worker + main-looper idle). The `Thread.sleep(10)` poll is still wall time and is on the hot path of every repeat / capture tap test that seeds through `runOffTestThread`.
- **Fix:** Idle the looper in a tight loop with a nanoTime deadline only (already present). Or park on `pending.get(timeout)` and idle from a watcher. Do not sleep 10 ms per spin.

---

## Nit

### N1. `ViewModelStore` cleared in `@After`, not `finally`

- **Where:** `HomeLayoutBandsHeaderTest.kt:389-393`
- **Rule:** Backpin — clear the `ViewModelStore` in `finally`.
- **Why:** `@After tearDown` → `closeOpened()` works on the happy path. A failure between `opened += env` and the next test can skip the same guarantee peer files encode with `try/finally { env.close() }`.
- **Fix:** Per-test `try/finally`, matching `RepeatHomeSpecTest`.

### N2. `performTouchInput { click() }` without the visible center

- **Where:** `CaptureSheetActionsTest.kt:458, :488`; `CountdownTapTest.kt:84, :95, :143, :606`; `HomeActionTapTest.kt:175`
- **Rule:** Tap the visible center.
- **Fix:** `click(center)` like `ThemeQa.tap` and the repeat QA helpers.

### N3. Migration-count assertion

- **Where:** `SchemaAndPreferencesTest.kt:36`
- **Rule:** ankyr — observable schema, not inventory.
- **Why:** `assertEquals(4, FocusStackDatabase.migrations.size)` breaks on every added migration even when fixtures already prove columns and row data.
- **Fix:** Keep version / column asserts; drop `migrations.size`.

### N4. Copied `fillToday` / `sample()` factories

- **Where:** `CaptureDestinationTest.kt:197`; `CountdownArrivalTest` / `ScheduledReleaseTest` / `PileInterruptTest` (same helper); `RepeatDueTest.kt:59`
- **Rule:** ankyr — share setup. Kit already has `sampleTask`, `sampleRepeat`, `seedFullToday`.
- **Fix:** Call the kit factories once #50 lands.

### N5. No `androidTest` suite

- **Where:** `app/src/androidTest` does not exist.
- **Rule:** ankyr exception — end-to-end / device smokes mock nothing. Not a failure of the Robolectric suite; a gap.
- **Fix:** When a device smoke exists, put it here and do not fake the clock or Room. Until then, keep Robolectric on the kit.

---

## Flaky offenders

Seed for `backpin-plan/qa/flaky-audit.md`. Ordered by proven or likely CI cost.

1. **`RepeatHomeSpecTest.headerShowsLeftCountAndAllDoneWhenTheLastOpenRowIsCheckedOff`** — #46. VM `await` then merged `onNodeWithText("All done")` / `"N of N left"` (`:392-404`).
2. **`BandBoundaryTickTest`** — `Thread.sleep` + Default-scoped RepeatsService timers (`:55` … `:220`).
3. **`CountdownTapTest`** — largest `LocalDate.now()` + wall `AppClock` + post-wait Compose surface (`:82-511`).
4. **`RepeatHomeDayRefreshTest`** — day rollover, VM wait, then merged `assertDoesNotExist` (`:99-100`, `:140`, `:198-199`). Regression-critical.
5. **`RepeatsServiceDayRefreshTest`** — #44 class. Collectors on `runBlocking`, timers on `StandardTestDispatcher`. Auckland / date-broadcast waits flake when an in-flight old-day snapshot is taken (partially fixed in #45).
6. **`FocusStackViewModelHomeBandsTest`** — #42 class. Outer `runBlocking` + service `TestScope`; safe only if every bound wait advances virtual time.
7. **`ThemeLaunchNoFlashTest`** — activity launch `waitUntil` then merged chrome assert (`:55-67`).
8. **`HomeLayoutBandsHeaderTest`** — `ready` wait then `assertIsDisplayed` on band headers (`:86-87`).
9. **`ScheduleCalendarTapTest` / `EditTaskSurfacesTest` / `MissedTaskSheetQaTest`** — wall clock + `runBlocking` inside `waitUntil`.
10. **`RunOffTestThread`** — 10 ms sleep poll on every seed path (`:29`).
11. **`RepeatNotificationCoordinatorTest`** — `Dispatchers.Unconfined` scheduler (`:44`) plus `ZoneId.systemDefault()` (`:38`).
12. **`CurrentNoticeTest`** — `RemoteViews` private-field reflection (`:512-537`); breaks when the platform shape moves.

---

## Out of scope / notes

- **No Mockito / mockk** in this suite. Fakes are clocks, Room, and a fake speech recognizer. That part matches ankyr.
- **Domain tests** (`HomeBandsTest`, `RepeatDueTest`, `BandAlarmTimesTest`, `ScheduleDateTest` aside from S7) inject `LocalDate` / `LocalTime` / explicit zones. Leave them; adopt `sampleRepeat` only.
- **`RepeatsServiceTest`** does not pass `context`, so midnight / band loops do not start. Keep it that way unless the test is about those loops.
- **Kit is not on `dev`.** Suggested fixes that name `FakeAppClock` / `TestFocusFixture` assume #50. Until then, pin `ZoneId.of("America/New_York")` and one `SettableClock` per file rather than inventing a third clock type.
- **`app/src/androidTest`** is absent; every finding above is Robolectric.
