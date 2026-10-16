package app.backpin.focus.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.backpin.focus.MainActivity
import app.backpin.focus.R
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.SystemAppClock
import app.backpin.focus.domain.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Posts one Android group per band. The summary (`Morning · 3 things`) alerts.
 * Each open repeat is a quiet child with its own Done action. A running focus
 * session holds the start nudge until that frame ends or pauses.
 */
class RepeatNotificationCoordinator(
    private val context: Context,
    private val repository: FocusRepository,
    private val scheduler: BandScheduler,
    private val clock: AppClock = SystemAppClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val postedChildren = mutableMapOf<RepeatBand, MutableSet<Int>>()

    fun start(scope: CoroutineScope) {
        ensureFocusChannels(context)
        scope.launch {
            combine(
                repository.observeSnapshot().map { snapshot ->
                    snapshot.frames.any { it.state == FrameState.ACTIVE }
                },
                repository.preferences.map { it.heldBandNudge to it.heldBandEpochDay },
            ) { focusActive, held ->
                Triple(focusActive, held.first, held.second)
            }.distinctUntilChanged()
                .collect { (focusActive, heldBand, heldDay) ->
                    if (!focusActive && heldBand != null && heldDay != null) {
                        releaseHeld(heldBand, heldDay)
                    }
                }
        }
    }

    suspend fun handleBandAlarm(
        band: RepeatBand,
        kind: BandAlarmKind,
    ) {
        when (kind) {
            BandAlarmKind.START -> onBandStart(band)
            BandAlarmKind.FOLLOW_UP -> onFollowUp(band)
            BandAlarmKind.END -> onBandEnd(band)
        }
        scheduler.resyncNow()
    }

    /**
     * Done from a child notification. Writes the log without opening the app,
     * then drops that child and updates or cancels the summary.
     */
    suspend fun handleRepeatDone(
        repeatId: String?,
        epochDay: Long,
    ) {
        if (repeatId.isNullOrBlank()) return
        if (!repository.markRepeatDone(repeatId, epochDay)) return
        val repeat = repository.observeRepeats().first().firstOrNull { it.id == repeatId }
        val band = repeat?.band ?: return
        refreshBand(band, alert = false)
        scheduler.resyncNow()
    }

    private suspend fun onBandStart(band: RepeatBand) {
        val items = openReminderOccurrences(todaysInBand(band))
        val decision = decideStart(items)
        when (decision) {
            BandNudgeDecision.Post -> postGroup(band, items, alert = true)
            BandNudgeDecision.Hold -> repository.setHeldBandNudge(band, todayEpochDay())
            is BandNudgeDecision.Skip -> Unit
        }
    }

    private suspend fun onFollowUp(band: RepeatBand) {
        val today = todayEpochDay()
        val already = repository.followUpPostedDay(band) == today
        val items = openReminderOccurrences(todaysInBand(band))
        val preferences = repository.preferences.first()
        val decision =
            decideBandFollowUp(
                openReminders = items,
                focusActive = repository.hasActiveFocus(),
                notificationsAllowed = notificationsAllowed(context),
                quietStart = preferences.quietStart,
                quietEnd = preferences.quietEnd,
                time = localTime(),
                alreadyPosted = already,
            )
        if (decision is BandNudgeDecision.Post) {
            postGroup(band, items, alert = false)
            repository.setFollowUpPostedDay(band, today)
        }
    }

    private suspend fun onBandEnd(band: RepeatBand) {
        val today = todayEpochDay()
        repository.markOpenRepeatsMissed(band, today)
        cancelBand(band)
        val held = repository.preferences.first()
        if (held.heldBandNudge == band && held.heldBandEpochDay == today) {
            repository.setHeldBandNudge(null, null)
        }
    }

    private suspend fun releaseHeld(
        band: RepeatBand,
        epochDay: Long,
    ) {
        repository.setHeldBandNudge(null, null)
        if (epochDay != todayEpochDay()) return
        val items = openReminderOccurrences(todaysInBand(band))
        when (decideStart(items)) {
            BandNudgeDecision.Post -> postGroup(band, items, alert = true)
            BandNudgeDecision.Hold -> repository.setHeldBandNudge(band, epochDay)
            is BandNudgeDecision.Skip -> Unit
        }
    }

    private suspend fun decideStart(items: List<RepeatOccurrence>): BandNudgeDecision {
        val preferences = repository.preferences.first()
        return decideBandNudge(
            openReminders = items,
            focusActive = repository.hasActiveFocus(),
            notificationsAllowed = notificationsAllowed(context),
            quietStart = preferences.quietStart,
            quietEnd = preferences.quietEnd,
            time = localTime(),
        )
    }

    private suspend fun refreshBand(
        band: RepeatBand,
        alert: Boolean,
    ) {
        val items = openReminderOccurrences(todaysInBand(band))
        if (items.isEmpty()) {
            cancelBand(band)
            return
        }
        postGroup(band, items, alert = alert)
    }

    private suspend fun todaysInBand(band: RepeatBand): List<RepeatOccurrence> =
        repository.observeTodaysRepeats().first().inBand(band)

    private fun postGroup(
        band: RepeatBand,
        items: List<RepeatOccurrence>,
        alert: Boolean,
    ) {
        if (items.isEmpty()) {
            cancelBand(band)
            return
        }
        val group = bandGroupKey(band)
        val nextIds = mutableSetOf<Int>()
        for (item in items) {
            val id = repeatChildNotificationId(item.repeat.id, item.epochDay)
            nextIds += id
            notify(
                id,
                buildRepeatChildNotification(
                    context,
                    band,
                    item.repeat.id,
                    item.repeat.title,
                    item.repeat.everyNDays,
                    item.epochDay,
                    group,
                ),
            )
        }
        val previous = postedChildren.getOrPut(band) { mutableSetOf() }
        (previous - nextIds).forEach { cancel(it) }
        previous.clear()
        previous.addAll(nextIds)
        notify(
            repeatSummaryNotificationId(band),
            buildRepeatSummaryNotification(context, band, items.size, group, alert),
        )
    }

    private fun cancelBand(band: RepeatBand) {
        postedChildren.remove(band)?.forEach { cancel(it) }
        cancel(repeatSummaryNotificationId(band))
        val group = bandGroupKey(band)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications
            .filter { it.notification.group == group }
            .forEach { manager.cancel(it.id) }
    }

    private fun notify(
        id: Int,
        notification: android.app.Notification,
    ) {
        if (!notificationsAllowed(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun cancel(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun todayEpochDay(): Long =
        Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone()).toLocalDate().toEpochDay()

    private fun localTime(): LocalTime =
        Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone()).toLocalTime()
}

internal fun buildRepeatSummaryNotification(
    context: Context,
    band: RepeatBand,
    count: Int,
    group: String,
    alert: Boolean,
): android.app.Notification {
    val builder =
        NotificationCompat.Builder(context, NotificationContract.CHANNEL_REPEATS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(bandSummaryTitle(band, count))
            .setGroup(group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openHome(context, requestCode = 60 + band.ordinal))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    if (!alert) {
        builder.setSilent(true).setOnlyAlertOnce(true)
    }
    return builder.build()
}

internal fun buildRepeatChildNotification(
    context: Context,
    band: RepeatBand,
    repeatId: String,
    title: String,
    everyNDays: Int,
    epochDay: Long,
    group: String,
): android.app.Notification =
    NotificationCompat.Builder(context, NotificationContract.CHANNEL_REPEATS)
        .setSmallIcon(R.drawable.ic_stat_focus)
        .setContentTitle(title)
        .setContentText(bandChildText(everyNDays))
        .setGroup(group)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setContentIntent(openHome(context, requestCode = 70 + band.ordinal))
        .addAction(
            0,
            context.getString(R.string.action_done),
            repeatDonePending(context, repeatId, epochDay),
        ).setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

internal fun repeatDonePending(
    context: Context,
    repeatId: String,
    epochDay: Long,
): PendingIntent {
    val intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationContract.ACTION_REPEAT_DONE
            data = Uri.parse("app.backpin.focus://repeat-done/$repeatId/$epochDay")
            putExtra(NotificationContract.EXTRA_REPEAT_ID, repeatId)
            putExtra(NotificationContract.EXTRA_EPOCH_DAY, epochDay)
        }
    return PendingIntent.getBroadcast(
        context,
        31 * repeatId.hashCode() + epochDay.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun openHome(
    context: Context,
    requestCode: Int,
): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
