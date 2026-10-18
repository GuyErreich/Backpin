// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs [Dispatchers.Main] as a [StandardTestDispatcher] on one
 * [TestCoroutineScheduler]. [kotlinx.coroutines.test.runTest] without an
 * explicit dispatcher picks that scheduler from Main, so virtual time stays
 * shared. No real clock. No sleeps.
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
}
