// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import app.backpin.focus.testing.InMemoryFocusRule
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.awaitState
import app.backpin.focus.testing.expectedStartRedirectInterruptionTitle
import app.backpin.focus.testing.expectedStartRedirectParentTitle
import app.backpin.focus.testing.nestInterruptChain
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartRedirectViewModelTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule(dispatcher = UnconfinedTestDispatcher())

    @get:Rule(order = 1)
    val focus = InMemoryFocusRule()

    @Test
    fun startOnAParentExposesTheDeepestChildThenDismissClearsIt() = runBlocking {
        val titles = listOf("Write the brief", "Call the school", "Ping the vendor")
        useTestFocus { fixture ->
            val chain = fixture.nestInterruptChain(titles)
            fixture.repository.pauseActive()
            fixture.withViewModel { viewModel ->
                viewModel.start(chain.first().id)
                val redirected =
                    viewModel.uiState.awaitState { state ->
                        state.startRedirect != null &&
                            state.now?.taskId == chain.last().id
                    }
                val redirect = redirected.startRedirect!!
                assertEquals(chain.last().id, redirect.interruptionId)
                assertEquals(
                    expectedStartRedirectInterruptionTitle(titles),
                    redirect.interruptionTitle,
                )
                assertEquals(chain[chain.lastIndex - 1].id, redirect.parentId)
                assertEquals(expectedStartRedirectParentTitle(titles), redirect.parentTitle)
                viewModel.dismissStartRedirect()
                val cleared =
                    viewModel.uiState.awaitState { state ->
                        state.startRedirect == null && state.now?.taskId == chain.last().id
                    }
                assertNull(cleared.startRedirect)
                assertEquals(chain.last().id, cleared.now?.taskId)
            }
        }
    }

    @Test
    fun startWithoutOpenChildrenDoesNotOfferARedirect() = runBlocking {
        useTestFocus { fixture ->
            val parent = fixture.nestInterruptChain(listOf("Write the brief")).single()
            fixture.repository.pauseActive()
            fixture.withViewModel { viewModel ->
                viewModel.start(parent.id)
                val started =
                    viewModel.uiState.awaitState { state ->
                        state.now?.taskId == parent.id && state.startRedirect == null
                    }
                assertNull(started.startRedirect)
                assertEquals(parent.id, started.now?.taskId)
            }
        }
    }
}
