// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.os.Looper
import androidx.lifecycle.ViewModelStore
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Band-boundary crossing used to poll with [yield] and a real 5s timeout on
 * [ZoneId.systemDefault]. Pin [FakeAppClock] and its zone, drive the
 * RepeatsService boundary loop on a [TestCoroutineScheduler], and await the
 * emission list instead of a shared Main dispatcher spin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelHomeBandCrossingTest {
    @Test
    fun crossingABoundMovesOpenBandAndTheMainThreeWithNoExtraEmissions() {
        val previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TEST_ZONE.id))
        try {
            runCrossing()
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    private fun runCrossing() = runBlocking {
        val noon = atLocal(12, 0)
        val beforeNoon = noon.minusMillis(500)
        val clock = FakeAppClock(beforeNoon, DEFAULT_TEST_ZONE)
        val serviceScheduler = TestCoroutineScheduler()
        val serviceScope = TestScope(StandardTestDispatcher(serviceScheduler))
        TestFocusFixture
            .open(
                clock = clock,
                repeatsContext = ApplicationProvider.getApplicationContext(),
                repeatsScope = serviceScope,
            ).use { fixture ->
                fixture.repository.preferences.first()
                fixture.repeats.observeBandTimes().first()
                serviceScheduler.runCurrent()
                val store = ViewModelStore()
                try {
                    val viewModel = fixture.viewModel()
                    val emissions = CopyOnWriteArrayList<HomeBands>()
                    val collectJob = SupervisorJob()
                    val collector =
                        launch(collectJob + UnconfinedTestDispatcher()) {
                            viewModel.homeBands.collect { emissions += it }
                        }
                    try {
                        awaitBands(emissions, serviceScheduler) { true }
                        captureToday(fixture, serviceScheduler, "One")
                        clock.advanceBy(java.time.Duration.ofMillis(1))
                        captureToday(fixture, serviceScheduler, "Two")
                        clock.advanceBy(java.time.Duration.ofMillis(1))
                        captureToday(fixture, serviceScheduler, "Three")
                        clock.set(beforeNoon)
                        drain(serviceScheduler)
                        val before =
                            awaitBands(emissions, serviceScheduler) { state ->
                                state.openBand == HomeBand.MORNING &&
                                    state.morning.tasks.map { it.title } ==
                                    listOf("One", "Two", "Three")
                            }
                        assertEquals(HomeBand.MORNING, before.openBand)
                        drain(serviceScheduler)
                        val settled = emissions.size
                        clock.set(noon)
                        val waitToNoon =
                            millisUntilNextBandBoundary(
                                beforeNoon.toEpochMilli(),
                                BandTimes.DEFAULT,
                                clock.zone,
                            )
                        serviceScheduler.advanceTimeBy(waitToNoon)
                        drain(serviceScheduler)
                        val after =
                            awaitBands(emissions, serviceScheduler) { state ->
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
                        serviceScheduler.advanceTimeBy(250)
                        drain(serviceScheduler)
                        assertEquals(describeEmissions(emissions), afterCross, emissions.size)
                    } finally {
                        collector.cancel()
                        collectJob.cancel()
                    }
                } finally {
                    serviceScope.cancel()
                    drain(serviceScheduler)
                    store.clear()
                    drain(serviceScheduler)
                }
            }
    }

    private suspend fun captureToday(
        fixture: TestFocusFixture,
        serviceScheduler: TestCoroutineScheduler,
        title: String,
    ) {
        val outcome = fixture.useCases.captureIntoToday(title)
        assertEquals(PromoteOutcome.Promoted, outcome)
        drain(serviceScheduler)
    }

    private suspend fun awaitBands(
        emissions: List<HomeBands>,
        serviceScheduler: TestCoroutineScheduler,
        predicate: (HomeBands) -> Boolean,
    ): HomeBands {
        withTimeout(5_000) {
            while (emissions.none(predicate)) {
                drain(serviceScheduler)
                yield()
            }
        }
        drain(serviceScheduler)
        return emissions.last(predicate)
    }

    private fun drain(serviceScheduler: TestCoroutineScheduler) {
        shadowOf(Looper.getMainLooper()).idle()
        serviceScheduler.runCurrent()
    }

    private fun atLocal(hour: Int, minute: Int): Instant =
        DAY.atTime(hour, minute).atZone(DEFAULT_TEST_ZONE).toInstant()

    private fun describeEmissions(emissions: List<HomeBands>): String =
        emissions
            .mapIndexed { index, bands ->
                "$index:${bands.openBand}/m${bands.morning.tasks.size}/n${bands.noon.tasks.size}"
            }.joinToString(separator = ",")

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
    }
}
