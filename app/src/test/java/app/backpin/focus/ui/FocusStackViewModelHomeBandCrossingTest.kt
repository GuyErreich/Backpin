// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.millisUntilNextBandBoundary
import app.backpin.focus.testing.DEFAULT_TEST_ZONE
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.TestFocusFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Band-boundary crossing used to poll with [yield] and a real 5s timeout on
 * [ZoneId.systemDefault]. Pin [FakeAppClock] and its zone, put RepeatsService
 * and [Dispatchers.Main] on one [TestCoroutineScheduler], and await the
 * emission list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelHomeBandCrossingTest {
    @Test
    fun crossingABoundMovesOpenBandAndTheMainThreeWithNoExtraEmissions() {
        val previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TEST_ZONE.id))
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        try {
            runCrossing(scheduler)
        } finally {
            Dispatchers.resetMain()
            TimeZone.setDefault(previousZone)
        }
    }

    private fun runCrossing(scheduler: TestCoroutineScheduler) = runBlocking {
        val noon = atLocal(12, 0)
        val beforeNoon = noon.minusMillis(500)
        val clock = FakeAppClock(beforeNoon, DEFAULT_TEST_ZONE)
        val serviceScope = TestScope(StandardTestDispatcher(scheduler))
        TestFocusFixture
            .open(
                clock = clock,
                repeatsContext = ApplicationProvider.getApplicationContext(),
                repeatsScope = serviceScope,
            ).use { fixture ->
                fixture.repository.preferences.first()
                val times = fixture.repeats.observeBandTimes().first()
                scheduler.runCurrent()
                fixture.withViewModel { viewModel ->
                    val emissions = CopyOnWriteArrayList<HomeBands>()
                    val collectJob = SupervisorJob()
                    val collector =
                        launch(collectJob + UnconfinedTestDispatcher()) {
                            viewModel.homeBands.collect { emissions += it }
                        }
                    try {
                        awaitBands(emissions, scheduler) { true }
                        captureToday(fixture, scheduler, "One")
                        clock.advanceBy(Duration.ofMillis(1))
                        captureToday(fixture, scheduler, "Two")
                        clock.advanceBy(Duration.ofMillis(1))
                        captureToday(fixture, scheduler, "Three")
                        clock.set(beforeNoon)
                        drain(scheduler)
                        val before =
                            awaitBands(emissions, scheduler) { state ->
                                state.openBand == HomeBand.MORNING &&
                                    state.morning.tasks.map { it.title } ==
                                    listOf("One", "Two", "Three")
                            }
                        assertEquals(HomeBand.MORNING, before.openBand)
                        drain(scheduler)
                        val settled = emissions.size
                        val waitToNoon =
                            millisUntilNextBandBoundary(
                                beforeNoon.toEpochMilli(),
                                times,
                                clock.zone,
                            )
                        clock.set(noon)
                        scheduler.advanceTimeBy(waitToNoon)
                        drain(scheduler)
                        val after =
                            awaitBands(emissions, scheduler) { state ->
                                state.openBand == HomeBand.NOON &&
                                    state.noon.tasks.map { it.title } ==
                                    listOf("One", "Two", "Three")
                            }
                        assertEquals(HomeBand.NOON, after.openBand)
                        assertTrue(after.morning.tasks.isEmpty())
                        assertEquals(BandPhase.PAST, after.morning.phase)
                        assertEquals(BandPhase.OPEN, after.noon.phase)
                        val afterCross = emissions.size
                        assertTrue(afterCross > settled)
                        scheduler.advanceTimeBy(250)
                        drain(scheduler)
                        assertEquals(describeEmissions(emissions), afterCross, emissions.size)
                    } finally {
                        collector.cancel()
                        collectJob.cancel()
                    }
                }
                serviceScope.cancel()
                drain(scheduler)
            }
    }

    private suspend fun captureToday(
        fixture: TestFocusFixture,
        scheduler: TestCoroutineScheduler,
        title: String,
    ) {
        val outcome = fixture.useCases.captureIntoToday(title)
        assertEquals(PromoteOutcome.Promoted, outcome)
        drain(scheduler)
    }

    private suspend fun awaitBands(
        emissions: List<HomeBands>,
        scheduler: TestCoroutineScheduler,
        predicate: (HomeBands) -> Boolean,
    ): HomeBands {
        withTimeout(5_000) {
            while (emissions.none(predicate)) {
                drain(scheduler)
                yield()
            }
        }
        drain(scheduler)
        return emissions.last(predicate)
    }

    private fun drain(scheduler: TestCoroutineScheduler) {
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun atLocal(
        hour: Int,
        minute: Int,
    ): Instant = DAY.atTime(hour, minute).atZone(DEFAULT_TEST_ZONE).toInstant()

    private fun describeEmissions(emissions: List<HomeBands>): String = emissions
        .mapIndexed { index, bands ->
            "$index:${bands.openBand}/m${bands.morning.tasks.size}/n${bands.noon.tasks.size}"
        }.joinToString(separator = ",")

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
    }
}
