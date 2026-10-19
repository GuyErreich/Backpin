// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.runOffTestThread
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatCaptureFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createCaptureDoesNotOfferRepeat() {
        val env = env()
        try {
            compose.setContent {
                FocusStackTheme {
                    FocusApp(env.viewModel)
                }
            }
            await { env.viewModel.uiState.value.ready }
            env.viewModel.openCapture()
            await { env.viewModel.uiState.value.captureOpen }
            compose.onNode(hasSetTextAction()).performTextInput("Take meds")
            compose.onNodeWithText("Repeat").assertDoesNotExist()
            compose.onNodeWithText("A small repeating thing.").assertDoesNotExist()
        } finally {
            env.close()
        }
    }

    @Test
    fun taskEditSheetFromFocusAppDoesNotOfferRepeat() {
        val env = env()
        try {
            val id =
                runOffTestThread {
                    env.useCases.capture("Water plants")
                    env.useCases.observeSnapshot().first().tasks.single { it.title == "Water plants" }.id
                }
            compose.setContent {
                FocusStackTheme {
                    FocusApp(env.viewModel)
                }
            }
            await { env.viewModel.uiState.value.ready }
            env.viewModel.openEdit(id)
            await { env.viewModel.uiState.value.editing?.id == id }
            compose.onNodeWithText("Edit task").assertExists()
            compose.onNodeWithText("Repeat").assertDoesNotExist()
            compose.onNodeWithText("A small repeating thing.").assertDoesNotExist()
        } finally {
            env.close()
        }
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun env(): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = FakeAppClock()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "repeat-capture-${UUID.randomUUID()}"),
                clock = clock,
            )
        val useCases = FocusUseCases(repository)
        val repeats = RepeatsService(repository, clock = clock)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases, repeats) as T
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
}
