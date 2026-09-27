package app.backpin.focus.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.backpin.focus.MainActivity
import app.backpin.focus.R
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Posts an ongoing Today notice and schedules inexact nudges.
 * The notice stays while Today has an open task, including when nothing is
 * started. Idle and active notices both use default importance so a small
 * status icon can show. Both are silent, not a heads-up, and not a foreground
 * service. Nudges stay dismissible on their own channel. Permission denial
 * cancels delivery and leaves the in-app loop alone.
 */
class NotificationCoordinator(
    private val context: Context,
    private val repository: FocusRepository,
) {
    private val visible = MutableStateFlow(false)
    private val recheck = MutableStateFlow(0)
    private var postedKey: String? = null
    private var postedChannel: String? = null
    private val scheduler by lazy { NudgeScheduler(androidx.work.WorkManager.getInstance(context)) }

    fun setAppVisible(isVisible: Boolean) {
        visible.value = isVisible
        recheck.value = recheck.value + 1
    }

    fun start(scope: CoroutineScope) {
        ensureFocusChannels(context)
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

    /**
     * Switch from the expanded notice. Pauses the current task and starts [taskId].
     * This is [FocusRepository.start], not a nested interruption. The current row
     * and anything outside Today are ignored.
     */
    suspend fun handleSwitch(taskId: String?) {
        if (taskId.isNullOrBlank()) return
        val snapshot = repository.observeSnapshot().first()
        val current = relevantFocus(snapshot)
        if (current?.taskId == taskId) return
        val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != TaskStatus.OPEN || task.bucket != TaskBucket.TODAY) return
        repository.start(taskId)
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
        val rows = todayNoticeRows(snapshot, target?.taskId.orEmpty(), preferences.todayLimit)
        if (!shouldShowTodayNotice(preferences.currentNotificationEnabled, allowed, rows.size)) {
            cancel(NotificationContract.ID_CURRENT)
            postedKey = null
            postedChannel = null
            return
        }
        val copy =
            currentNoticeCopy(
                rows = rows,
                target = target,
                todayLabel = context.getString(R.string.notification_today_title),
                inFocusLabel = context.getString(R.string.notification_in_focus),
            )
        val channel = todayNoticeChannel(copy.idle)
        val key = currentNoticeKey(copy, rows)
        if (!force && key == postedKey && channel == postedChannel) return
        if (postedChannel != null && postedChannel != channel) {
            cancel(NotificationContract.ID_CURRENT)
        }
        val notification = buildCurrentTaskNotification(context, copy.title, copy.body, rows, idle = copy.idle)
        if (notify(NotificationContract.ID_CURRENT, notification)) {
            postedKey = key
            postedChannel = channel
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
    ): PendingIntent = focusPendingIntent(context, destination, requestCode)

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
}

/** Title and body of the ongoing notice. [idle] is Today with nothing active or waiting. */
internal data class CurrentNoticeCopy(
    val title: String,
    val body: String,
    val idle: Boolean,
)

/**
 * The notice stays while the setting is on, permission is granted, and Today
 * still has an open task. It does not require an active or waiting frame.
 */
internal fun shouldShowTodayNotice(
    enabled: Boolean,
    notificationsAllowed: Boolean,
    openTodayCount: Int,
): Boolean = enabled && notificationsAllowed && openTodayCount > 0

/** One open Today task shown when the current-task notice is expanded. */
internal data class TodayNoticeRow(
    val taskId: String,
    val title: String,
    val current: Boolean,
)

/**
 * Open Today tasks, in saved order, capped by the stored limit.
 * [currentTaskId] is marked and is not a switch target.
 */
internal fun todayNoticeRows(
    snapshot: FocusSnapshot,
    currentTaskId: String,
    limit: Int,
): List<TodayNoticeRow> {
    val cap = limit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)
    return snapshot.tasks
        .asSequence()
        .filter { it.status == TaskStatus.OPEN && it.bucket == TaskBucket.TODAY }
        .sortedBy { it.sortOrder }
        .take(cap)
        .map { task ->
            TodayNoticeRow(
                taskId = task.id,
                title = task.title,
                current = task.id == currentTaskId,
            )
        }
        .toList()
}

/** Changes when the collapsed copy or the open Today set changes. */
internal fun currentNoticeKey(
    copy: CurrentNoticeCopy,
    rows: List<TodayNoticeRow>,
): String {
    val today = rows.joinToString(separator = "|") { "${it.taskId}:${it.title}:${it.current}" }
    return "${copy.idle}:${copy.title}:${copy.body}:$today"
}

/**
 * Collapsed copy. Idle Today is titled Today. The body uses two lines so the
 * open titles stay readable in the collapsed shade.
 * An active or waiting task is titled with that task. The other titles share
 * the first line, and the return note takes the second when there is one.
 */
internal fun currentNoticeCopy(
    rows: List<TodayNoticeRow>,
    target: FocusTarget?,
    todayLabel: String,
    inFocusLabel: String,
): CurrentNoticeCopy {
    if (target == null) {
        return CurrentNoticeCopy(
            title = todayLabel,
            body = twoLines(rows.map { it.title }),
            idle = true,
        )
    }
    val others = rows.filter { it.taskId != target.taskId }.map { it.title }
    val cue = target.returnCue?.trim()?.takeIf { it.isNotEmpty() }
    val body =
        when {
            others.isNotEmpty() && cue != null -> others.joinToString(separator = " · ") + "\n" + cue
            others.isNotEmpty() -> twoLines(others)
            cue != null -> cue
            else -> inFocusLabel
        }
    return CurrentNoticeCopy(title = target.title, body = body, idle = false)
}

