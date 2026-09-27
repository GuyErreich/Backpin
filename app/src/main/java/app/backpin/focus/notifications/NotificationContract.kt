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
    const val ID_CURRENT: Int = 41
    const val ID_NUDGE: Int = 42
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
