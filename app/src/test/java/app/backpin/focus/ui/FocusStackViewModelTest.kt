package app.backpin.focus.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.UNTITLED_INTERRUPTION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import android.os.Looper
import org.junit.After
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: FocusStackDatabase
    private lateinit var useCases: FocusUseCases

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "vm-${UUID.randomUUID()}"),
                    clock = object : AppClock {
                        override fun nowEpochMillis(): Long = 5_000L
                    },
                ),
            )
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun captureFromTheViewModelLeavesTheActiveFrame() =
        runBlocking {
            val viewModel = FocusStackViewModel(useCases)
            val collect = launch { viewModel.uiState.collect {} }
            val original = seedToday("Review CI failure")
            useCases.start(original)
            val before = useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id
            viewModel.saveToInbox("Buy milk")
            withTimeout(5_000) {
                viewModel.uiState.first { state -> state.inboxCount == 1 && state.now?.taskId == original }
            }
            val after = useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id
            assertEquals(before, after)
            viewModel.saveToInbox("   ")
            assertEquals(1, useCases.observeSnapshot().first().tasks.count { it.bucket == TaskBucket.INBOX })
            viewModel.dismissCapture()
            assertEquals(1, useCases.observeSnapshot().first().tasks.count { it.bucket == TaskBucket.INBOX })
            collect.cancel()
        }

    @Test
    fun doubleInterruptAndNewViewModelShowTheParentCue() =
        runBlocking {
            val first = FocusStackViewModel(useCases)
            val collect = launch { first.uiState.collect {} }
            val original = seedToday("Review CI failure")
            useCases.updateReturnCue(original, "Next: reproduce CI failure")
            useCases.start(original)
            first.interruptNow("Message John")
            withTimeout(5_000) { first.uiState.first { it.now?.title == "Message John" } }
            first.interruptNow("")
            withTimeout(5_000) { first.uiState.first { it.now?.title == UNTITLED_INTERRUPTION } }
            first.rename(first.uiState.value.now!!.taskId, "Talk to Sarah")
            withTimeout(5_000) { first.uiState.first { it.now?.title == "Talk to Sarah" } }
            first.completeActive()
            val recovered =
                withTimeout(5_000) {
                    first.uiState.first { it.recovery?.title == "Message John" && it.undo?.message == "Marked done" }
                }
            assertNull(recovered.now)
            assertEquals("Marked done", recovered.undo?.message)
            collect.cancel()

            val recreated = FocusStackViewModel(useCases)
            val collectAgain = launch { recreated.uiState.collect {} }
            val restored =
                withTimeout(5_000) {
                    recreated.uiState.first { it.recovery?.title == "Message John" }
                }
            assertEquals("Message John", restored.recovery?.title)
            recreated.resume(restored.recovery!!.frameId)
            withTimeout(5_000) { recreated.uiState.first { it.now?.title == "Message John" } }
            recreated.completeActive()
            val originalRecovery =
                withTimeout(5_000) {
                    recreated.uiState.first { it.recovery?.title == "Review CI failure" }
                }
            assertEquals("Next: reproduce CI failure", originalRecovery.recovery?.returnCue)
            val frameId = originalRecovery.recovery!!.frameId
            recreated.resume(frameId)
            val resumed =
                withTimeout(5_000) {
                    recreated.uiState.first { it.now?.title == "Review CI failure" }
                }
            assertEquals("Next: reproduce CI failure", resumed.now?.returnCue)
            assertTrue(resumed.now?.isInterruption == false)
            collectAgain.cancel()
        }

    @Test
    fun undoSnackbarRestoresDoneAndLater() =
        runBlocking {
            val viewModel = FocusStackViewModel(useCases)
            val collect = launch { viewModel.uiState.collect {} }
            val original = seedToday("Review CI failure")
            useCases.start(original)
            viewModel.completeActive()
            val done =
                withTimeout(5_000) {
                    viewModel.uiState.first { it.undo?.message == "Marked done" && it.now == null && it.recovery == null }
                }
            assertNull(done.now)
            assertNull(done.recovery)
            viewModel.undo(done.undo!!.id)
            withTimeout(5_000) { viewModel.uiState.first { it.now?.title == "Review CI failure" && it.undo == null } }

            viewModel.demote(original)
            val later =
                withTimeout(5_000) {
                    viewModel.uiState.first { state ->
                        state.undo?.message == "Moved to Later" && state.todaySlots.none { it.task != null }
                    }
                }
            assertEquals(0, later.todaySlots.count { it.task != null })
            viewModel.undo(later.undo!!.id)
            val restored =
                withTimeout(5_000) {
                    viewModel.uiState.first { state ->
                        state.undo == null && state.now?.taskId == original && state.todaySlots.any { it.task?.id == original }
                    }
                }
            assertEquals(SlotRole.NOW, restored.todaySlots.single { it.task?.id == original }.role)
            collect.cancel()
        }

    private suspend fun seedToday(title: String): String {
        useCases.capture(title)
        val id = useCases.observeSnapshot().first().tasks.single { it.title == title }.id
        useCases.promote(id)
        return id
    }
}
