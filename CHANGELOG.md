# Changelog
All notable changes to this project will be documented in this file.

## [0.1.21-dev] - 18-10-2026

### Features & Enhancements
- theme tokens and theme setting (#29)
- band headers on Home with layout switch (#32)
- Today notification matches option B (#59)


### Bug Fixes & Resolutions
- midnight and foreground refresh (#30)


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