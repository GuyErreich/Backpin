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

## What the MVP does

Home shows one Now card and up to three Today positions, including Now. A free Today position is a single dashed empty slot. Today ends at those slots. Later and Inbox are separate sections, each with its own title and container. Capture is a title-only sheet: Save to Inbox, or Interrupt now.

The Now card offers Done and Interrupt as actions, with Later quieter. Interrupt opens capture; entering a title and choosing Interrupt now pushes a frame. Swipe a task right to mark it done, or left to move it to Later. Both show Undo. Reorder inside Today from the Move menu. + Today, Later, and Done change a task's bucket. A drop does not. If Today is already full, the swap chooser asks which task to park.

Settings can turn nudges Off or to about 30 minutes, 1 hour, or 2 hours, set quiet hours, and show a dismissible current-task notification. Nudges use inexact WorkManager and can be delayed. The app does not use exact alarms or a foreground service. Notification permission is requested only when you turn a notification feature on. If you deny it, capture, Home, and recovery keep working.
