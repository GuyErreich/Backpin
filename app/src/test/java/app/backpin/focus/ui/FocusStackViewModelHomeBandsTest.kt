// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.repeats.RepeatsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelHomeBandsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
    }

    @Test
    fun openTodayTasksPinToOpenBandAndRepeatsKeepTheirs() =
        runBlocking {
            val env = env(at(8, 0))
            try {
                env.captureToday("One")
                env.clock.now += 1
                env.captureToday("Two")
                env.clock.now += 1
                env.captureToday("Three")
                env.repeats.create("Meds", RepeatBand.NIGHT, 1, DAY.toEpochDay())
                val bands =
                    env.awaitBands { state ->
                        state.openBand == HomeBand.MORNING &&
                            state.morning.tasks.map { it.title } == listOf("One", "Two", "Three") &&
                            state.night.repeats.any { it.repeat.title == "Meds" }
                    }
                assertEquals(HomeBand.MORNING, bands.current)
                assertEquals(BandPhase.OPEN, bands.morning.phase)
                assertEquals(BandPhase.FUTURE, bands.noon.phase)
                assertEquals(BandPhase.FUTURE, bands.night.phase)
                assertEquals(BandPhase.ALWAYS_OPEN, bands.anytime.phase)
                assertTrue(bands.anytime.tasks.isEmpty())
                assertTrue(bands.anytime.repeats.isEmpty())
                assertTrue(bands.noon.tasks.isEmpty())
                assertEquals(listOf("Meds"), bands.night.repeats.map { it.repeat.title })
            } finally {
                env.close()
            }
        }

    @Test
    fun afterNightEndTheMainThreePinToMorningNotAnytime() =
        runBlocking {
            val env = env(at(23, 30))
            try {
                env.captureToday("One")
                env.clock.now += 1
                env.captureToday("Two")
                env.clock.now += 1
                env.captureToday("Three")
                val bands =
                    env.awaitBands { state ->
                        state.morning.tasks.size == 3 && state.openBand == HomeBand.MORNING
                    }
                assertEquals(HomeBand.MORNING, bands.openBand)
                assertEquals(listOf("One", "Two", "Three"), bands.morning.tasks.map { it.title })
                assertTrue(bands.anytime.tasks.isEmpty())
                assertEquals(BandPhase.OPEN, bands.morning.phase)
                assertEquals(BandPhase.FUTURE, bands.noon.phase)
                assertEquals(BandPhase.FUTURE, bands.night.phase)
            } finally {
                env.close()
            }
        }

    @Test
    fun leftCountIsOpenPlusMissedAndMatchesTheGroup() =
        runBlocking {
            val env = env(at(8, 0))
            try {
                env.captureToday("Stay")
                env.repeats.create("Plants", RepeatBand.MORNING, 1, DAY.toEpochDay())
                val meds = env.repeats.create("Meds", RepeatBand.MORNING, 1, DAY.toEpochDay())!!
                env.repeats.markDone(meds, DAY.toEpochDay())
                env.repository.markOpenRepeatsMissed(RepeatBand.MORNING, DAY.toEpochDay())
                val bands =
                    env.awaitBands { state ->
                        state.morning.repeats.size == 2 && state.morning.tasks.size == 1
                    }
                val morning = bands.morning
                assertEquals(1, morning.counts.done)
                assertEquals(1, morning.counts.missed)
                assertEquals(2, morning.counts.left)
            } finally {
                env.close()
            }
        }

    @Test
    fun flowRecomputesOnDayRefreshBandTimeTaskAndRowAndNotWhenNothingChanged() =
        runBlocking {
            val env = env(at(8, 0))
            try {
                env.captureToday("Stay")
                env.awaitBands { it.morning.tasks.size == 1 }
                val afterSeed = env.emissions.size

                env.repeats.refreshToday()
                Thread.sleep(50)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(afterSeed, env.emissions.size)

                env.clock.now += 1
                env.captureToday("Next")
                env.awaitBands { it.morning.tasks.size == 2 }

                val afterTask = env.emissions.size
                env.repeats.create("Meds", RepeatBand.NOON, 1, DAY.toEpochDay())
                env.awaitBands { it.noon.repeats.size == 1 }
                assertTrue(env.emissions.size > afterTask)

                val afterRow = env.emissions.size
                env.repeats.setBandTime(BandEdge.NOON, 8 * 60)
                env.awaitBands { it.openBand == HomeBand.NOON }
                assertTrue(env.emissions.size > afterRow)

                env.clock.now = at(6, 0, DAY.plusDays(1))
                val afterDayClock = env.emissions.size
                env.repeats.refreshToday()
                env.awaitBands { bands ->
                    bands.openBand == HomeBand.MORNING && env.emissions.size > afterDayClock
                }
            } finally {
                env.close()
            }
        }

    @Test
    fun crossingABoundMovesOpenBandAndTheMainThreeWithNoExtraEmissions() =
        runBlocking {
            val noon = at(12, 0)
            val env = env(noon - 500, armBoundaryLoop = true)
            try {
                env.captureToday("One")
                env.clock.now += 1
                env.captureToday("Two")
                env.clock.now += 1
                env.captureToday("Three")
                env.clock.now = noon - 500
                val before =
                    env.awaitBands { state ->
                        state.openBand == HomeBand.MORNING &&
                            state.morning.tasks.map { it.title } == listOf("One", "Two", "Three")
                    }
                assertEquals(HomeBand.MORNING, before.openBand)
                val settled = env.emissions.size
                env.clock.now = noon
                val after =
                    env.awaitBands { state ->
                        state.openBand == HomeBand.NOON &&
                            state.noon.tasks.map { it.title } == listOf("One", "Two", "Three")
                    }
                assertEquals(HomeBand.NOON, after.openBand)
                assertTrue(after.morning.tasks.isEmpty())
                assertEquals(BandPhase.PAST, after.morning.phase)
                assertEquals(BandPhase.OPEN, after.noon.phase)
                val afterCross = env.emissions.size
                assertTrue(afterCross > settled)
                Thread.sleep(250)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(afterCross, env.emissions.size)
            } finally {
                env.close()
            }
        }

    private suspend fun env(
        now: Long,
        armBoundaryLoop: Boolean = false,
    ): Env {
        val clock = SettableClock(now)
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "vm-bands-${UUID.randomUUID()}"),
                clock = clock,
            )
        repository.preferences.first()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val repeats =
            RepeatsService(
                repository = repository,
                clock = clock,
                context = if (armBoundaryLoop) context else null,
                zone = { ZONE },
                scope = scope,
            )
        val store = ViewModelStore()
        val useCases = FocusUseCases(repository)
        val viewModel = FocusStackViewModel(useCases, repeats, clock)
        val emissions = mutableListOf<HomeBands>()
        val collector =
            scope.launch {
                viewModel.homeBands.collect { emissions += it }
            }
        waitUntil(5_000) { emissions.isNotEmpty() }
        if (armBoundaryLoop) {
            Thread.sleep(50)
        }
        return Env(
            clock = clock,
            database = database,
            repository = repository,
            repeats = repeats,
            useCases = useCases,
            store = store,
            viewModel = viewModel,
            job = job,
            collector = collector,
            emissions = emissions,
        )
    }

    private class Env(
        val clock: SettableClock,
        val database: FocusStackDatabase,
        val repository: FocusRepositoryImpl,
        val repeats: RepeatsService,
        val useCases: FocusUseCases,
        val store: ViewModelStore,
        val viewModel: FocusStackViewModel,
        val job: Job,
        val collector: Job,
        val emissions: MutableList<HomeBands>,
    ) {
        suspend fun captureToday(title: String) {
            val outcome = useCases.captureIntoToday(title)
            assertEquals(PromoteOutcome.Promoted, outcome)
        }

        suspend fun awaitBands(predicate: (HomeBands) -> Boolean): HomeBands =
            withTimeout(5_000) {
                viewModel.homeBands.first { state ->
                    shadowOf(Looper.getMainLooper()).idle()
                    predicate(state)
                }
            }

        suspend fun close() {
            collector.cancel()
            store.clear()
            job.cancelAndJoin()
            database.close()
        }
    }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneId = ZoneId.systemDefault()

        fun at(
            hour: Int,
            minute: Int,
            day: LocalDate = DAY,
        ): Long = day.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()

        fun waitUntil(
            timeoutMs: Long,
            condition: () -> Boolean,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!condition()) {
                check(System.currentTimeMillis() < deadline) { "timed out after ${timeoutMs}ms" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
    }
}
