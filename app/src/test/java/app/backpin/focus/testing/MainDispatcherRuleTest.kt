// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun runTestSharesTheRuleScheduler() =
        runTest {
            assertSame(mainDispatcherRule.scheduler, testScheduler)
            assertTrue(Dispatchers.Main is TestDispatcher)
            assertSame(mainDispatcherRule.dispatcher.scheduler, testScheduler)
            var ran = false
            val job = launch(Dispatchers.Main) { ran = true }
            assertFalse(ran)
            testScheduler.runCurrent()
            job.join()
            assertTrue(ran)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleResetTest {
    @Test
    fun resetsMainAfterTheStatement() {
        val rule = MainDispatcherRule()
        val description = Description.createTestDescription(javaClass, "resetsMain")
        rule
            .apply(
                object : Statement() {
                    override fun evaluate() {
                        assertTrue(Dispatchers.Main is TestDispatcher)
                    }
                },
                description,
            ).evaluate()
        assertFalse(Dispatchers.Main is TestDispatcher)
    }
}
