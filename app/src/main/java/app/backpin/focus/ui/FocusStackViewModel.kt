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
    private val inboxExpanded = MutableStateFlow(false)
    private val laterExpanded = MutableStateFlow(false)
    private val archiveExpanded = MutableStateFlow(false)
    private val swapIncomingId = MutableStateFlow<String?>(null)
    private val switchOpen = MutableStateFlow(false)
    private val pendingUndo = MutableStateFlow<PendingUndo?>(null)

    val uiState: StateFlow<FocusUiState> =
        combine(
            useCases.observeSnapshot(),
            useCases.preferences,
            combine(captureOpen, inboxExpanded, laterExpanded, archiveExpanded) { capture, inbox, later, archive ->
                ListChrome(capture, inbox, later, archive)
            }.combine(combine(swapIncomingId, switchOpen, pendingUndo) { swap, switch, undo ->
                Triple(swap, switch, undo)
            }) { lists, dialogs ->
                ChromeParts(
                    captureOpen = lists.captureOpen,
                    inboxExpanded = lists.inboxExpanded,
                    laterExpanded = lists.laterExpanded,
                    archiveExpanded = lists.archiveExpanded,
                    swapIncomingId = dialogs.first,
                    switchOpen = dialogs.second,
                    undo = dialogs.third,
                )
            },
        ) { snapshot, preferences, chrome ->
            deriveFocusUi(
                snapshot,
                ChromeState(
                    captureOpen = chrome.captureOpen,
                    inboxExpanded = chrome.inboxExpanded,
                    laterExpanded = chrome.laterExpanded,
                    archiveExpanded = chrome.archiveExpanded,
                    swapIncomingId = chrome.swapIncomingId,
                    switchOpen = chrome.switchOpen,
                    undo = chrome.undo,
                    preferences = preferences,
                ),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FocusUiState(preferences = UserPreferences()),
        )

    fun openCapture() {
        captureOpen.value = true
    }

    fun dismissCapture() {
        captureOpen.value = false
    }

    fun saveToInbox(title: String) {
        viewModelScope.launch {
            useCases.capture(title)
            if (title.isNotBlank()) captureOpen.value = false
        }
    }

    fun interruptNow(title: String) {
        viewModelScope.launch {
            useCases.interrupt(title)
            captureOpen.value = false
        }
    }

    fun promote(taskId: String) {
        viewModelScope.launch {
            val before = useCases.observeSnapshot().first().tasks.firstOrNull { it.id == taskId }
            when (useCases.promote(taskId)) {
                is PromoteOutcome.NeedsSwap -> swapIncomingId.value = taskId
                is PromoteOutcome.Promoted -> {
                    swapIncomingId.value = null
                    if (before != null && before.bucket != TaskBucket.TODAY) {
                        offerUndo("Added to Today", UndoToken(tasks = listOf(before), frames = emptyList()))
                    }
                }
                is PromoteOutcome.Rejected -> swapIncomingId.value = null
            }
        }
    }

    fun confirmReplace(outgoingTaskId: String) {
        val incoming = swapIncomingId.value ?: return
        viewModelScope.launch {
            val token = useCases.swapIntoToday(incoming, outgoingTaskId)
            swapIncomingId.value = null
            offerUndo("Swapped into Today", token)
        }
    }

    fun confirmInterrupt() {
        val incoming = swapIncomingId.value ?: return
        viewModelScope.launch {
            useCases.interruptWith(incoming)
            swapIncomingId.value = null
        }
    }

    fun cancelSwap() {
        swapIncomingId.value = null
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

    fun toggleInbox() {
        inboxExpanded.value = !inboxExpanded.value
    }

    fun toggleLater() {
        laterExpanded.value = !laterExpanded.value
    }

    fun toggleArchive() {
        archiveExpanded.value = !archiveExpanded.value
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

    private fun offerUndo(
        message: String,
        token: UndoToken,
    ) {
        pendingUndo.value = PendingUndo(id = System.nanoTime(), message = message, token = token)
    }

    private data class ListChrome(
        val captureOpen: Boolean,
        val inboxExpanded: Boolean,
        val laterExpanded: Boolean,
        val archiveExpanded: Boolean,
    )

    private data class ChromeParts(
        val captureOpen: Boolean,
        val inboxExpanded: Boolean,
        val laterExpanded: Boolean,
        val archiveExpanded: Boolean,
        val swapIncomingId: String?,
        val switchOpen: Boolean,
        val undo: PendingUndo?,
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
