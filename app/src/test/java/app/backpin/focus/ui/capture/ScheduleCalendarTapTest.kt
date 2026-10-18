// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ScheduleCalendarTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tomorrowInSevenDaysOrAPickedDateSchedulesOnceAndCloses() {
        val wakeUps = mutableListOf<Long>()
        withViewModel(wakeUps) { viewModel, useCases ->
            var open by mutableStateOf(true)
            compose.setContent {
                if (!open) return@setContent
                FocusStackTheme {
                    CaptureSheet(
                        phrases = emptyList(),
                        frameActive = false,
                        onDismiss = { open = false },
                        onSaveToInbox = viewModel::saveToInbox,
                        onInterruptNow = viewModel::interruptNow,
                        onPlaceToday = viewModel::placeOnToday,
                        onPlaceStart = viewModel::placeAndStart,
                        onPlaceLater = viewModel::placeLater,
                        onSchedule = viewModel::placeScheduled,
                        recognizer = QuietRecognizer,
                    )
                }
            }

            fun reopen() {
                compose.runOnIdle { open = true }
                compose.waitForIdle()
            }

            compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
            tap("Schedule")
            compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Add a time").assertDoesNotExist()
            compose.onNodeWithText("Change date").assertDoesNotExist()
            tap("Tomorrow")
            val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
            awaitScheduled(useCases, "Call Dana", tomorrow, minute = null)
            assertEquals(1, taskCount(useCases))
            assertSheetClosed()

            reopen()
            compose.onNode(hasSetTextAction()).performTextInput("File the form")
            tap("Schedule")
            compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
            tap("In 7 days")
            awaitScheduled(useCases, "File the form", LocalDate.now().plusDays(7).toEpochDay(), minute = null)
            assertEquals(2, taskCount(useCases))
            assertSheetClosed()

            reopen()
            compose.onNode(hasSetTextAction()).performTextInput("Book the room")
            tap("Schedule")
            tap("Pick a date")
            tap("OK")
            awaitScheduled(useCases, "Book the room", tomorrow, minute = null)
            assertEquals(3, taskCount(useCases))
            assertSheetClosed()
            assertTrue(wakeUps.isEmpty())
        }
    }

    @Test
    fun cancellingTheCalendarLeavesTheQuickDaysInPlace() {
        val placed = mutableListOf<String>()
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = false,
                    onDismiss = {},
                    onSaveToInbox = { placed += "inbox:$it" },
                    onInterruptNow = { placed += "interrupt:$it" },
                    onPlaceToday = { placed += "today:$it" },
                    onPlaceStart = { placed += "start:$it" },
                    onPlaceLater = { placed += "later:$it" },
                    onSchedule = { title, day, minute ->
                        placed += if (minute == null) "schedule:$title:$day" else "schedule:$title:$day:$minute"
                    },
                    recognizer = QuietRecognizer,
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
        tap("Schedule")
        tap("Pick a date")
        tap("Cancel")
        compose.onNodeWithText("Add a time").assertDoesNotExist()
        compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Change date").assertDoesNotExist()
        tap("In 7 days")
        assertEquals(listOf("schedule:Call Dana:${LocalDate.now().plusDays(7).toEpochDay()}"), placed)
    }

    private fun assertSheetClosed() {
        compose.onNodeWithText("When should it come back?").assertDoesNotExist()
        compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
    }

    private fun withViewModel(
        wakeUps: MutableList<Long>,
        block: (FocusStackViewModel, FocusUseCases) -> Unit,
    ) {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "calendar-tap-${UUID.randomUUID()}"),
                    clock = AppClock { System.currentTimeMillis() },
                ),
                wakeUp = { wakeUps += it },
            )
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            block(viewModel, useCases)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun taskCount(useCases: FocusUseCases): Int = runBlocking { useCases.observeSnapshot().first().tasks.size }

    private fun awaitScheduled(
        useCases: FocusUseCases,
        title: String,
        epochDay: Long,
        minute: Int?,
    ): Task {
        lateinit var found: Task
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking {
                val snapshot = useCases.observeSnapshot().first()
                val task = snapshot.tasks.singleOrNull { it.title == title }
                if (
                    task != null &&
                    task.bucket == TaskBucket.SCHEDULED &&
                    task.scheduledOnEpochDay == epochDay &&
                    task.scheduledAtMinuteOfDay == minute &&
                    snapshot.frames.none { it.taskId == task.id }
                ) {
                    found = task
                    true
                } else {
                    false
                }
            }
        }
        return found
    }

    private fun tap(label: String): SemanticsNodeInteraction {
        compose.waitForIdle()
        val merged = compose.onAllNodesWithText(label).fetchSemanticsNodes()
        val node =
            if (merged.isNotEmpty()) {
                compose.onNodeWithText(label).assertIsEnabled().assertHasClickAction()
            } else {
                compose.onNodeWithText(label, useUnmergedTree = true)
            }
        node.performTouchInput { click() }
        compose.waitForIdle()
        return node
    }
}

private object QuietRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
