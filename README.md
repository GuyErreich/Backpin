# Focus Stack

Focus Stack is an offline Android app for keeping your place. Capture a thought into Inbox without leaving the active task, keep at most three open tasks in Today, and unwind nested interruptions back to the exact task you paused.

There is no account, backend, or network. Elapsed time is computed from stored timestamps while the screen is open.

## Requirements

- JDK 21
- Android SDK platform 37.2 and build-tools 37.0.0

Point the SDK at the project with `ANDROID_HOME` or `sdk.dir` in `local.properties`.

## Build and test

```bash
./gradlew test
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install that APK on a device or emulator running Android 8.0 (API 26) or newer.
