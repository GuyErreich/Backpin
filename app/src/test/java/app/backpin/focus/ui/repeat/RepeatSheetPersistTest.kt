// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Spec 1 through the real [FocusStackViewModel] + [RepeatsService] path.
 * Sheet callback tests live in [RepeatSheetTest]; these assert Room writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatSheetPersistTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backAfterEditingAFieldPersistsTheChange() {
        val env = env()
        try {
            showSheet(env.viewModel)
            env.viewModel.openRepeatCreate("Take meds")
            await { env.viewModel.editingRepeat.value != null }
            compose.onNode(hasSetTextAction() and hasText("Take meds")).performTextReplacement("Take vitamins")
            tap("Noon")
            compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
            await {
                env.viewModel.editingRepeat.value == null &&
                    stored(env).singleOrNull()?.title == "Take vitamins"
            }
            val stored = stored(env).single()
            assertEquals("Take vitamins", stored.title)
            assertEquals(RepeatBand.NOON, stored.band)
            assertEquals(1, stored.everyNDays)
        } finally {
            env.close()
        }
    }

    @Test
    fun backWithEmptyTitlePersistsNothingAndDismissesTheSheet() {
        val env = env()
        try {
            showSheet(env.viewModel)
            env.viewModel.openRepeatCreate()
            await { env.viewModel.editingRepeat.value != null }
            compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
            await { env.viewModel.editingRepeat.value == null }
            assertTrue(stored(env).isEmpty())
        } finally {
            env.close()
        }
    }

    @Test
    fun backAfterClearingTheTitlePersistsNothing() {
        val env = env()
        try {
            showSheet(env.viewModel)
            env.viewModel.openRepeatCreate("Take meds")
            await { env.viewModel.editingRepeat.value?.title == "Take meds" }
            compose.onNode(hasSetTextAction() and hasText("Take meds")).performTextReplacement("  ")
            compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
            await { env.viewModel.editingRepeat.value == null }
            assertTrue("A blank title must not create a repeat", stored(env).isEmpty())
        } finally {
            env.close()
        }
    }

    @Test
    fun backOnEditWithEmptyTitleLeavesTheStoredRepeatAlone() {
        val env = env()
        try {
            val id =
                runOffTestThread {
                    env.repeats.create("Water plants", RepeatBand.NOON, 2, DAY.toEpochDay())!!
                }
            showSheet(env.viewModel)
            val existing = runOffTestThread { env.repeats.observeRepeats().first().single { it.id == id } }
            env.viewModel.openRepeatEdit(existing)
            await { env.viewModel.editingRepeat.value?.id == id }
            compose.onNode(hasSetTextAction() and hasText("Water plants")).performTextReplacement("")
            compose.onNodeWithContentDescription("Back").performTouchInput { click(center) }
            await { env.viewModel.editingRepeat.value == null }
            val stored = stored(env).single()
            assertEquals("Water plants", stored.title)
            assertEquals(RepeatBand.NOON, stored.band)
            assertEquals(2, stored.everyNDays)
        } finally {
            env.close()
        }
    }

    @Test
    fun doneWithEmptyTitlePersistsNothingAndKeepsTheSheetOpen() {
        val env = env()
        try {
            showSheet(env.viewModel)
            env.viewModel.openRepeatCreate()
            await { env.viewModel.editingRepeat.value != null }
            compose.onNodeWithText("Done").assertIsNotEnabled()
            compose.onNodeWithText("Done").performScrollTo()
            compose.onNodeWithText("Done").performTouchInput { click(center) }
            compose.waitForIdle()
            assertTrue(env.viewModel.editingRepeat.value != null)
            assertTrue(stored(env).isEmpty())
            compose.onNodeWithText("A small repeating thing.").assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun commitRepeatDraftRejectsABlankTitleAndDismissRepeatSheetClosesWithoutWriting() {
        val env = env()
        try {
            showSheet(env.viewModel)
            env.viewModel.openRepeatCreate("Take meds")
            await { env.viewModel.editingRepeat.value != null }
            env.viewModel.commitRepeatDraft(RepeatDraft(title = "   ", band = RepeatBand.NIGHT))
            await {
                env.viewModel.editingRepeat.value != null && stored(env).isEmpty()
            }
            assertTrue(stored(env).isEmpty())
            compose.onNodeWithText("A small repeating thing.").assertExists()
            env.viewModel.dismissRepeatSheet()
            await { env.viewModel.editingRepeat.value == null }
            assertTrue(stored(env).isEmpty())
            compose.onNodeWithText("A small repeating thing.").assertDoesNotExist()
        } finally {
            env.close()
        }
    }

    private fun showSheet(viewModel: FocusStackViewModel) {
        compose.setContent {
            val draft by viewModel.editingRepeat.collectAsState()
            FocusStackTheme {
                draft?.let { open ->
                    RepeatSheet(
                        draft = open,
                        onCommit = viewModel::commitRepeatDraft,
                        onDismiss = viewModel::dismissRepeatSheet,
                        onArchive = viewModel::archiveRepeat,
                        recognizer = SilentPersistRecognizer,
                    )
                }
            }
        }
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.performScrollTo()
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun stored(env: Env) = runOffTestThread { env.repeats.observeRepeats().first() }

    private fun env(): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = clockAt(8)
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "repeat-sheet-persist-${UUID.randomUUID()}"),
                clock = clock,
            )
        val repeats = RepeatsService(repository, clock = clock)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(FocusUseCases(repository), repeats) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        return Env(repeats, database, viewModel, store)
    }

    private data class Env(
        val repeats: RepeatsService,
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

private object SilentPersistRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
