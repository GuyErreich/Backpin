// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
    fun runTestSharesTheRuleScheduler() = mainDispatcherRule.runTest {
        assertSame(mainDispatcherRule.scheduler, testScheduler)
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
    fun setsMainForEachApplyCycle() {
        val rule = MainDispatcherRule()
        val description = Description.createTestDescription(javaClass, "resetsMain")
        val cycles = listOf("first", "second")
        for (name in cycles) {
            var ran = false
            rule
                .apply(
                    object : Statement() {
                        override fun evaluate() {
                            val job = CoroutineScope(Dispatchers.Main).launch { ran = true }
                            rule.scheduler.runCurrent()
                            assertTrue(name, ran)
                            job.cancel()
                        }
                    },
                    description,
                ).evaluate()
        }
    }
}
