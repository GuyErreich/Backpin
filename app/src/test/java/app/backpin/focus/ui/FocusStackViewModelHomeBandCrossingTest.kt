// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.millisUntilNextBandBoundary
import app.backpin.focus.testing.DEFAULT_TEST_ZONE
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.TestFocusFixture
import app.backpin.focus.testing.awaitState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelHomeBandCrossingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
        val waitToNoon =
            millisUntilNextBandBoundary(
                beforeNoon.toEpochMilli(),
                BandTimes.DEFAULT,
                clock.zone,
            )
        val serviceScope = TestScope(mainDispatcherRule.dispatcher)
        TestFocusFixture
            .open(
                clock = clock,
                repeatsContext = ApplicationProvider.getApplicationContext(),
                repeatsScope = serviceScope,
            ).use { fixture ->
                try {
                    fixture.repository.preferences.first()
                    fixture.repeats.observeBandTimes().first()
                    flushMain()
                    fixture.withViewModel { viewModel ->
                        val emissions = CopyOnWriteArrayList<HomeBands>()
                        val collect =
                            launch(UnconfinedTestDispatcher()) {
                                viewModel.homeBands.collect { emissions += it }
                            }
                        try {
                            flushMain()
                            awaitBands(viewModel) { true }
                            fixture.useCases.captureIntoToday("One")
                            clock.advanceBy(Duration.ofMillis(1))
                            fixture.useCases.captureIntoToday("Two")
                            clock.advanceBy(Duration.ofMillis(1))
                            fixture.useCases.captureIntoToday("Three")
                            clock.set(beforeNoon)
                            flushMain()
                            val before =
                                awaitBands(viewModel) { state ->
                                    state.openBand == HomeBand.MORNING &&
                                        state.morning.tasks.map { it.title } ==
                                        listOf("One", "Two", "Three")
                                }
                            assertEquals(HomeBand.MORNING, before.openBand)
                            val settled = emissions.size
                            clock.set(noon)
                            mainDispatcherRule.scheduler.advanceTimeBy(waitToNoon)
                            flushMain()
                            val after =
                                awaitBands(viewModel) { state ->
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
                            mainDispatcherRule.scheduler.advanceTimeBy(250)
                            flushMain()
                            assertEquals(describeEmissions(emissions), afterCross, emissions.size)
                        } finally {
                            collect.cancel()
                        }
                    }
                } finally {
                    serviceScope.cancel()
                }
            }
    }

    private fun flushMain() {
        mainDispatcherRule.scheduler.advanceUntilIdle()
    }

    private suspend fun awaitBands(
        viewModel: FocusStackViewModel,
        predicate: (HomeBands) -> Boolean,
    ): HomeBands = coroutineScope {
        val waiter = async { viewModel.homeBands.awaitState(predicate = predicate) }
        while (!waiter.isCompleted) {
            flushMain()
            yield()
        }
        waiter.await()
    }

    private fun atLocal(hour: Int, minute: Int): Instant = DAY.atTime(hour, minute)
        .atZone(DEFAULT_TEST_ZONE)
        .toInstant()

    private fun describeEmissions(emissions: List<HomeBands>): String = emissions
        .mapIndexed { index, bands ->
            "$index:${bands.openBand}/m${bands.morning.tasks.size}/n${bands.noon.tasks.size}"
        }.joinToString(separator = ",")

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
    }
}
