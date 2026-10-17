// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.app.Instrumentation
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Home stays open across a local-day change. Midnight and the MainActivity
 * onStop → onStart path ([app.backpin.focus.FocusStackApp.onForeground] →
 * [RepeatsService.refreshToday]) must update rows without a toast, snackbar,
 * or motion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatHomeDayRefreshTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun midnightRolloverOnOpenHomeIsQuiet() {
        val env = env(hour = 23, minute = 59)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
                env.repeats.create("Water plants", RepeatBand.NOON, 2, today)
            }
            showApp(env)
            await {
                env.viewModel.uiState.value.ready &&
                    titles(env) == setOf("Take meds", "Water plants") &&
                    env.viewModel.repeatRows.value.rows.all { it.status == RepeatDayStatus.MISSED }
            }
            val rowsBefore = env.viewModel.repeatRows.value
            compose.mainClock.autoAdvance = false

            env.advanceClockPastMidnight(at(hour = 0, minute = 1, day = DAY.plusDays(1)))
            idleUi()

            await {
                titles(env) == setOf("Take meds") &&
                    env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.OPEN
            }
            assertTrue(rowsBefore.rows.any { it.repeat.title == "Water plants" })
            assertFalse(env.viewModel.repeatRows.value.rows.any { it.repeat.title == "Water plants" })
            compose.onNodeWithText("Water plants").assertDoesNotExist()
            compose.onNodeWithText("Take meds", useUnmergedTree = true).assertExists()
            assertQuietHome(env)
            assertNoAnimationAfterRollover(env)
        } finally {
            env.close()
        }
    }

    @Test
    fun backgroundedDayChangeThenOnStartDropsYesterdaysMissedAndStaysQuiet() {
        val env = env(hour = 23, minute = 59)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
                env.repeats.create("Water plants", RepeatBand.NOON, 2, today)
            }
            showApp(env)
            await {
                env.viewModel.uiState.value.ready &&
                    titles(env) == setOf("Take meds", "Water plants") &&
                    env.viewModel.repeatRows.value.rows.all { it.status == RepeatDayStatus.MISSED }
            }
            val activity = compose.activity
            val emissions = rowEmissions(env)
            val beforeBackground = emissions.size
            stopActivity(activity)
            env.clock.now = at(hour = 0, minute = 1, day = DAY.plusDays(1))
            compose.mainClock.autoAdvance = false
            // MainActivity.onStart → FocusStackApp.onForeground → refreshToday()
            env.repeats.refreshToday()
            startActivity(activity)
            idleUi()

            await {
                titles(env) == setOf("Take meds") &&
                    env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.OPEN
            }
            assertTrue(emissions.size > beforeBackground)
            assertFalse(env.viewModel.repeatRows.value.rows.any { it.repeat.title == "Water plants" })
            compose.onNodeWithText("Water plants").assertDoesNotExist()
            assertQuietHome(env)
            assertNoAnimationAfterRollover(env)

            val afterDayChange = emissions.size
            stopActivity(activity)
            env.repeats.refreshToday()
            startActivity(activity)
            idleUi()
            yieldMain()
            assertEquals(
                "A same-day onStart must not emit again",
                afterDayChange,
                emissions.size,
            )
            assertEquals(listOf("Take meds"), env.viewModel.repeatRows.value.rows.map { it.repeat.title })
            assertQuietHome(env)
            emissions.close()
        } finally {
            env.close()
        }
    }

    private fun showApp(env: Env) {
        compose.setContent {
            FocusStackTheme {
                FocusApp(env.viewModel)
            }
        }
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

    private fun idleUi() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun yieldMain() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun titles(env: Env): Set<String> = env.viewModel.repeatRows.value.rows.map { it.repeat.title }.toSet()

    private fun assertQuietHome(env: Env) {
        assertNull(ShadowToast.getLatestToast())
        assertFalse(viewHasSnackbar(compose.activity.window.decorView))
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithText("Checked off").assertDoesNotExist()
        assertNull(env.viewModel.uiState.value.undo)
    }

    private fun assertNoAnimationAfterRollover(env: Env) {
        val titlesNow = env.viewModel.repeatRows.value.rows.map { it.repeat.title }
        compose.mainClock.advanceTimeBy(2_000)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(titlesNow, env.viewModel.repeatRows.value.rows.map { it.repeat.title })
        assertNull(ShadowToast.getLatestToast())
        assertFalse(viewHasSnackbar(compose.activity.window.decorView))
        assertNull(env.viewModel.uiState.value.undo)
    }

    private fun env(
        hour: Int,
        minute: Int = 0,
        day: LocalDate = DAY,
    ): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val clock = SettableClock(at(hour, minute, day))
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher)
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "repeat-home-day-${UUID.randomUUID()}"),
                clock = clock,
            )
        val useCases = FocusUseCases(repository)
        val repeats =
            RepeatsService(
                repository = repository,
                clock = clock,
                context = context,
                zone = { ZONE },
                scope = scope,
            )
        scheduler.runCurrent()
        val store = ViewModelStore()
        return Env(
            clock = clock,
            repeats = repeats,
            database = database,
            useCases = useCases,
            store = store,
            scheduler = scheduler,
            job = job,
            viewModel = createViewModel(store, useCases, repeats),
        )
    }

    private fun rowEmissions(env: Env): EmissionProbe {
        val values = mutableListOf<RepeatRowsState>()
        val job =
            CoroutineScope(SupervisorJob()).launch {
                env.repeats.observeRows().collect { values += it }
            }
        runBlocking {
            withTimeout(5_000) {
                while (values.isEmpty()) {
                    yield()
                }
            }
        }
        return EmissionProbe(values, job)
    }

    private data class Env(
        val clock: SettableClock,
        val repeats: RepeatsService,
        val database: FocusStackDatabase,
        val useCases: FocusUseCases,
        val store: ViewModelStore,
        val scheduler: TestCoroutineScheduler,
        val job: Job,
        val viewModel: FocusStackViewModel,
    ) {
        fun advanceClockPastMidnight(nextNow: Long) {
            val wait = millisUntilNextLocalMidnight(clock.now, ZONE)
            clock.now = nextNow
            scheduler.advanceTimeBy(wait)
            scheduler.runCurrent()
        }

        fun close() {
            job.cancel()
            store.clear()
            database.close()
        }
    }

    private class EmissionProbe(
        private val values: MutableList<RepeatRowsState>,
        private val job: kotlinx.coroutines.Job,
    ) {
        val size: Int
            get() = values.size

        fun close() {
            job.cancel()
        }
    }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

        fun at(
            hour: Int,
            minute: Int,
            day: LocalDate = DAY,
        ): Long = day.atTime(LocalTime.of(hour, minute)).atZone(ZONE).toInstant().toEpochMilli()

        fun millisUntilNextLocalMidnight(
            nowMillis: Long,
            zone: ZoneId,
        ): Long {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return (nextMidnight - nowMillis).coerceAtLeast(1L)
        }

        fun createViewModel(
            store: ViewModelStore,
            useCases: FocusUseCases,
            repeats: RepeatsService,
        ): FocusStackViewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return FocusStackViewModel(useCases, repeats) as T
                }
            },
        )[FocusStackViewModel::class.java]

        fun viewHasSnackbar(root: View): Boolean {
            if (root.javaClass.name.contains("Snackbar", ignoreCase = true)) return true
            if (root is ViewGroup) {
                for (index in 0 until root.childCount) {
                    if (viewHasSnackbar(root.getChildAt(index))) return true
                }
            }
            return false
        }

        fun stopActivity(activity: ComponentActivity) {
            val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.callActivityOnPause(activity)
            instrumentation.callActivityOnStop(activity)
        }

        fun startActivity(activity: ComponentActivity) {
            val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.callActivityOnStart(activity)
            instrumentation.callActivityOnResume(activity)
        }
    }
}
