// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.repeats

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimeResult
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.ExactAlarmAccess
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.RepeatScheduleSync
import app.backpin.focus.domain.SystemAppClock
import app.backpin.focus.domain.TodaysRepeats
import app.backpin.focus.domain.applyBandTime
import app.backpin.focus.domain.bandWindow
import app.backpin.focus.domain.isRepeatDue
import app.backpin.focus.domain.millisUntilNextBandBoundary
import app.backpin.focus.domain.repeatRowsState
import app.backpin.focus.domain.repeatStatusOnDay
import app.backpin.focus.domain.toDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

/**
 * Owns repeats Room writes, band-alarm resync, and the exact-alarm prompt flag.
 * [FocusStackViewModel][app.backpin.focus.ui.FocusStackViewModel] only maps these flows.
 *
 * Today's status follows [localEpochDay]: local midnight, a system date/time/zone
 * change, or [refreshToday]. Equal days do not re-emit. A miss stays missed
 * through its own local day, then drops off Home.
 *
 * [observeBandBoundaryTick] fires at morning, noon, night, and night-end
 * bounds, and restarts that wait on band-time edits, [refreshToday], or
 * a date/time/zone change.
 *
 * @param repository Room and preference writes for repeats.
 * @param schedule Rebuilds band start, follow-up, and end alarms.
 * @param exactAlarms Whether the OS will honor exact alarms.
 * @param clock Local-day source for draft anchors and the today signal.
 * @param context When set, registers for [Intent.ACTION_DATE_CHANGED],
 * [Intent.ACTION_TIME_CHANGED], and [Intent.ACTION_TIMEZONE_CHANGED].
 * @param zone System zone used with [clock] to read the local date.
 * @param scope Runs the delay until the next local midnight and the next
 * band bound.
 */
