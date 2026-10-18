# Agent rules for Backpin

Read this before changing code. One line per rule.

## UI

Buttons
- Use only `QuietButton`, `AccentButton`, or `CaptureChoice` from [Buttons.kt](app/src/main/java/app/backpin/focus/ui/components/Buttons.kt). No raw `Button`, `OutlinedButton`, `TextButton`, chips, or filled buttons.
- Sheets, Settings, and dialogs stay 56dp minimum height, 16dp radius, 12dp gaps, `ButtonLabel` text, equal width in `ActionRow`. The tokens live in [Theme.kt](app/src/main/java/app/backpin/focus/ui/theme/Theme.kt).
- List cards lead with a `titleLarge` title. Their actions sit in `CardActions`: 48dp tall and only as wide as the label.

Colors
- Accent buttons are outlined, with the label in the same color as the border.
- Mint is Done, Resume, and Inbox. Lavender is Interrupt and events, everywhere. Amber is missed or due. Everything else is quiet.
- Use only the theme colors in `FocusColors`.
- No red, no alarm look.

Navigation
- One top-left back arrow, [BackArrow](app/src/main/java/app/backpin/focus/ui/components/BackArrow.kt), in every screen and sheet. It steps back, and on the first step it closes.
- No X and no text Back. The system back does the same as the arrow.
- Dialogs use Cancel. Exception: when dismissing is itself the safe choice, the safe button replaces Cancel.

Motion
- Quiet motion from [ui/motion](app/src/main/java/app/backpin/focus/ui/motion): no ripple, no bounce, no sound.
- Honor reduced motion.

Copy
- Real copy, short sentences.
- Countdowns and dates say exact days ("In 7 days"), never vague ones ("a couple of days").

## Behavior

- Today holds 1 to 5 tasks, 3 by default. Nothing auto-fills Today, auto-starts, or auto-resumes, except a countdown on its day.
- Interrupt only appears while a task is active. It nests under the active task.
- No exact alarms except AlarmManager for repeat band reminders (band start, follow-up, and band end), with the WorkManager fallback when permission is denied. No foreground service. Notifications stay silent, except the repeat band group summary, which alerts once; its child notifications stay quiet.

## Testing

- Follow Guy's ankyr-testing discipline on every test.
- Test behavior at seams, not internals.
- Fake only true I/O (clock, zone, network), using `app.backpin.focus.testing` fakes, not mocks of the code under test.
- Share setup via that kit's factories and helpers instead of per-test clocks, dispatchers, or databases.
- Table-driven cases.
- Cover edge cases.
- One regression test per bug, which fails if the fix is reverted.
- No test that still passes when the code under test is deleted.
- No real clock and no fixed sleeps.
- Each test PR's plan must say "ankyr-testing followed".
- Tap tests use `performTouchInput` on the visible center, not only `performClick`.
- Clear the `ViewModelStore` in `finally`.
- Robolectric runs on a plain `Application`.
- Before pushing, compile the tests with `./gradlew :app:compileDebugUnitTestKotlin` and run `./gradlew ktlintCheck`.
- Do not run tests as a gate. CI is the test verdict, including the CI flake check that reruns new or changed test classes about 30 times per PR.
- A PR is ready when Sam approves it and CI is green.
- On a CI failure, Ray decides whether the workflow or the code is at fault, and the PR author and the test owner fix it.

## Git

- New work goes on a `cursor/` branch from `dev`, with one pull request into `dev`.
- A merged pull request into `dev` lets Action-Semver-Control promote `staging`. That promotion publishes the preview APK.
- `main` is production. Feature pull requests do not target it.
- A squash-merged branch counts as merged when its tree matches `dev`, so follow-ups start a new branch.
- Do not edit plan files.

## Cursor Cloud

- JDK 21 lives at `/usr/lib/jvm/java-21-openjdk-amd64` and is the default `java`. Login shells export `JAVA_HOME` and `ANDROID_HOME` from `/etc/profile.d/backpin-android.sh`.
- The Android SDK is `$HOME/Android/Sdk` (platform 37.2, build-tools 37.0.0). `local.properties` sets `sdk.dir` to that path.
- A missing `local.properties` is rewritten by `~/.gradle/init.d/backpin-sdk.gradle` the next time Gradle runs.
- Check the app with `./gradlew test` and `./gradlew assembleDebug`. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
