// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Method
import java.util.UUID

/**
 * Expected pending until #29 (`HomeLayout`) is merged in and Home reads
 * that flag. HomeScreen.grouped is hardcoded true for now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeLayoutBandsHeaderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<CloseableEnv>()

    @After
    fun tearDown() {
        closeOpened()
    }

    @Test
    fun bandsLayoutShowsMorningNoonNightHeadersAndFlatDoesNot() {
        val layoutClass =
            runCatching { Class.forName("app.backpin.focus.domain.HomeLayout") }.getOrNull()
        Assume.assumeTrue(
            "Expected pending: HomeLayout from #29 is not on this base",
            layoutClass != null,
        )
        val setter =
            PreferencesDataStore::class.java.methods.firstOrNull { method ->
                method.name == "setHomeLayout"
            }
        Assume.assumeTrue(
            "Pending PreferencesDataStore.setHomeLayout from #29",
            setter != null,
        )
        val bands = layoutValue(layoutClass!!, "BANDS")
        val flat = layoutValue(layoutClass, "FLAT")

        showHome(layout = bands, setter = setter!!)
        compose.onNodeWithText("Morning").assertIsDisplayed()
        compose.onNodeWithText("Noon").assertIsDisplayed()
        compose.onNodeWithText("Night").assertIsDisplayed()

        showHome(layout = flat, setter = setter)
        compose.onNodeWithText("Morning").assertDoesNotExist()
        compose.onNodeWithText("Noon").assertDoesNotExist()
        compose.onNodeWithText("Night").assertDoesNotExist()
    }

    private fun showHome(
        layout: Any,
        setter: Method,
    ) {
        closeOpened()
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val preferences = PreferencesDataStore(context, "home-layout-${UUID.randomUUID()}")
        runBlocking { setter.invoke(preferences, layout) }
        val clock = AppClock { System.currentTimeMillis() }
        val repository = FocusRepositoryImpl(database, preferences, clock)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(
                            FocusUseCases(repository),
                            RepeatsService(repository),
                            clock,
                        ) as T
                    }
                },
            )[FocusStackViewModel::class.java]
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
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.ready }
        opened.add(CloseableEnv(store, database))
    }

    private fun layoutValue(
        layoutClass: Class<*>,
        name: String,
    ): Any {
        val constants = layoutClass.enumConstants ?: error("HomeLayout has no constants")
        return constants.first { constant -> constant.toString() == name }
    }

    private fun closeOpened() {
        opened.forEach { env ->
            env.store.clear()
            env.database.close()
        }
        opened.clear()
    }

    private class CloseableEnv(
        val store: ViewModelStore,
        val database: FocusStackDatabase,
    )
}
