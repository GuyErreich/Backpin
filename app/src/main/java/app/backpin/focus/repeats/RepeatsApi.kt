// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.repeats

import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimeResult
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.TodaysRepeats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-process repeats backend. Home, capture, Settings, and notifications call this.
 * Arguments are plain data. Observable state is a [Flow]. No Compose or Android UI types.
 */
interface RepeatsApi {
    /**
     * Unarchived repeats in [Repeat.sortOrder].
     * Used to open the edit sheet.
     */
    fun observeRepeats(): Flow<List<Repeat>>

    /**
     * Current local epoch day from the injected clock and system zone.
     * Emits on local midnight, a system date/time/zone change, or
     * [refreshToday]. Equal days do not re-emit.
     */
    fun observeLocalEpochDay(): Flow<Long>

    /**
     * Today's due items per band, with open / done / missed.
     * Repeats never enter Today and never become focus work.
     * Recomputes when the local day changes: local midnight, a system
     * date/time/zone change, or [refreshToday].
     */
    fun observeTodaysRepeats(): Flow<TodaysRepeats>

    /**
     * Home REPEAT rows: open in band order, then missed, then done.
     * Done keeps the relative order from the band lists.
     * [RepeatRowsState.leftCount] and [RepeatRowsState.total] are the "N of M left" numbers.
     * Each [app.backpin.focus.domain.RepeatRow] includes [app.backpin.focus.domain.RepeatRow.everyNDays]
     * (also [app.backpin.focus.domain.RepeatRow.cadenceDays]) so the UI can show "every N days"
     * when the interval is not daily.
     * Recomputes from the same local-day signal as [observeTodaysRepeats].
     */
    fun observeRows(): Flow<RepeatRowsState>

    /**
     * Create or edit sheet draft, or null when the sheet is closed.
     */
    fun observeEditing(): Flow<RepeatDraft?>

    /**
     * Last [setBandTime] result, or null before the first edit.
     */
    fun observeBandTimeResult(): Flow<BandTimeResult?>

    /**
     * Current user-set morning, noon, night, and night-end minutes.
     */
    fun observeBandTimes(): Flow<BandTimes>

    /**
     * True when Settings should show the one-time exact-alarm prompt.
     * That is: the OS denies exact alarms and the user has not dismissed the prompt.
     */
    fun observeExactAlarmPrompt(): Flow<Boolean>

    /**
     * Pending check-off undo, or null. The UI supplies the snackbar copy.
     */
    fun observePendingUndo(): Flow<RepeatUndo?>

    /**
     * Opens the create sheet, prefilled with [title].
     */
    fun openCreate(title: String = "")

    /**
     * Opens the edit sheet for an existing [repeat].
     */
    fun openEdit(repeat: Repeat)

    /**
     * Closes the create or edit sheet without writing.
     */
    fun dismissSheet()

    /**
     * Saves a valid [draft] and closes the sheet. Done and Back both call this
     * so closing keeps the changes. A blank title writes nothing and leaves
     * the sheet open. Create when [RepeatDraft.id] is null; otherwise update.
     * A zero [RepeatDraft.anchorEpochDay] stores the current local day from the
     * injected clock and system zone.
     */
    suspend fun commitRepeatDraft(draft: RepeatDraft): Boolean

    /**
     * Adds a repeat and rebuilds band alarms.
     * A blank title writes nothing and leaves the sheet open.
     * A successful write closes the sheet and returns the new id.
     */
    suspend fun create(
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean = true,
    ): String?

    /**
     * Rewrites every field on an unarchived repeat and rebuilds band alarms.
     * Success closes the sheet.
     */
    suspend fun update(
        id: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
        sortOrder: Long,
    ): Boolean

    /**
     * Soft-deletes a repeat. Success closes the sheet.
     */
    suspend fun archive(id: String): Boolean

    /**
     * Writes DONE for that due day, including a late check-off on a missed row.
     * Offers [observePendingUndo] so a later [undo] can restore OPEN or MISSED.
     * Safe to call twice; a second call does not replace the undo.
     */
    suspend fun markDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean

