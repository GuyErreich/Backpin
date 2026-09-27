# Focus Stack

Focus Stack is an offline Android app for keeping your place. Capture a thought into Inbox without leaving the active task, keep a small number of open tasks in Today, and unwind nested interruptions back to the exact task you paused.

There is no account or backend. Speaking a title uses the phone's speech recognizer, prefers an offline pack, and may use the network only when that pack is missing. Elapsed time is computed from stored timestamps while the screen is open.

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

Home shows one Now card and a Today list capped by the setting (1 to 5, default 3). A free place is a single dashed empty slot, and empty places are never filled on their own. Inbox, Later, and Done are count rows under Today. Tapping a row opens that pile on its own screen, and Back returns to Today. Those lists stay off Home. Capture stays on Home: a title field with a mic, and saved phrases as chips above it. Speech fills the field and does not save by itself. Save to Inbox and Interrupt now stay for a line you type or speak. A finished phrase chip saves to Inbox from + Capture, and interrupts now from Interrupt (including the notification Interrupt action, which is shown only while a task is in focus). A phrase with angle brackets, such as `Talk to <name>`, asks only for that word, then does the same. An empty fill-in creates nothing.

The Now card offers Done and Interrupt as actions, with Later quieter. Interrupt opens capture; entering a title and choosing Interrupt now pushes a frame. On a Today task, swipe left to move it to Later. Swipe right is Done when that row is Now, Start when nothing is in focus, and Interrupt when another task is current. That Interrupt pauses the current task and starts this one on top of it. Both stay in Today. Finishing the new one brings back the one underneath, with its return note. A row waiting to be resumed keeps Done, so resume stays explicit. On a Later or Inbox task, the buttons and swipes are Today and Start: swipe right to add it to Today, swipe left to start it. A slide moves the task and leaves the pile screen open. Done and Later are not offered there. Done and Later commit immediately, with no Undo snackbar. A finished task stays in Done. Opening Done shows that list, and Restore puts the task back into Inbox as an open task without starting it, moving it into Today, or asking to confirm.

Long-press a Today task and drag to reorder that list. A drop stays inside Today and does not change the bucket. Inbox and Later cannot be dragged onto Today, and Today cannot be dragged onto them.

If Today is already full, adding or starting a task from Inbox or Later does not add a slot. The choice is Interrupt current task, when something in Today is active, or Replace a Today task. Interrupt reuses the incoming task as the new Now, leaves the previous Now recoverable as the parent frame, and moves that previous task out of Today. Replace parks the chosen task in Later and puts the incoming task in that slot, in one transaction, without starting it. If nothing is active, only Replace is available. Adding, swapping, and reordering Today can still offer Undo. Interrupt does not.

Settings stores the Today cap on this phone, a short phrase list, and can turn nudges Off or to about 30 minutes, 1 hour, or 2 hours, set quiet hours, and show an ongoing notice while Today has an open task. With nothing in focus, the notice is titled Today, lists those titles, and can show a small status icon so it stays findable. While a task is active or waiting, the title is that task and the text adds the other Today titles and the return note. It stays silent either way: no sound, no vibration, and no heads-up. It leaves only when Today is empty, the setting is off, or notifications are denied. Expanding that notice lists the open Today tasks, up to the saved limit. While a task is in focus, that row is marked and opens Focus, and each other row switches to that task: the current one pauses and the chosen one starts, without nesting an interruption. The Interrupt action is on the notice only then. When nothing is started, and when the only frame is waiting to resume, that action is hidden. Tapping an unstarted row while nothing is in focus starts that task, again without nesting. A row that is waiting to resume still opens Focus. Tapping the collapsed notice still opens Focus. The first launch seeds `Talk to my wife` and `Talk to <name>`. Deleting or renaming those examples sticks. Elapsed time stays in the app. Nudges use inexact WorkManager and can be delayed. The app does not use exact alarms or a foreground service. Notification permission is requested only when you turn a notification feature on. If you deny it, capture, Home, and recovery keep working.