/** Splits titles across the two lines a collapsed notice can show. */
internal fun twoLines(titles: List<String>): String {
    val parts = titles.filter { it.isNotBlank() }
    if (parts.size <= 1) return parts.firstOrNull().orEmpty()
    val midpoint = (parts.size + 1) / 2
    return parts.take(midpoint).joinToString(separator = " · ") + "\n" + parts.drop(midpoint).joinToString(separator = " · ")
}

/**
 * Channel for the ongoing notice. Idle uses [NotificationContract.CHANNEL_TODAY],
 * which is default importance, because the older low channel did not show a
 * status icon. An active or waiting task stays on [NotificationContract.CHANNEL_CURRENT].
 */
internal fun todayNoticeChannel(idle: Boolean): String =
    if (idle) NotificationContract.CHANNEL_TODAY else NotificationContract.CHANNEL_CURRENT

/**
 * Ongoing, silent notice. Idle and active both use default importance so a
 * small status icon can show, including when nothing is started. Neither is
 * a heads-up or a foreground service. There is no elapsed-time ticker and no
 * full-screen intent.
 */
internal fun buildCurrentTaskNotification(
    context: Context,
    title: String,
    text: String,
    todayRows: List<TodayNoticeRow> = emptyList(),
    idle: Boolean = false,
): android.app.Notification {
    val builder =
        NotificationCompat.Builder(context, todayNoticeChannel(idle))
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                focusPendingIntent(context, NotificationContract.DESTINATION_FOCUS, requestCode = 1),
            )
            .addAction(
                0,
                context.getString(R.string.action_interrupt),
                focusPendingIntent(context, NotificationContract.DESTINATION_CAPTURE, requestCode = 2),
            )
            .setCustomContentView(todayCollapsedContent(context, text))
    if (todayRows.isNotEmpty()) {
        builder.setCustomBigContentView(todayBigContent(context, todayRows))
    }
    builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
    return builder.build()
}

/** Collapsed body, capped at two lines so the titles are not one clipped run. */
internal fun todayCollapsedContent(
    context: Context,
    text: String,
): RemoteViews {
    val summary = RemoteViews(context.packageName, R.layout.notification_today_summary)
    summary.setTextViewText(R.id.notification_summary, text)
    return summary
}

private val todayRowIds =
    intArrayOf(
        R.id.today_row_0,
        R.id.today_row_1,
        R.id.today_row_2,
        R.id.today_row_3,
        R.id.today_row_4,
    )

/**
 * Expanded shade lists Today in five fixed slots. Empty slots are [View.GONE].
 * The current or waiting row is mint and opens Focus. Every other row switches.
 */
internal fun todayBigContent(
    context: Context,
    rows: List<TodayNoticeRow>,
): RemoteViews {
    val root = RemoteViews(context.packageName, R.layout.notification_today)
    val shown = rows.take(MAX_TODAY_LIMIT)
    val mint = context.getColor(R.color.mint)
    val onMint = context.getColor(R.color.on_mint)
    todayRowIds.forEachIndexed { index, viewId ->
        val row = shown.getOrNull(index)
        if (row == null) {
            root.setViewVisibility(viewId, View.GONE)
            return@forEachIndexed
        }
        root.setViewVisibility(viewId, View.VISIBLE)
        root.setTextViewText(viewId, row.title)
        if (row.current) {
            root.setInt(viewId, "setBackgroundColor", mint)
            root.setTextColor(viewId, onMint)
            root.setContentDescription(viewId, context.getString(R.string.notification_now_row, row.title))
            root.setOnClickPendingIntent(
                viewId,
                focusPendingIntent(context, NotificationContract.DESTINATION_FOCUS, requestCode = 80 + index),
            )
        } else {
            root.setInt(viewId, "setBackgroundColor", Color.TRANSPARENT)
            root.setContentDescription(viewId, context.getString(R.string.notification_switch_row, row.title))
            root.setOnClickPendingIntent(viewId, switchPendingIntent(context, row.taskId))
        }
    }
    return root
}

internal fun switchPendingIntent(
    context: Context,
    taskId: String,
): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        switchBroadcast(context, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun switchBroadcast(
    context: Context,
    taskId: String,
): Intent =
    Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationContract.ACTION_SWITCH
        data = Uri.parse("app.backpin.focus://switch/$taskId")
        putExtra(NotificationContract.EXTRA_TASK_ID, taskId)
    }

internal fun focusPendingIntent(
    context: Context,
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

/**
 * Registers the active notice and the idle Today list at default importance.
 * [NotificationContract.CHANNEL_CURRENT_LEGACY] stays unused.
 * [NotificationContract.CHANNEL_TODAY_IDLE] is removed when this install
 * already created it: Android will not raise that low channel, and recreating
 * the same id would restore the quiet importance. Neither live channel is high.
 */
internal fun ensureFocusChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val current =
        NotificationChannel(
            NotificationContract.CHANNEL_CURRENT,
            context.getString(R.string.channel_current_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_current_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
    val today =
        NotificationChannel(
            NotificationContract.CHANNEL_TODAY,
            context.getString(R.string.channel_today_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_today_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
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
    manager.createNotificationChannel(today)
    manager.createNotificationChannel(nudge)
    // Drop the quiet idle channel if this install already created it. Recreating
    // that same id would restore the low importance, so the new id stays.
    manager.deleteNotificationChannel(NotificationContract.CHANNEL_TODAY_IDLE)
}
