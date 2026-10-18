// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest as coroutinesRunTest

/**
 * Installs [Dispatchers.Main] as a [StandardTestDispatcher] on one
 * [TestCoroutineScheduler]. [runTest] uses that same dispatcher so virtual
 * time stays shared. No real clock. No sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    val dispatcher: TestDispatcher = StandardTestDispatcher(scheduler),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }

    /**
     * [kotlinx.coroutines.test.runTest] bound to [dispatcher], so the body
     * and Main share [scheduler].
     */
    fun runTest(
        timeout: Duration = 60.seconds,
        testBody: suspend TestScope.() -> Unit,
    ) = coroutinesRunTest(
        context = dispatcher,
        timeout = timeout,
        testBody = testBody,
    )
}
