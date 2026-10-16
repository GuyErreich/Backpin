# Agent rules for Backpin

Read this before changing code. One line per rule.

## UI

Buttons
- Use only `QuietButton`, `AccentButton`, or `CaptureChoice` from [Buttons.kt](app/src/main/java/app/backpin/focus/ui/components/Buttons.kt). No raw `Button`, `OutlinedButton`, `TextButton`, chips, or filled buttons.
- Sheets, Settings, and dialogs stay 56dp minimum height, 16dp radius, 12dp gaps, `ButtonLabel` text, equal width in `ActionRow`. The tokens live in [Theme.kt](app/src/main/java/app/backpin/focus/ui/theme/Theme.kt).
- List cards lead with a `titleLarge` title. Their actions sit in `CardActions`: 40dp tall and only as wide as the label.

Colors
- Accent buttons are outlined, with the label in the same color as the border.
- Mint is Done, Resume, and Inbox. Lavender is Interrupt and events, everywhere. Amber is Schedule. Everything else is quiet.
- Use only the theme colors in `FocusColors`.
- No red, no alarm look.

Navigation
- One top-left back arrow, [BackArrow](app/src/main/java/app/backpin/focus/ui/components/BackArrow.kt), in every screen and sheet. It steps back, and on the first step it closes.
- No X and no text Back. The system back does the same as the arrow.
- Dialogs use Cancel.

Motion
- Quiet motion from [ui/motion](app/src/main/java/app/backpin/focus/ui/motion): no ripple, no bounce, no sound.
- Honor reduced motion.

Copy
- Real copy, short sentences.
- Countdowns and dates say exact days ("In 7 days"), never vague ones ("a couple of days").

## Behavior

- Today holds 1 to 5 tasks, 3 by default. Nothing auto-fills Today, auto-starts, or auto-resumes, except a countdown on its day.
- Interrupt only appears while a task is active. It nests under the active task.
- No exact alarms, no foreground service. Notifications are silent.

## Testing

- Tap tests use `performTouchInput` on the visible center, not only `performClick`.
- Clear the `ViewModelStore` in `finally`.
- Robolectric runs on a plain `Application`.
- Run `./gradlew test` before pushing.

## Git

- New work goes on a `cursor/` branch from `dev`, with one pull request into `dev`.
- A push to `dev` promotes `staging`. That promotion publishes the preview APK.
- `main` is production. Feature pull requests do not target it.
- A squash-merged branch counts as merged when its tree matches `dev`, so follow-ups start a new branch.
- Do not edit plan files.
