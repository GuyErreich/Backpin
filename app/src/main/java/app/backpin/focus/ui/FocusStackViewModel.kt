// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimeResult
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.SystemAppClock
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TodaysRepeats
import app.backpin.focus.domain.UndoToken
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.domain.backInDaysLabel
import app.backpin.focus.domain.countdownIntervalDays
import app.backpin.focus.domain.currentMinuteOfDay
import app.backpin.focus.domain.editPlace
import app.backpin.focus.domain.homeBandsForToday
import app.backpin.focus.repeats.RepeatsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One activity-scoped model. Home, capture, and focus all read the database flow,
 * so a new process rebuilds NOW and recovery from persisted frames.
 */
class FocusStackViewModel(
    private val useCases: FocusUseCases,
    private val repeatsApi: RepeatsApi = RepeatsApi.Empty,
    internal val clock: AppClock = SystemAppClock,
) : ViewModel() {
    private val captureOpen = MutableStateFlow(false)
    private val captureInterrupts = MutableStateFlow(false)
    private val editingTaskId = MutableStateFlow<String?>(null)
    private val swapIncomingId = MutableStateFlow<String?>(null)
    private val freshSwapTitle = MutableStateFlow<String?>(null)
    private val switchOpen = MutableStateFlow(false)
    private val pendingUndo = MutableStateFlow<PendingUndo?>(null)
    private val taskSheetState = MutableStateFlow<TaskSheetUi?>(null)
    private val missedRepeatSheetState = MutableStateFlow<MissedRepeatSheetUi?>(null)
    private val missedTaskSheetState = MutableStateFlow<MissedTaskSheetUi?>(null)

    val uiState: StateFlow<FocusUiState> =
        combine(
            useCases.observeSnapshot(),
            useCases.preferences,
            combine(
                captureOpen,
                captureInterrupts,
                swapIncomingId,
                freshSwapTitle,
            ) { capture, interrupts, swap, fresh ->
                CaptureChrome(
                    captureOpen = capture,
                    captureInterrupts = interrupts,
                    swapIncomingId = swap,
                    freshSwapTitle = fresh,
                )
            },
            combine(switchOpen, pendingUndo, editingTaskId, repeatsApi.observePendingUndo()) { switching, taskUndo, editing, repeatUndo ->
                val undo =
                    listOfNotNull(
                        taskUndo,
                        repeatUndo?.let { pending ->
                            PendingUndo(
                                id = pending.id,
                                message = "",
                                target =
                                UndoTarget.RepeatCheckOff(
                                    repeatId = pending.repeatId,
                                    epochDay = pending.epochDay,
                                    restore = pending.restore,
                                ),
                            )
                        },
                    ).maxByOrNull { it.id }
                Triple(switching, undo, editing)
            },
        ) { snapshot, preferences, capture, extras ->
            val (switching, undo, editing) = extras
            deriveFocusUi(
                snapshot,
                ChromeState(
                    captureOpen = capture.captureOpen,
                    captureInterrupts = capture.captureInterrupts,
                    swapIncomingId = capture.swapIncomingId,
                    freshSwapTitle = capture.freshSwapTitle,
                    switchOpen = switching,
                    undo = undo,
                    preferences = preferences,
                    editingTaskId = editing,
                ),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FocusUiState(preferences = UserPreferences()),
        )

    /**
     * Today's due repeats per band, with open/done/missed. The REPEAT section
     * reads this. Repeats never appear in [uiState] Today slots.
     */
    val todaysRepeats: StateFlow<TodaysRepeats> =
        repeatsApi.observeTodaysRepeats().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TodaysRepeats(),
        )

    /**
     * Today's due repeats in Home order: open (band order), then missed, then done.
     * [RepeatRowsState.leftCount] and [RepeatRowsState.total] are the "N of M left" numbers.
     */
    val repeatRows: StateFlow<RepeatRowsState> =
        repeatsApi.observeRows().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RepeatRowsState(),
        )

    /** Unarchived repeats for the REPEAT section, in sort order. */
    val repeats: StateFlow<List<Repeat>> =
        repeatsApi.observeRepeats().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    /** Create or edit sheet for a repeat. Separate from [uiState] so task-edit chrome can land beside it. */
    val editingRepeat: StateFlow<RepeatDraft?> =
        repeatsApi.observeEditing().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    val bandTimeResult: StateFlow<BandTimeResult?> =
        repeatsApi.observeBandTimeResult().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    /**
     * Settings shows the one-time exact-alarm prompt when this is true.
     * Call [refreshExactAlarmPermission] when Settings resumes.
     */
    val showExactAlarmPrompt: StateFlow<Boolean> =
        repeatsApi.observeExactAlarmPrompt().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            false,
        )

    val bandTimes: StateFlow<BandTimes> =
        repeatsApi.observeBandTimes().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BandTimes.DEFAULT,
        )

    /** Chip-row task sheet, or null when it is closed. */
    val taskSheet: StateFlow<TaskSheetUi?> = taskSheetState

    /** Chip-row missed-repeat sheet, or null when it is closed. */
    val missedRepeatSheet: StateFlow<MissedRepeatSheetUi?> = missedRepeatSheetState

    /** Chip-row missed-task sheet, or null when it is closed. */
    val missedTaskSheet: StateFlow<MissedTaskSheetUi?> = missedTaskSheetState

    /**
     * Home Today tasks and today's repeats grouped by band.
     * Open Today tasks pin to the top of [HomeBands.openBand]. When no
     * timed window contains now that is Morning, never Anytime.
     *
     * Recomputes on the local-day signal from [RepeatsApi.observeLocalEpochDay],
     * on [RepeatsApi.observeBandBoundaryTick] at morning/noon/night/night-end,
     * on [RepeatsApi.observeBandTimes], on the snapshot, and on today's
     * repeat rows. Flat [uiState] Today slots and [repeatRows] stay as they are.
     *
     * Lory switches Home between this grouping and the flat lists using
     * `HomeLayout` from `PreferencesDataStore.homeLayout` once PR #29
     * (`cursor/theme-setting`) merges. Until then this flow is always
     * produced. Jenny owns header visuals on this branch later.
     */
    val homeBands: StateFlow<HomeBands> =
        combine(
            useCases.observeSnapshot(),
            repeatsApi.observeRows(),
            repeatsApi.observeBandTimes(),
            repeatsApi.observeLocalEpochDay(),
            repeatsApi.observeBandBoundaryTick(),
        ) { snapshot, rows, times, _, _ ->
            homeBandsForToday(
                tasks = snapshot.tasks,
                rows = rows,
                times = times,
                minuteOfDay = currentMinuteOfDay(clock),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeBands(),
        )

    /**
     * Opens capture. [interrupts] is true from Interrupt, including the notification
     * Interrupt action, so a phrase chip starts now. + Capture leaves it false, and a
     * chip saves to Inbox without touching the active task.
     */
    fun openCapture(interrupts: Boolean = false) {
        editingTaskId.value = null
        dismissActionSheets()
        repeatsApi.dismissSheet()
        captureInterrupts.value = interrupts
        captureOpen.value = true
    }

    /** Opens the edit sheet for [taskId]. Closes capture so only one sheet is up. */
    fun openEdit(taskId: String) {
        closeCapture()
        dismissActionSheets()
        repeatsApi.dismissSheet()
        editingTaskId.value = taskId
    }

    fun dismissEdit() {
        editingTaskId.value = null
    }

    /**
     * Opens the chip-row task sheet for [taskId]. Today open, Today waiting,
     * and pile rows share this sheet. Only one sheet is up.
     */
    fun openTaskSheet(taskId: String) {
        closeCapture()
        editingTaskId.value = null
        repeatsApi.dismissSheet()
        missedRepeatSheetState.value = null
        missedTaskSheetState.value = null
        val state = uiState.value
        val title = taskTitle(taskId, state) ?: return
        val waiting = state.recovery?.takeIf { it.taskId == taskId }
        val role =
            when {
                waiting != null -> TaskSheetRole.TODAY_WAITING
                state.todaySlots.any { it.task?.id == taskId } -> TaskSheetRole.TODAY_OPEN
                else -> TaskSheetRole.PILE
            }
        taskSheetState.value =
            TaskSheetUi(
                taskId = taskId,
                title = title,
                role = role,
                canInterrupt = state.canInterrupt && role != TaskSheetRole.TODAY_WAITING,
                frameId = waiting?.frameId,
            )
    }

    fun dismissTaskSheet() {
        taskSheetState.value = null
    }

    fun startFromTaskSheet() {
        val id = taskSheetState.value?.taskId ?: return
        dismissTaskSheet()
        start(id)
    }

    fun doneFromTaskSheet() {
        val id = taskSheetState.value?.taskId ?: return
        dismissTaskSheet()
        markDone(id)
    }

    fun laterFromTaskSheet() {
        val id = taskSheetState.value?.taskId ?: return
        dismissTaskSheet()
        demote(id)
    }

    fun editFromTaskSheet() {
        val id = taskSheetState.value?.taskId ?: return
        openEdit(id)
    }

    fun interruptFromTaskSheet() {
        val sheet = taskSheetState.value ?: return
        dismissTaskSheet()
        if (sheet.role == TaskSheetRole.PILE) {
            interruptFromPile(sheet.taskId)
        } else {
            interruptWith(sheet.taskId)
        }
    }

    fun todayFromTaskSheet() {
        val id = taskSheetState.value?.taskId ?: return
        dismissTaskSheet()
        promote(id)
    }

    fun resumeFromTaskSheet() {
        val frameId = taskSheetState.value?.frameId ?: return
        dismissTaskSheet()
        resume(frameId)
    }

    /**
     * Opens the missed-repeat sheet. The Home row stays muted and checkable
     * until local midnight. A tap no longer writes Done on its own.
     */
    fun openMissedRepeat(
        repeatId: String,
        epochDay: Long,
    ) {
        closeCapture()
        editingTaskId.value = null
        repeatsApi.dismissSheet()
        taskSheetState.value = null
        missedTaskSheetState.value = null
        val title =
            repeatRows.value.rows
                .firstOrNull { it.repeat.id == repeatId }
                ?.repeat
                ?.title
                ?: return
        missedRepeatSheetState.value = MissedRepeatSheetUi(repeatId, epochDay, title)
    }

    fun dismissMissedRepeat() {
        missedRepeatSheetState.value = null
    }

    fun doneMissedRepeat() {
        val pending = missedRepeatSheetState.value ?: return
        dismissMissedRepeat()
        markRepeatDone(pending.repeatId, pending.epochDay)
    }

    /**
     * Closes the missed-repeat sheet without writing Done.
     *
     * TODO(Tommy): RepeatsApi has no skip-today. Add skip(repeatId, epochDay)
     * if a skip should persist as something other than leaving the row MISSED.
     * Until that exists, Skip today only dismisses. The row stays muted and
     * checkable until local midnight.
     */
    fun skipMissedRepeat() {
        // TODO(Tommy): stub — no RepeatsApi.skip. Dismiss only.
        dismissMissedRepeat()
    }

    /** Opens the missed-task sheet for a due scheduled task. */
    fun openMissedTask(taskId: String) {
        closeCapture()
        editingTaskId.value = null
        repeatsApi.dismissSheet()
        taskSheetState.value = null
        missedRepeatSheetState.value = null
        val title =
            uiState.value.scheduledTasks.firstOrNull { it.id == taskId }?.title
                ?: taskTitle(taskId, uiState.value)
                ?: return
        missedTaskSheetState.value = MissedTaskSheetUi(taskId, title)
    }

    fun dismissMissedTask() {
        missedTaskSheetState.value = null
    }

    fun doneMissedTask() {
        val id = missedTaskSheetState.value?.taskId ?: return
        dismissMissedTask()
        markDone(id)
    }

    fun tomorrowMissedTask() {
        val id = missedTaskSheetState.value?.taskId ?: return
        dismissMissedTask()
        scheduleTaskOn(id, LocalDate.now().plusDays(1).toEpochDay())
    }

    fun pickDayMissedTask(epochDay: Long) {
        val id = missedTaskSheetState.value?.taskId ?: return
        dismissMissedTask()
        scheduleTaskOn(id, epochDay)
    }

    /**
     * Writes the edit and closes the sheet. A blank title keeps the old one.
     * [place] and [scheduledOnEpochDay] reuse the same move and schedule paths as
     * the cards, so a day or bucket change reschedules or cancels as before.
     */
    fun saveEdit(
        title: String,
        place: CapturePlace? = null,
        scheduledOnEpochDay: Long? = null,
    ) {
        val id = editingTaskId.value ?: return
        editingTaskId.value = null
        viewModelScope.launch {
            val task = useCases.observeSnapshot().first().tasks.firstOrNull { it.id == id } ?: return@launch
            val resolved = title.trim().ifEmpty { task.title }
            if (resolved != task.title) {
                useCases.rename(id, resolved)
            }
            when (place ?: task.bucket.editPlace()) {
                CapturePlace.INBOX -> if (task.bucket != TaskBucket.INBOX) useCases.moveToInbox(id)
                CapturePlace.TODAY ->
                    if (task.bucket != TaskBucket.TODAY) {
                        when (useCases.promote(id)) {
                            is PromoteOutcome.NeedsSwap -> swapIncomingId.value = id
                            is PromoteOutcome.Promoted -> swapIncomingId.value = null
                            is PromoteOutcome.Rejected -> Unit
                        }
                    }
                CapturePlace.LATER -> if (task.bucket != TaskBucket.LATER) useCases.demote(id)
                CapturePlace.SCHEDULE -> {
                    val day = scheduledOnEpochDay ?: task.scheduledOnEpochDay
                    val alreadyOnThatDay =
                        day == task.scheduledOnEpochDay &&
                            (task.bucket == TaskBucket.SCHEDULED || task.bucket == TaskBucket.COUNTDOWN)
                    if (day != null && !alreadyOnThatDay) {
                        useCases.scheduleExisting(id, day)
                    }
                }
                CapturePlace.START,
                CapturePlace.INTERRUPT,
                null,
                -> Unit
            }
        }
    }

    fun dismissCapture() {
        closeCapture()
    }

    fun saveToInbox(title: String) {
        viewModelScope.launch {
            useCases.capture(title)
            if (title.isNotBlank()) closeCapture()
        }
    }

    fun interruptNow(title: String) {
        viewModelScope.launch {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) return@launch
            if (uiState.value.now == null) return@launch
            useCases.interrupt(trimmed)
            closeCapture()
        }
    }

    /** Places a typed or phrase title on Today without starting it. A full Today waits for Replace or Interrupt. */
    fun placeOnToday(title: String) {
        viewModelScope.launch {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) return@launch
            when (useCases.captureIntoToday(trimmed)) {
                is PromoteOutcome.NeedsSwap -> {
                    freshSwapTitle.value = trimmed
                    closeCapture()
                }
                is PromoteOutcome.Promoted -> closeCapture()
                is PromoteOutcome.Rejected -> Unit
            }
        }
    }

    /**
     * Starts a new task now. An active frame is paused without nesting.
     * A full Today uses the same Replace or Interrupt choice as any other arrival.
     */
    fun placeAndStart(title: String) {
        viewModelScope.launch {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) return@launch
            when (useCases.captureAndStart(trimmed)) {
                is PromoteOutcome.NeedsSwap -> {
                    freshSwapTitle.value = trimmed
                    closeCapture()
                }
                is PromoteOutcome.Promoted -> closeCapture()
                is PromoteOutcome.Rejected -> Unit
            }
        }
    }

    fun placeLater(title: String) {
        viewModelScope.launch {
            useCases.captureLater(title)
            if (title.isNotBlank()) closeCapture()
        }
    }

    /** Schedules a new task for a day and, when [scheduledAtMinuteOfDay] is set, a time on it. */
    fun placeScheduled(
        title: String,
        scheduledOnEpochDay: Long,
        scheduledAtMinuteOfDay: Int? = null,
    ) {
        viewModelScope.launch {
            useCases.captureScheduled(title, scheduledOnEpochDay, scheduledAtMinuteOfDay)
            if (title.isNotBlank()) closeCapture()
        }
    }

    fun addCountdown(
        title: String,
        epochDay: Long,
    ) {
        viewModelScope.launch {
            useCases.captureCountdown(title, epochDay)
        }
    }

    fun changeCountdownDate(
        taskId: String,
        epochDay: Long,
    ) {
        viewModelScope.launch {
            useCases.rescheduleCountdown(taskId, epochDay)
        }
    }

    fun removeCountdown(taskId: String) {
        viewModelScope.launch {
            useCases.removeCountdown(taskId)
        }
    }

    /**
     * Sends an arrived event out of focus to Today, Later, or back to counting down.
     * The paused task then waits for Resume. A full Today opens Replace for the event.
     * A countdown resume stays on the current tab and offers Undo for the event and parent.
     */
    fun detachEvent(
        taskId: String,
        destination: DetachDestination,
    ) {
        viewModelScope.launch {
            val snapshot = useCases.observeSnapshot().first()
            val before = snapshot.tasks.firstOrNull { it.id == taskId }
            val frame = snapshot.frames.firstOrNull { it.taskId == taskId && it.state != FrameState.CLOSED }
            val parent = frame?.parentFrameId?.let { parentId -> snapshot.frames.firstOrNull { it.id == parentId } }
            when (useCases.detachInterruption(taskId, destination)) {
                is PromoteOutcome.NeedsSwap -> swapIncomingId.value = taskId
                is PromoteOutcome.Promoted -> {
                    swapIncomingId.value = null
                    if (destination is DetachDestination.Countdown && before != null) {
                        val today = LocalDate.now()
                        val days = countdownIntervalDays(destination.epochDay, today) ?: return@launch
                        offerUndo(
                            backInDaysLabel(days, today),
                            UndoToken(
                                tasks = listOf(before),
                                frames = listOfNotNull(frame, parent),
                            ),
                        )
                    }
                }
                is PromoteOutcome.Rejected -> Unit
            }
        }
    }

    fun moveToInbox(taskId: String) {
        viewModelScope.launch {
            useCases.moveToInbox(taskId)
        }
    }

    fun removeScheduled(taskId: String) {
        viewModelScope.launch {
            useCases.removeScheduled(taskId)
        }
    }

    /** Moves that scheduled task to [epochDay]. Does not create another task. */
    fun changeScheduledDate(
        taskId: String,
        epochDay: Long,
    ) {
        viewModelScope.launch {
            useCases.rescheduleScheduled(taskId, epochDay)
        }
    }

    /** Sets or clears the time on that scheduled task. Null clears it. */
    fun changeScheduledTime(
        taskId: String,
        minuteOfDay: Int?,
    ) {
        viewModelScope.launch {
            useCases.setScheduledTime(taskId, minuteOfDay)
        }
    }

    fun promote(taskId: String) {
        viewModelScope.launch {
            val before = useCases.observeSnapshot().first().tasks.firstOrNull { it.id == taskId }
            when (useCases.promote(taskId)) {
                is PromoteOutcome.NeedsSwap -> swapIncomingId.value = taskId
                is PromoteOutcome.Promoted -> {
                    swapIncomingId.value = null
                    if (before != null && before.bucket != TaskBucket.TODAY && before.bucket != TaskBucket.SCHEDULED) {
                        offerUndo("Added to Today", UndoToken(tasks = listOf(before), frames = emptyList()))
                    }
                }
                is PromoteOutcome.Rejected -> swapIncomingId.value = null
            }
        }
    }

    fun confirmReplace(outgoingTaskId: String) {
        val fresh = freshSwapTitle.value
        val incoming = swapIncomingId.value
        if (fresh == null && incoming == null) return
        viewModelScope.launch {
            val token =
                if (fresh != null) {
                    useCases.captureReplacing(fresh, outgoingTaskId)
                } else {
                    useCases.swapIntoToday(incoming ?: return@launch, outgoingTaskId)
                }
            freshSwapTitle.value = null
            swapIncomingId.value = null
            offerUndo("Swapped into Today", token)
        }
    }

    fun confirmInterrupt() {
        val fresh = freshSwapTitle.value
        val incoming = swapIncomingId.value
        if (fresh == null && incoming == null) return
        viewModelScope.launch {
            if (fresh != null) {
                useCases.interruptWithNew(fresh)
            } else if (incoming != null) {
                useCases.interruptWith(incoming)
            }
            freshSwapTitle.value = null
            swapIncomingId.value = null
        }
    }

    /**
     * Pauses the active task and nests an Inbox, Later, or Scheduled task under it.
     * Today is left alone, including when it is full. Hidden unless a frame is active.
     */
    fun interruptFromPile(taskId: String) {
        viewModelScope.launch {
            try {
                useCases.interruptFromPile(taskId)
            } catch (_: IllegalStateException) {
                // Nothing active, or a waiting frame, leaves the pile row where it is.
            }
        }
    }

    /**
     * Pauses the current Today task and starts [taskId] on top of it.
     * Both stay in Today. This is a nested interruption, not a switch.
     * A task waiting for an explicit resume is left alone.
     */
    fun interruptWith(taskId: String) {
        viewModelScope.launch {
            try {
                useCases.interruptWith(taskId)
            } catch (_: IllegalStateException) {
                // Resume stays explicit, and a stale row does not crash the swipe.
            }
        }
    }

    fun cancelSwap() {
        swapIncomingId.value = null
        freshSwapTitle.value = null
    }

    fun start(taskId: String) {
        viewModelScope.launch {
            when (val outcome = useCases.start(taskId)) {
                is PromoteOutcome.NeedsSwap -> swapIncomingId.value = taskId
                is PromoteOutcome.Promoted -> swapIncomingId.value = null
                is PromoteOutcome.Rejected -> swapIncomingId.value = null
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            useCases.pauseActive()
        }
    }

    fun completeActive() {
        viewModelScope.launch {
            useCases.completeActive()
        }
    }

    fun markDone(taskId: String) {
        viewModelScope.launch {
            useCases.completeTask(taskId)
        }
    }

    fun demote(taskId: String) {
        viewModelScope.launch {
            useCases.demote(taskId)
        }
    }

    /**
     * Sends a Home item to Coming up on [epochDay]. Arrived events count down
     * again; everything else is scheduled. Undo puts it back where it was.
     */
    fun bringBackOnDate(
        taskId: String,
        epochDay: Long,
        undoMessage: String,
    ) {
        viewModelScope.launch {
            val snapshot = useCases.observeSnapshot().first()
            val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return@launch
            val token = undoTokenFor(snapshot, taskId) ?: return@launch
            if (task.bucket == TaskBucket.COUNTDOWN) {
                when (useCases.detachInterruption(taskId, DetachDestination.Countdown(epochDay))) {
                    is PromoteOutcome.NeedsSwap -> swapIncomingId.value = taskId
                    is PromoteOutcome.Promoted -> {
                        swapIncomingId.value = null
                        offerUndo(undoMessage, token)
                    }
                    is PromoteOutcome.Rejected -> Unit
                }
            } else {
                useCases.scheduleExisting(taskId, epochDay)
                offerUndo(undoMessage, token)
            }
        }
    }

    /**
     * Parks a Home item in Inbox with no date. Undo puts it back where it was.
     */
    fun bringBackToInbox(
        taskId: String,
        undoMessage: String,
    ) {
        viewModelScope.launch {
            val snapshot = useCases.observeSnapshot().first()
            val token = undoTokenFor(snapshot, taskId) ?: return@launch
            useCases.moveToInbox(taskId)
            offerUndo(undoMessage, token)
        }
    }

    private fun undoTokenFor(
        snapshot: app.backpin.focus.domain.FocusSnapshot,
        taskId: String,
    ): UndoToken? {
        val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return null
        val frame = snapshot.frames.firstOrNull { it.taskId == taskId && it.state != FrameState.CLOSED }
        val parent = frame?.parentFrameId?.let { id -> snapshot.frames.firstOrNull { it.id == id } }
        return UndoToken(tasks = listOf(task), frames = listOfNotNull(frame, parent))
    }

    fun restore(taskId: String) {
        viewModelScope.launch {
            useCases.restore(taskId)
        }
    }

    fun reorderToday(orderedIds: List<String>) {
        viewModelScope.launch {
            val token = useCases.reorderToday(orderedIds) ?: return@launch
            offerUndo("Reordered Today", token)
        }
    }

    fun setNudge(
        enabled: Boolean,
        minutes: Int,
    ) {
        viewModelScope.launch {
            useCases.setNudge(enabled, minutes)
        }
    }

    fun setQuietHours(
        start: String?,
        end: String?,
    ) {
        viewModelScope.launch {
            useCases.setQuietHours(start, end)
        }
    }

    fun setCurrentNotification(enabled: Boolean) {
        viewModelScope.launch {
            useCases.setCurrentNotificationEnabled(enabled)
        }
    }

    fun setTodayLimit(limit: Int) {
        viewModelScope.launch {
            useCases.setTodayLimit(limit)
        }
    }

    fun openRepeatCreate(title: String = "") {
        closeCapture()
        dismissActionSheets()
        editingTaskId.value = null
        repeatsApi.openCreate(title)
    }

    fun openRepeatEdit(repeat: Repeat) {
        closeCapture()
        dismissActionSheets()
        editingTaskId.value = null
        repeatsApi.openEdit(repeat)
    }

    fun dismissRepeatSheet() {
        repeatsApi.dismissSheet()
    }

    fun commitRepeatDraft(draft: RepeatDraft) {
        viewModelScope.launch {
            if (repeatsApi.commitRepeatDraft(draft)) closeCapture()
        }
    }

    fun createRepeat(
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean = true,
    ) {
        viewModelScope.launch {
            if (repeatsApi.create(title, band, everyNDays, anchorEpochDay, reminderOn) != null) {
                closeCapture()
            }
        }
    }

    fun updateRepeat(
        id: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
        sortOrder: Long,
    ) {
        viewModelScope.launch {
            repeatsApi.update(id, title, band, everyNDays, anchorEpochDay, reminderOn, sortOrder)
        }
    }

    fun archiveRepeat(id: String) {
        viewModelScope.launch {
            repeatsApi.archive(id)
        }
    }

    fun markRepeatDone(
        repeatId: String,
        epochDay: Long,
    ) {
        viewModelScope.launch {
            repeatsApi.markDone(repeatId, epochDay)
        }
    }

    fun undoRepeatDone(
        repeatId: String,
        epochDay: Long,
    ) {
        viewModelScope.launch {
            repeatsApi.undoDone(repeatId, epochDay)
        }
    }

    fun setBandTimes(times: BandTimes) {
        viewModelScope.launch {
            repeatsApi.setBandTimes(times)
        }
    }

    fun setBandTime(
        which: BandEdge,
        minuteOfDay: Int,
    ) {
        viewModelScope.launch {
            repeatsApi.setBandTime(which, minuteOfDay)
        }
    }

    /** Settings can show the one-time exact-alarm prompt when this is false. */
    fun canScheduleExactAlarms(): Boolean = repeatsApi.canScheduleExactAlarms()

    fun refreshExactAlarmPermission() {
        repeatsApi.refreshExactAlarmPermission()
    }

    fun dismissExactAlarmPrompt() {
        viewModelScope.launch {
            repeatsApi.dismissExactAlarmPrompt()
        }
    }

    fun addSavedPhrase(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        editSavedPhrases { it + phrase }
    }

    fun renameSavedPhrase(
        index: Int,
        text: String,
    ) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        editSavedPhrases { current ->
            if (index !in current.indices) current else current.toMutableList().apply { this[index] = phrase }
        }
    }

    fun removeSavedPhrase(index: Int) {
        editSavedPhrases { current ->
            if (index !in current.indices) current else current.filterIndexed { i, _ -> i != index }
        }
    }

    fun resume(frameId: String) {
        viewModelScope.launch {
            useCases.resume(frameId)
        }
    }

    fun updateCue(
        taskId: String,
        cue: String,
    ) {
        viewModelScope.launch {
            useCases.updateReturnCue(taskId, cue)
        }
    }

    fun rename(
        taskId: String,
        title: String,
    ) {
        viewModelScope.launch {
            useCases.rename(taskId, title)
        }
    }

    fun openSwitch() {
        switchOpen.value = true
    }

    fun dismissSwitch() {
        switchOpen.value = false
    }

    fun switchTo(taskId: String) {
        switchOpen.value = false
        start(taskId)
    }

    fun undo(id: Long) {
        val pending = pendingUndo.value
        if (pending != null && pending.id == id) {
            pendingUndo.value = null
            viewModelScope.launch {
                when (val target = pending.target) {
                    is UndoTarget.Tasks -> useCases.undo(target.token)
                    is UndoTarget.RepeatCheckOff -> repeatsApi.undo(id)
                }
            }
            return
        }
        viewModelScope.launch {
            repeatsApi.undo(id)
        }
    }

    fun dismissUndo(id: Long) {
        if (pendingUndo.value?.id == id) pendingUndo.value = null
        repeatsApi.dismissUndo(id)
    }

    private fun closeCapture() {
        captureOpen.value = false
        captureInterrupts.value = false
    }

    private fun dismissActionSheets() {
        taskSheetState.value = null
        missedRepeatSheetState.value = null
        missedTaskSheetState.value = null
    }

    private fun taskTitle(
        taskId: String,
        state: FocusUiState,
    ): String? {
        state.now?.takeIf { it.taskId == taskId }?.title?.let { return it }
        state.recovery?.takeIf { it.taskId == taskId }?.title?.let { return it }
        state.paused?.takeIf { it.taskId == taskId }?.title?.let { return it }
        state.todaySlots.firstOrNull { it.task?.id == taskId }?.task?.title?.let { return it }
        state.inboxTasks.firstOrNull { it.id == taskId }?.title?.let { return it }
        state.laterTasks.firstOrNull { it.id == taskId }?.title?.let { return it }
        state.scheduledTasks.firstOrNull { it.id == taskId }?.title?.let { return it }
        return null
    }

    private fun scheduleTaskOn(
        taskId: String,
        epochDay: Long,
    ) {
        viewModelScope.launch {
            useCases.scheduleExisting(taskId, epochDay)
        }
    }

    private fun editSavedPhrases(transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            val current = useCases.preferences.first().savedPhrases
            val next = transform(current)
            if (next != current) useCases.setSavedPhrases(next)
        }
    }

    private fun offerUndo(
        message: String,
        target: UndoTarget,
    ) {
        pendingUndo.value = PendingUndo(id = System.nanoTime(), message = message, target = target)
    }

    private fun offerUndo(
        message: String,
        token: UndoToken,
    ) {
        offerUndo(message, UndoTarget.Tasks(token))
    }

    private data class CaptureChrome(
        val captureOpen: Boolean,
        val captureInterrupts: Boolean,
        val swapIncomingId: String?,
        val freshSwapTitle: String?,
    )
}

class FocusStackViewModelFactory(
    private val useCases: FocusUseCases,
    private val repeatsApi: RepeatsApi = RepeatsApi.Empty,
    private val clock: AppClock = SystemAppClock,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusStackViewModel::class.java)) {
            return FocusStackViewModel(useCases, repeatsApi, clock) as T
        }
        error("Unknown ViewModel ${modelClass.name}")
    }
}
