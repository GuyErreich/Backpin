# Changelog
All notable changes to this project will be documented in this file.

## [0.1.38-dev] - 22-10-2026

### Features & Enhancements
- show Done on the task in focus (#116)


### Bug Fixes & Resolutions
- keep a gap between a band task and the repeat under it (#114)

## [0.1.37-dev] - 22-10-2026

### Bug Fixes & Resolutions
- keep the swipe color a band on the opened side (#112)
- keep a gap between a band task and the repeat under it (#114)

## [0.1.36-dev] - 22-10-2026

### Bug Fixes & Resolutions
- fill the swipe color behind the rounded row (#110)
- keep the swipe color a band on the opened side (#112)

## [0.1.35-dev] - 21-10-2026

### Bug Fixes & Resolutions
- publish the preview when a staging push is not delivered (#108)
- fill the swipe color behind the rounded row (#110)

## [0.1.34-dev] - 21-10-2026

### Bug Fixes & Resolutions
- center the add pill and keep a started task in the head (#106)
- publish the preview when a staging push is not delivered (#108)

## [0.1.33-dev] - 21-10-2026

### Refactoring & Code Quality
- split CaptureSheet into create, interrupt, EditTaskSheet (#102)


### Bug Fixes & Resolutions
- center the add pill and keep a started task in the head (#106)

## [0.1.32-dev] - 21-10-2026

### Refactoring & Code Quality
- split CaptureSheet into create, interrupt, EditTaskSheet (#102)


### Bug Fixes & Resolutions
- align the sheets, use the day wheel for countdowns, and restore home swipes (#103)

## [0.1.31-dev] - 21-10-2026

### Features & Enhancements
- Capture sheet to Penpot C1/2 (#93)


### Refactoring & Code Quality
- rename FocusColors.Lavender to Now (#100)


### Bug Fixes & Resolutions
- design QA follow-ups for Home v2 (#92)
- align the sheets, use the day wheel for countdowns, and restore home swipes (#103)


### Testing
- cover build-logic test-class parser (#81)
- make two FocusStack ViewModel unit tests deterministic (#76)

## [0.1.30-dev] - 19-10-2026

### Features & Enhancements
- Today notification matches option B (#59)
- match Home to the frozen compact mockup (#58)
- Capture sheet to Penpot C1/2 (#93)


### Refactoring & Code Quality
- rename FocusColors.Lavender to Now (#100)


### Infrastructure & Tooling
- run CI on pull requests to any branch (#71)
- rerun changed tests to catch flakes (#74)


### Bug Fixes & Resolutions
- keep captured task in Inbox when Today is full (#57)
- design QA follow-ups for Home v2 (#92)


### Testing
- cover build-logic test-class parser (#81)
- make two FocusStack ViewModel unit tests deterministic (#76)

## [0.1.25-dev] - 19-10-2026

### Features & Enhancements
- theme tokens and theme setting (#29)
- band headers on Home with layout switch (#32)
- Today notification matches option B (#59)
- match Home to the frozen compact mockup (#58)


### Infrastructure & Tooling
- run CI on pull requests to any branch (#71)
- rerun changed tests to catch flakes (#74)


### Bug Fixes & Resolutions
- midnight and foreground refresh (#30)
- keep captured task in Inbox when Today is full (#57)


### Testing
- QA coverage for theme tokens and settings (#33)
- fix flaky Today swap wait and HomeBands boundary emissions (#42) (#49)
- wait for Auckland day in RepeatsServiceDayRefreshTest (fixes #44) (#45)
- fix flaky RepeatHomeSpecTest header assert (#46) (#53)
- add shared test-fixtures kit (clock, dispatcher, in-memory Room, awaitState, factories) (#50)

## [0.1.20-dev] - 18-10-2026

### Features & Enhancements
- repeats backend with exact band alarms (#13)
- repeats Home, sheet and settings UI (#17)
- theme tokens and theme setting (#29)
- band headers on Home with layout switch (#32)


### Infrastructure & Tooling
- wire shared workflows from ci-workflows v0.1.0 (#21)
- bump actions/checkout from 4 to 7 (#23)
- bump actions/setup-java from 4 to 6 (#24)
- pin ci-workflows to v0.1.2 and split pr-title workflow (#27)


### Bug Fixes & Resolutions
- midnight and foreground refresh (#30)


### Testing
- QA coverage for theme tokens and settings (#33)
- fix flaky Today swap wait and HomeBands boundary emissions (#42) (#49)
- wait for Auckland day in RepeatsServiceDayRefreshTest (fixes #44) (#45)
- fix flaky RepeatHomeSpecTest header assert (#46) (#53)
- add shared test-fixtures kit (clock, dispatcher, in-memory Room, awaitState, factories) (#50)


### Other Changes
- Count down again offers Again in N days, typed days, or a date (#11)
- Edit tasks with the capture sheet (#12)

## [0.1.12-dev] - 17-10-2026

### Features & Enhancements
- repeats backend with exact band alarms (#13)
- repeats Home, sheet and settings UI (#17)


### Infrastructure & Tooling
- stop installing the removed Android SDK tools package (#8)
- wire shared workflows from ci-workflows v0.1.0 (#21)
- bump actions/checkout from 4 to 7 (#23)
- bump actions/setup-java from 4 to 6 (#24)
- pin ci-workflows to v0.1.2 and split pr-title workflow (#27)


### Other Changes
- Document Cloud Agent JDK and Android SDK paths (#7)
- Count down again offers Again in N days, typed days, or a date (#11)
- Edit tasks with the capture sheet (#12)

## [0.1.4-dev] - 16-10-2026

### Infrastructure & Tooling
- stop installing the removed Android SDK tools package (#8)


### Other Changes
- Document Cloud Agent JDK and Android SDK paths (#7)

## [0.1.2-dev] - 16-10-2026

### Other Changes
- Miscellaneous changes