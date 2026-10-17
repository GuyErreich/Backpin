// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.repeats

import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimeResult
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.ExactAlarmAccess
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.RepeatScheduleSync
import app.backpin.focus.domain.TodaysRepeats
import app.backpin.focus.domain.applyBandTime
import app.backpin.focus.domain.repeatRowsState
import app.backpin.focus.domain.toDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Owns repeats Room writes, band-alarm resync, and the exact-alarm prompt flag.
 * [FocusStackViewModel][app.backpin.focus.ui.FocusStackViewModel] only maps these flows.
 *
 * @param repository Room and preference writes for repeats.
 * @param schedule Rebuilds band start, follow-up, and end alarms.
 * @param exactAlarms Whether the OS will honor exact alarms.
 */
class RepeatsService(
    private val repository: FocusRepository,
    private val schedule: RepeatScheduleSync = RepeatScheduleSync {},
    private val exactAlarms: ExactAlarmAccess = ExactAlarmAccess { false },
) : RepeatsApi {
    private val editing = MutableStateFlow<RepeatDraft?>(null)
    private val pendingUndo = MutableStateFlow<RepeatUndo?>(null)
    private val lastBandTimeResult = MutableStateFlow<BandTimeResult?>(null)
    private val exactAlarmGranted = MutableStateFlow(exactAlarms.canScheduleExactAlarms())

    override fun observeRepeats(): Flow<List<Repeat>> = repository.observeRepeats()

    override fun observeTodaysRepeats(): Flow<TodaysRepeats> = repository.observeTodaysRepeats()

    override fun observeRows(): Flow<RepeatRowsState> =
        repository.observeTodaysRepeats().map { repeatRowsState(it) }

    override fun observeEditing(): Flow<RepeatDraft?> = editing

    override fun observeBandTimeResult(): Flow<BandTimeResult?> = lastBandTimeResult

    override fun observeBandTimes(): Flow<BandTimes> = repository.preferences.map { it.bandTimes }

    override fun observeExactAlarmPrompt(): Flow<Boolean> =
        combine(repository.preferences, exactAlarmGranted) { preferences, granted ->
            !granted && !preferences.exactAlarmPromptDismissed
        }

    override fun observePendingUndo(): Flow<RepeatUndo?> = pendingUndo

    override fun openCreate(title: String) {
        editing.value = RepeatDraft(title = title.trim())
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
                LocalDate.now().toEpochDay()
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

    override suspend fun dismissExactAlarmPrompt() {
        repository.dismissExactAlarmPrompt()
    }

    private suspend fun currentStatus(
        repeatId: String,
        epochDay: Long,
    ): RepeatDayStatus? =
        repository
            .observeTodaysRepeats()
            .first()
            .all()
            .firstOrNull { it.repeat.id == repeatId && it.epochDay == epochDay }
            ?.status
}