class RepeatsService(
    private val repository: FocusRepository,
    private val schedule: RepeatScheduleSync = RepeatScheduleSync {},
    private val exactAlarms: ExactAlarmAccess = ExactAlarmAccess { false },
    private val clock: AppClock = SystemAppClock,
    context: Context? = null,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : RepeatsApi {
    private val editing = MutableStateFlow<RepeatDraft?>(null)
    private val pendingUndo = MutableStateFlow<RepeatUndo?>(null)
    private val lastBandTimeResult = MutableStateFlow<BandTimeResult?>(null)
    private val exactAlarmGranted = MutableStateFlow(exactAlarms.canScheduleExactAlarms())
    private val localEpochDay = MutableStateFlow(currentLocalEpochDay())
    private val bandBoundaryTick = MutableStateFlow(clock.nowEpochMillis())
    private val rescheduleMidnight = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val rescheduleBandBoundary = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        val app = context?.applicationContext
        if (app != null) {
            registerDateChangeReceiver(app)
            scope.launch { emitAtEachLocalMidnight() }
            scope.launch { emitAtEachBandBoundary() }
        }
    }

    override fun observeRepeats(): Flow<List<Repeat>> = repository.observeRepeats()

    override fun observeLocalEpochDay(): Flow<Long> = localEpochDay

    override fun observeTodaysRepeats(): Flow<TodaysRepeats> = combine(
        repository.observeRepeats(),
        repository.observeTodaysRepeats(),
        observeBandTimes(),
        localEpochDay,
    ) { repeats, logged, times, day ->
        repeatsForLocalDay(repeats, logged, times, day)
    }

    override fun observeRows(): Flow<RepeatRowsState> = observeTodaysRepeats().map { repeatRowsState(it) }

    override fun observeEditing(): Flow<RepeatDraft?> = editing

    override fun observeBandTimeResult(): Flow<BandTimeResult?> = lastBandTimeResult

    override fun observeBandTimes(): Flow<BandTimes> = repository.preferences.map { it.bandTimes }

    override fun observeBandBoundaryTick(): Flow<Long> = bandBoundaryTick

    override fun observeExactAlarmPrompt(): Flow<Boolean> = combine(
        repository.preferences,
        exactAlarmGranted,
    ) { preferences, granted ->
        !granted && !preferences.exactAlarmPromptDismissed
    }

    override fun observePendingUndo(): Flow<RepeatUndo?> = pendingUndo

    override fun openCreate(
        title: String,
        convertsTaskId: String?,
    ) {
        editing.value =
            RepeatDraft(
                title = title.trim(),
                convertsTaskId = convertsTaskId,
            )
    }

    override fun openEdit(repeat: Repeat) {
        editing.value = repeat.toDraft()
    }

    override fun dismissSheet() {
        editing.value = null
    }

    override suspend fun commitRepeatDraft(draft: RepeatDraft): Boolean {
        val trimmed = draft.title.trim()
        val interval = draft.everyNDays.coerceAtLeast(1)
        val day =
            if (draft.anchorEpochDay != 0L) {
                draft.anchorEpochDay
            } else {
                currentLocalEpochDay()
            }
        val prepared = draft.copy(title = trimmed, everyNDays = interval, anchorEpochDay = day)
        editing.value = prepared
        if (trimmed.isEmpty()) return false
        val id = prepared.id
        return if (id == null) {
            create(trimmed, prepared.band, interval, day, prepared.reminderOn) != null
        } else {
            update(
                id = id,
                title = trimmed,
                band = prepared.band,
                everyNDays = interval,
                anchorEpochDay = day,
                reminderOn = prepared.reminderOn,
                sortOrder = prepared.sortOrder,
            )
        }
    }

    override suspend fun create(
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
    ): String? {
        val id = repository.createRepeat(title, band, everyNDays, anchorEpochDay, reminderOn) ?: return null
        editing.value = null
        schedule.resync()
        return id
    }

    override suspend fun update(
        id: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
        sortOrder: Long,
    ): Boolean {
        val changed = repository.updateRepeat(id, title, band, everyNDays, anchorEpochDay, reminderOn, sortOrder)
        if (changed) {
            editing.value = null
            schedule.resync()
        }
        return changed
    }

    override suspend fun archive(id: String): Boolean {
        val changed = repository.archiveRepeat(id)
        if (changed) {
            editing.value = null
            schedule.resync()
        }
        return changed
    }

    override suspend fun markDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean {
        val before = currentStatus(repeatId, epochDay)
        if (before == RepeatDayStatus.DONE) return true
        val changed = repository.markRepeatDone(repeatId, epochDay)
        if (!changed) return false
        pendingUndo.value =
            RepeatUndo(
                id = System.nanoTime(),
                repeatId = repeatId,
                epochDay = epochDay,
                restore = before ?: RepeatDayStatus.OPEN,
            )
        schedule.resync()
        return true
    }

    override suspend fun undoDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean {
        val pending = pendingUndo.value
        if (pending != null && pending.repeatId == repeatId && pending.epochDay == epochDay) {
            return undo(pending.id)
        }
        val changed = repository.undoRepeatDone(repeatId, epochDay)
        if (changed) schedule.resync()
        return changed
    }

    override suspend fun undo(id: Long): Boolean {
        val pending = pendingUndo.value ?: return false
        if (pending.id != id) return false
        pendingUndo.value = null
        val changed =
            when (pending.restore) {
                RepeatDayStatus.MISSED ->
                    repository.writeRepeatOutcome(pending.repeatId, pending.epochDay, RepeatOutcome.MISSED)
                RepeatDayStatus.OPEN,
                RepeatDayStatus.DONE,
                -> repository.undoRepeatDone(pending.repeatId, pending.epochDay)
            }
        if (changed) schedule.resync()
        return changed
    }

    override fun dismissUndo(id: Long) {
        if (pendingUndo.value?.id == id) pendingUndo.value = null
    }

    override suspend fun setBandTimes(times: BandTimes): Boolean {
        val changed = repository.setBandTimes(times)
        if (changed) schedule.resync()
        return changed
    }

    override suspend fun setBandTime(
        which: BandEdge,
        minuteOfDay: Int,
    ): BandTimeResult {
        val current = repository.preferences.first().bandTimes
        val next = applyBandTime(current, which, minuteOfDay)
        if (next == null) {
            lastBandTimeResult.value = BandTimeResult.Rejected
            return BandTimeResult.Rejected
        }
        val changed = repository.setBandTimes(next)
        if (changed) schedule.resync()
        val result = if (changed) BandTimeResult.Applied else BandTimeResult.Rejected
        lastBandTimeResult.value = result
        return result
    }

    override fun canScheduleExactAlarms(): Boolean = exactAlarms.canScheduleExactAlarms()

    override fun refreshExactAlarmPermission() {
        exactAlarmGranted.value = exactAlarms.canScheduleExactAlarms()
    }

    override fun refreshToday() {
        localEpochDay.value = currentLocalEpochDay()
        rescheduleBandBoundary.tryEmit(Unit)
        tickBandBoundary()
    }

    override suspend fun dismissExactAlarmPrompt() {
        repository.dismissExactAlarmPrompt()
    }

    private suspend fun currentStatus(
        repeatId: String,
        epochDay: Long,
    ): RepeatDayStatus? = observeTodaysRepeats()
        .first()
        .all()
        .firstOrNull { it.repeat.id == repeatId && it.epochDay == epochDay }
        ?.status

    private fun registerDateChangeReceiver(app: Context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    refreshToday()
                    rescheduleMidnight.tryEmit(Unit)
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private suspend fun emitAtEachLocalMidnight() {
        while (true) {
            val wait = millisUntilNextLocalMidnight()
            val rescheduled = withTimeoutOrNull(wait) { rescheduleMidnight.first() } != null
            if (!rescheduled) {
                refreshToday()
            }
        }
    }

    private suspend fun emitAtEachBandBoundary() {
        while (true) {
            val times = observeBandTimes().first()
            val wait = millisUntilNextBandBoundary(clock.nowEpochMillis(), times, zone())
            val rescheduled =
                withTimeoutOrNull(wait) {
                    merge(
                        rescheduleBandBoundary,
                        observeBandTimes().drop(1).map { Unit },
                    ).first()
                } != null
            if (!rescheduled) {
                tickBandBoundary()
            }
        }
    }

    private fun tickBandBoundary() {
        bandBoundaryTick.value = clock.nowEpochMillis()
    }

    private fun repeatsForLocalDay(
        repeats: List<Repeat>,
        logged: TodaysRepeats,
        times: BandTimes,
        day: Long,
    ): TodaysRepeats {
        val now = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone())
        val nowMinute = now.hour * 60 + now.minute
        val known = logged.all().filter { it.epochDay == day }.associateBy { it.repeat.id }
        return TodaysRepeats(
            morning = occurrencesForBand(RepeatBand.MORNING, repeats, known, times, day, nowMinute),
            noon = occurrencesForBand(RepeatBand.NOON, repeats, known, times, day, nowMinute),
            night = occurrencesForBand(RepeatBand.NIGHT, repeats, known, times, day, nowMinute),
        )
    }

    private fun occurrencesForBand(
        band: RepeatBand,
        repeats: List<Repeat>,
        known: Map<String, RepeatOccurrence>,
        times: BandTimes,
        day: Long,
        nowMinute: Int,
    ): List<RepeatOccurrence> {
        val bandEnded = nowMinute >= bandWindow(band, times).endMinute
        return repeats
            .filter { it.band == band && isRepeatDue(it.anchorEpochDay, it.everyNDays, day) }
            .map { repeat ->
                RepeatOccurrence(
                    repeat = repeat,
                    epochDay = day,
                    status = statusOnLocalDay(known[repeat.id]?.status, bandEnded),
                )
            }
    }

    private fun statusOnLocalDay(
        loggedStatus: RepeatDayStatus?,
        bandEnded: Boolean,
    ): RepeatDayStatus = if (loggedStatus == RepeatDayStatus.DONE || loggedStatus == RepeatDayStatus.MISSED) {
        loggedStatus
    } else {
        repeatStatusOnDay(null, bandEnded)
    }

    private fun currentLocalEpochDay(): Long = Instant.ofEpochMilli(clock.nowEpochMillis())
        .atZone(zone())
        .toLocalDate()
        .toEpochDay()

    private fun millisUntilNextLocalMidnight(): Long {
        val zoneId = zone()
        val nowMillis = clock.nowEpochMillis()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return (nextMidnight - nowMillis).coerceAtLeast(1L)
    }
}
