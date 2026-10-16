package app.backpin.focus.ui.repeat

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatCheckOffTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun tappingARowChecksItOffDropsItAndUndoRestores() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.useCases.createRepeat("Take meds", RepeatBand.MORNING, 1, today)
                env.useCases.createRepeat("Water plants", RepeatBand.NOON, 1, today)
                env.useCases.createRepeat("Make bed", RepeatBand.MORNING, 1, today)
                env.useCases.markRepeatDone(idOf(env, "Make bed"), today)
            }
            showHome(env.viewModel)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.todaysRepeats.value.let { todays ->
                        orderedRepeatRows(todays).map { it.repeat.title } ==
                            listOf("Take meds", "Water plants", "Make bed")
                    }
            }
            compose.onNodeWithText("2 of 3 left").assertExists()
            tapDescription("Take meds. Morning. Check off.")
            await {
                val rows = orderedRepeatRows(env.viewModel.todaysRepeats.value)
                rows.map { it.repeat.title } == listOf("Water plants", "Take meds", "Make bed") &&
                    rows.last().status == RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.message == "Checked off"
            }
            assertEquals("1 of 3 left", repeatsLeftLabel(orderedRepeatRows(env.viewModel.todaysRepeats.value)))
            val titles = titleOrder("Water plants", "Take meds", "Make bed")
            assertEquals(listOf("Water plants", "Take meds", "Make bed"), titles)
            val undo = env.viewModel.uiState.value.undo
            assertNotNull(undo)
            env.viewModel.undo(undo!!.id)
            await {
                orderedRepeatRows(env.viewModel.todaysRepeats.value).let { rows ->
                    rows.first().repeat.title == "Take meds" &&
                        rows.first().status == RepeatDayStatus.OPEN &&
                        env.viewModel.uiState.value.undo == null
                }
            }
            compose.onNodeWithContentDescription("Take meds. Morning. Check off.").assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun markRepeatDoneOffersCheckedOffUndo() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            val id =
                runOffTestThread {
                    env.useCases.createRepeat("Take meds", RepeatBand.MORNING, 1, today)!!
                }
            showHome(env.viewModel)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.todaysRepeats.value.morning.singleOrNull()?.status == RepeatDayStatus.OPEN
            }
            env.viewModel.markRepeatDone(id, today)
            await {
                env.viewModel.todaysRepeats.value.morning.single().status == RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.message == "Checked off"
            }
        } finally {
            env.close()
        }
    }

    @Test
    fun tappingAMissedRowMarksItDoneAndUndoRestoresTheMiss() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.useCases.createRepeat("Water plants", RepeatBand.NOON, 1, today)
                env.useCases.createRepeat("Take meds", RepeatBand.MORNING, 1, today)
            }
            showHome(env.viewModel)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.todaysRepeats.value.morning.single().status == RepeatDayStatus.MISSED
            }
            compose.onNodeWithText("1 of 2 left").assertExists()
            compose.onNodeWithText("Missed at morning · muted until end of day").assertExists()
            compose
                .onNodeWithContentDescription("Take meds. Missed at morning · muted until end of day. Check off.")
                .assertExists()
            tapDescription("Take meds. Missed at morning · muted until end of day. Check off.")
            await {
                val rows = orderedRepeatRows(env.viewModel.todaysRepeats.value)
                rows.map { it.repeat.title } == listOf("Water plants", "Take meds") &&
                    rows.last().status == RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.message == "Checked off"
            }
            assertEquals("1 of 2 left", repeatsLeftLabel(orderedRepeatRows(env.viewModel.todaysRepeats.value)))
            compose.onNodeWithContentDescription("Take meds. Morning. Done.").assertExists()
            compose.onNodeWithText("Missed at morning · muted until end of day").assertDoesNotExist()
            val undo = env.viewModel.uiState.value.undo
            assertNotNull(undo)
            env.viewModel.undo(undo!!.id)
            await {
                env.viewModel.todaysRepeats.value.morning.single { it.repeat.title == "Take meds" }.status ==
                    RepeatDayStatus.MISSED &&
                    env.viewModel.uiState.value.undo == null
            }
            compose
                .onNodeWithContentDescription("Take meds. Missed at morning · muted until end of day. Check off.")
                .assertExists()
        } finally {
            env.close()
        }
    }

    private fun showHome(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            FocusStackTheme {
                HomeScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenFocus = {},
                    onOpenInbox = {},
                    onOpenLater = {},
                    onOpenScheduled = {},
                    onOpenDone = {},
                )
            }
        }
    }

    private fun tapDescription(description: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithContentDescription(description)
        node.performScrollTo()
        node.assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun titleOrder(vararg titles: String): List<String> =
        titles.sortedBy {
            compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private suspend fun idOf(
        env: Env,
        title: String,
    ): String = env.useCases.observeRepeats().first().single { it.title == title }.id

    private fun env(hour: Int): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "repeat-check-${UUID.randomUUID()}"),
                    clock = clockAt(hour),
                ),
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
        return Env(useCases, database, viewModel, store)
    }

    private data class Env(
        val useCases: FocusUseCases,
        val database: FocusStackDatabase,
        val viewModel: FocusStackViewModel,
        val store: ViewModelStore,
    ) {
        fun close() {
            store.clear()
            database.close()
        }
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)

        fun clockAt(hour: Int): AppClock {
            val millis = DAY.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return AppClock { millis }
        }
    }
}
