package app.backpin.focus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.UndoToken
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One activity-scoped model. Home, capture, and focus all read the database flow,
 * so a new process rebuilds NOW and recovery from persisted frames.
 */
class FocusStackViewModel(
    private val useCases: FocusUseCases,
) : ViewModel() {
    private val captureOpen = MutableStateFlow(false)
    private val captureInterrupts = MutableStateFlow(false)
    private val swapIncomingId = MutableStateFlow<String?>(null)
    private val freshSwapTitle = MutableStateFlow<String?>(null)
    private val switchOpen = MutableStateFlow(false)
    private val pendingUndo = MutableStateFlow<PendingUndo?>(null)

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
            combine(switchOpen, pendingUndo) { switching, undo ->
                switching to undo
            },
        ) { snapshot, preferences, capture, switchAndUndo ->
            val (switching, undo) = switchAndUndo
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
                ),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FocusUiState(preferences = UserPreferences()),
        )

    /**
     * Opens capture. [interrupts] is true from Interrupt, including the notification
     * Interrupt action, so a phrase chip starts now. + Capture leaves it false, and a
     * chip saves to Inbox without touching the active task.
     */
    fun openCapture(interrupts: Boolean = false) {
        captureInterrupts.value = interrupts
        captureOpen.value = true
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
            useCases.interrupt(title)
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

    fun placeScheduled(
        title: String,
        scheduledOnEpochDay: Long,
    ) {
        viewModelScope.launch {
            useCases.captureScheduled(title, scheduledOnEpochDay)
            if (title.isNotBlank()) closeCapture()
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
        val pending = pendingUndo.value ?: return
        if (pending.id != id) return
        pendingUndo.value = null
        viewModelScope.launch {
            useCases.undo(pending.token)
        }
    }

    fun dismissUndo(id: Long) {
        if (pendingUndo.value?.id == id) pendingUndo.value = null
    }

    private fun closeCapture() {
        captureOpen.value = false
        captureInterrupts.value = false
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
        token: UndoToken,
    ) {
        pendingUndo.value = PendingUndo(id = System.nanoTime(), message = message, token = token)
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusStackViewModel::class.java)) {
            return FocusStackViewModel(useCases) as T
        }
        error("Unknown ViewModel ${modelClass.name}")
    }
}
