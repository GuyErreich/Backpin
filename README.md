# Focus Stack

Focus Stack is an offline Android app for keeping your place. Capture a thought into Inbox without leaving the active task, keep a small number of open tasks in Today, and unwind nested interruptions back to the exact task you paused.

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

Home shows one Now card and a Today list capped by the setting (1 to 5, default 3). A free place is a single dashed empty slot, and empty places are never filled on their own. Inbox, Later, and Done are count rows under Today. Tapping a row opens that pile on its own screen, and Back returns to Today. Those lists stay off Home. Capture stays on Home: a title-only sheet with Save to Inbox, or Interrupt now.

The Now card offers Done and Interrupt as actions, with Later quieter. Interrupt opens capture; entering a title and choosing Interrupt now pushes a frame. On a Today task, swipe right to mark it done, or left to move it to Later. Start stays on a Today task that is not already Now. On a Later or Inbox task, the buttons and swipes are Today and Start: swipe right to add it to Today, swipe left to start it. A slide moves the task and leaves the pile screen open. Done and Later are not offered there. Done and Later commit immediately, with no Undo snackbar. A finished task stays in Done. Opening Done shows that list, and Restore puts the task back into Inbox as an open task without starting it, moving it into Today, or asking to confirm.

Long-press a Today task and drag to reorder that list. A drop stays inside Today and does not change the bucket. Inbox and Later cannot be dragged onto Today, and Today cannot be dragged onto them.

If Today is already full, adding or starting a task from Inbox or Later does not add a slot. The choice is Interrupt current task, when something in Today is active, or Replace a Today task. Interrupt reuses the incoming task as the new Now, leaves the previous Now recoverable as the parent frame, and moves that previous task out of Today. Replace parks the chosen task in Later and puts the incoming task in that slot, in one transaction, without starting it. If nothing is active, only Replace is available. Adding, swapping, and reordering Today can still offer Undo. Interrupt does not.

Settings stores the Today cap on this phone, and can turn nudges Off or to about 30 minutes, 1 hour, or 2 hours, set quiet hours, and show a dismissible current-task notification. Nudges use inexact WorkManager and can be delayed. The app does not use exact alarms or a foreground service. Notification permission is requested only when you turn a notification feature on. If you deny it, capture, Home, and recovery keep working.
