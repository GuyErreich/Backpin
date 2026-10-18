// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationContract {
    const val EXTRA_DESTINATION: String = "app.backpin.focus.destination"
    const val DESTINATION_FOCUS: String = "focus"
    const val DESTINATION_CAPTURE: String = "capture"
    const val EXTRA_TASK_ID: String = "app.backpin.focus.task"
    const val ACTION_LATER: String = "app.backpin.focus.action.LATER"
    const val ACTION_SWITCH: String = "app.backpin.focus.action.SWITCH"
    const val ACTION_TODAY_DONE: String = "app.backpin.focus.action.TODAY_DONE"
    const val ACTION_REPEAT_DONE: String = "app.backpin.focus.action.REPEAT_DONE"
    const val ACTION_BAND_ALARM: String = "app.backpin.focus.action.BAND_ALARM"
    const val EXTRA_REPEAT_ID: String = "app.backpin.focus.repeat"
    const val EXTRA_EPOCH_DAY: String = "app.backpin.focus.epoch_day"
    const val EXTRA_BAND: String = "app.backpin.focus.band"
    const val EXTRA_BAND_KIND: String = "app.backpin.focus.band_kind"

    /**
     * Low-importance channel from earlier builds. Left unused.
     * Android ignores a later importance change on a channel that already exists.
     */
    const val CHANNEL_CURRENT_LEGACY: String = "focus_current"

    /** Active or waiting task. Default importance so a small status icon can show. */
    const val CHANNEL_CURRENT: String = "focus_current_ongoing"

    /**
     * Idle Today list from an earlier build, created at low importance.
     * Left unused. Android will not raise a channel that already exists, and
     * that quiet channel showed no status icon, so an unstarted Today list
     * was easy to miss.
     */
    const val CHANNEL_TODAY_IDLE: String = "focus_today_idle"

    /**
     * Idle Today list. Default importance so a small status icon can show
     * when nothing is started. Sound and vibration stay off.
     */
    const val CHANNEL_TODAY: String = "focus_today_ongoing"

    const val CHANNEL_NUDGE: String = "focus_nudge"

    /** A countdown reached its day and is now in focus. Silent, dismissible, not a heads-up. */
    const val CHANNEL_COUNTDOWN: String = "focus_countdown"

    /** Band start alerts on this channel. Child repeats stay silent in the same group. */
    const val CHANNEL_REPEATS: String = "focus_repeats"

    const val ID_CURRENT: Int = 41
    const val ID_NUDGE: Int = 42
    const val ID_COUNTDOWN: Int = 43
    const val ID_REPEAT_SUMMARY_MORNING: Int = 210
    const val ID_REPEAT_SUMMARY_NOON: Int = 211
    const val ID_REPEAT_SUMMARY_NIGHT: Int = 212
    const val ID_REPEAT_CHILD_BASE: Int = 10_000
}

/** Runtime permission is required only on API 33+. App-level blocks count as denied too. */
fun notificationsAllowed(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    return true
}
