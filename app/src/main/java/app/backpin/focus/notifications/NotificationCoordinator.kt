package app.backpin.focus.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.backpin.focus.MainActivity
import app.backpin.focus.R
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Posts a dismissible current-task notification and schedules inexact nudges.
 * There is no foreground service and no exact alarm. Permission denial cancels
 * delivery and leaves the in-app loop alone.
 */
class NotificationCoordinator(
    private val context: Context,
    private val repository: FocusRepository,
) {
    private val visible = MutableStateFlow(false)
    private val recheck = MutableStateFlow(0)
    private var postedKey: String? = null
    private val scheduler by lazy { NudgeScheduler(androidx.work.WorkManager.getInstance(context)) }

    fun setAppVisible(isVisible: Boolean) {
        visible.value = isVisible
        recheck.value = recheck.value + 1
    }

    fun start(scope: CoroutineScope) {
        ensureChannels()
        scope.launch {
            combine(
                repository.observeSnapshot(),
                repository.preferences,
                visible,
                recheck,
            ) { snapshot, preferences, _, _ ->
                snapshot to preferences
            }.collect { (snapshot, preferences) ->
                val allowed = notificationsAllowed(context)
                refreshCurrent(snapshot, preferences, allowed, force = false)
                scheduler.sync(
                    enabled = preferences.nudgesEnabled,
                    minutes = preferences.nudgeMinutes,
                    notificationsAllowed = allowed,
                    hasRelevantTask = relevantFocus(snapshot) != null,
                )
            }
        }
    }

    /** Called from the periodic worker. Refreshes the current-task notice, then maybe posts a nudge. */
    suspend fun onScheduledTick() {
        val snapshot = repository.observeSnapshot().first()
        val preferences = repository.preferences.first()
        val allowed = notificationsAllowed(context)
        refreshCurrent(snapshot, preferences, allowed, force = true)
        val decision =
            decideNudge(
                nowEpochMillis = System.currentTimeMillis(),
                time = LocalTime.now(),
                appVisible = visible.value,
                notificationsAllowed = allowed,
                preferences = preferences,
                target = relevantFocus(snapshot),
            )
        if (decision is NudgeDecision.Post) {
            postNudge(decision.taskId, decision.title)
            repository.recordNudge(decision.taskId)
        }
    }

    /** Later from a notification. Reads the latest frame and no-ops if that task is no longer current. */
    suspend fun handleLater(taskId: String?) {
        if (taskId.isNullOrBlank()) return
        val snapshot = repository.observeSnapshot().first()
        val target = relevantFocus(snapshot) ?: return
        if (target.taskId != taskId) return
        val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != TaskStatus.OPEN) return
        repository.demote(taskId)
    }

    private fun refreshCurrent(
        snapshot: FocusSnapshot,
        preferences: UserPreferences,
        allowed: Boolean,
        force: Boolean,
    ) {
        val target = relevantFocus(snapshot)
        if (!preferences.currentNotificationEnabled || !allowed || target == null) {
            cancel(NotificationContract.ID_CURRENT)
            postedKey = null
            return
        }
        val key = target.taskId + ":" + target.recovery
        if (!force && key == postedKey) return
        val title =
            if (target.recovery) {
                context.getString(R.string.notification_before_this, target.title)
            } else {
                target.title
            }
        val text = target.returnCue?.takeIf { it.isNotBlank() } ?: context.getString(R.string.notification_in_focus)
        val notification =
            base(NotificationContract.CHANNEL_CURRENT)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setContentIntent(openApp(NotificationContract.DESTINATION_FOCUS, requestCode = 1))
                .addAction(
                    0,
                    context.getString(R.string.action_interrupt),
                    openApp(NotificationContract.DESTINATION_CAPTURE, requestCode = 2),
                )
                .build()
        if (notify(NotificationContract.ID_CURRENT, notification)) {
            postedKey = key
        }
    }

    private fun postNudge(
        taskId: String,
        title: String,
    ) {
        val notification =
            base(NotificationContract.CHANNEL_NUDGE)
                .setContentTitle(context.getString(R.string.nudge_title, title))
                .setContentText(context.getString(R.string.nudge_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openApp(NotificationContract.DESTINATION_FOCUS, requestCode = 3))
                .addAction(
                    0,
                    context.getString(R.string.action_resume),
                    openApp(NotificationContract.DESTINATION_FOCUS, requestCode = 4),
                )
                .addAction(
                    0,
                    context.getString(R.string.action_later),
                    laterPending(taskId),
                )
                .build()
        notify(NotificationContract.ID_NUDGE, notification)
    }

    private fun base(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_focus)

    private fun openApp(
        destination: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationContract.EXTRA_DESTINATION, destination)
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun laterPending(taskId: String): PendingIntent {
        val intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationContract.ACTION_LATER
                putExtra(NotificationContract.EXTRA_TASK_ID, taskId)
            }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(
        id: Int,
        notification: android.app.Notification,
    ): Boolean =
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }

    private fun cancel(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val current =
            NotificationChannel(
                NotificationContract.CHANNEL_CURRENT,
                context.getString(R.string.channel_current_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_current_description)
            }
        val nudge =
            NotificationChannel(
                NotificationContract.CHANNEL_NUDGE,
                context.getString(R.string.channel_nudge_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_nudge_description)
            }
        manager.createNotificationChannel(current)
        manager.createNotificationChannel(nudge)
    }
}
