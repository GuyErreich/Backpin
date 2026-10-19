// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.awaitState
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelFullTodayStartTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startOnAFullTodayKeepsTheCaptureInInboxAndDoesNotSwitch() = runBlocking {
        val clock = FakeAppClock()
        useTestFocus(clock = clock) { fixture ->
            val today = fixture.seedFullToday(listOf("Today 0", "Today 1", "Today 2"))
            fixture.useCases.start(today[0].id)
            fixture.withViewModel { viewModel ->
                val collect = launch { viewModel.uiState.collect {} }
                try {
                    flushMain()
                    awaitUi(viewModel) { state ->
                        state.todaySlots.count { it.task != null } == 3 &&
                            state.now?.taskId == today[0].id
                    }
                    viewModel.placeAndStart("Call Dana")
                    flushMain()
                    val waiting =
                        awaitUi(viewModel) { state ->
                            state.swapIncoming?.title == "Call Dana" && state.swapChoices.size == 3
                        }
                    val snapshot = fixture.useCases.observeSnapshot().first()
                    val captured = snapshot.tasks.single { it.title == "Call Dana" }
                    assertEquals(TaskBucket.INBOX, captured.bucket)
                    assertEquals(captured.id, waiting.swapIncoming?.id)
                    assertEquals(
                        today[0].id,
                        snapshot.frames.single { it.state == FrameState.ACTIVE }.taskId,
                    )
                    assertEquals(3, waiting.swapChoices.size)
                } finally {
                    collect.cancel()
                }
            }
        }
    }

    private fun flushMain() {
        mainDispatcherRule.scheduler.advanceUntilIdle()
    }

    private suspend fun awaitUi(
        viewModel: FocusStackViewModel,
        predicate: (FocusUiState) -> Boolean,
    ): FocusUiState = coroutineScope {
        val waiter = async { viewModel.uiState.awaitState(predicate = predicate) }
        while (!waiter.isCompleted) {
            flushMain()
            yield()
        }
        waiter.await()
    }
}