    /**
     * Undoes a DONE log for that day.
     * When a matching [RepeatUndo] is pending, MISSED is written back;
     * otherwise the DONE row is removed.
     */
    suspend fun undoDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean

    /**
     * Applies [observePendingUndo] when [id] matches, then clears it.
     */
    suspend fun undo(id: Long): Boolean

    /**
     * Drops [observePendingUndo] when [id] matches, without writing.
     */
    fun dismissUndo(id: Long)

    /**
     * Replaces every band bound. Invalid or out-of-order values write nothing.
     */
    suspend fun setBandTimes(times: BandTimes): Boolean

    /**
     * One band bound. The UI passes only the picked minute.
     * Order is morning < noon < night < night end ≤ 23:59.
     */
    suspend fun setBandTime(
        which: BandEdge,
        minuteOfDay: Int,
    ): BandTimeResult

    /**
     * True when the OS will honor exact band alarms.
     */
    fun canScheduleExactAlarms(): Boolean

    /**
     * Re-reads [canScheduleExactAlarms] after Settings returns from the system prompt.
     */
    fun refreshExactAlarmPermission()

    /**
     * Re-reads the current local day from the clock and system zone.
     * Call when the app returns to foreground so Home drops yesterday's
     * missed repeats if the date changed while the UI was stopped.
     * No-op when the local day has not changed.
     */
    fun refreshToday()

    /**
     * Persists that the user chose "Not now" on the exact-alarm prompt.
     */
    suspend fun dismissExactAlarmPrompt()

    companion object {
        /**
         * No-op API so screens that do not show repeats can still construct a ViewModel.
         */
        val Empty: RepeatsApi = EmptyRepeatsApi
    }
}

/**
 * One check-off the snackbar can reverse. [restore] is OPEN or MISSED.
 *
 * @property id Stable key the snackbar uses for undo versus dismiss.
 * @property repeatId The checked-off repeat.
 * @property epochDay The local day that was checked off.
 * @property restore Status to write back: OPEN deletes the log; MISSED writes MISSED.
 */
data class RepeatUndo(
    val id: Long,
    val repeatId: String,
    val epochDay: Long,
    val restore: RepeatDayStatus,
)

private object EmptyRepeatsApi : RepeatsApi {
    override fun observeRepeats(): Flow<List<Repeat>> = flowOf(emptyList())

    override fun observeLocalEpochDay(): Flow<Long> = flowOf(0L)

    override fun observeTodaysRepeats(): Flow<TodaysRepeats> = flowOf(TodaysRepeats())

    override fun observeRows(): Flow<RepeatRowsState> = flowOf(RepeatRowsState())

    override fun observeEditing(): Flow<RepeatDraft?> = flowOf(null)

    override fun observeBandTimeResult(): Flow<BandTimeResult?> = flowOf(null)

    override fun observeBandTimes(): Flow<BandTimes> = flowOf(BandTimes.DEFAULT)

    override fun observeExactAlarmPrompt(): Flow<Boolean> = flowOf(false)

    override fun observePendingUndo(): Flow<RepeatUndo?> = flowOf(null)

    override fun openCreate(title: String) = Unit

    override fun openEdit(repeat: Repeat) = Unit

    override fun dismissSheet() = Unit

    override suspend fun commitRepeatDraft(draft: RepeatDraft): Boolean = false

    override suspend fun create(
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
    ): String? = null

    override suspend fun update(
        id: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
        sortOrder: Long,
    ): Boolean = false

    override suspend fun archive(id: String): Boolean = false

    override suspend fun markDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean = false

    override suspend fun undoDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean = false

    override suspend fun undo(id: Long): Boolean = false

    override fun dismissUndo(id: Long) = Unit

    override suspend fun setBandTimes(times: BandTimes): Boolean = false

    override suspend fun setBandTime(
        which: BandEdge,
        minuteOfDay: Int,
    ): BandTimeResult = BandTimeResult.Rejected

    override fun canScheduleExactAlarms(): Boolean = false

    override fun refreshExactAlarmPermission() = Unit

    override fun refreshToday() = Unit

    override suspend fun dismissExactAlarmPrompt() = Unit
}
