// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class AwaitStateTest {
    @Test
    fun returnsMatchingValue() = runTest {
        val states = MutableStateFlow("ready")
        val found = states.awaitState { it == "ready" }
        assertEquals("ready", found)
    }

    @Test
    fun skipsStaleValuesUntilPredicateMatches() = runTest {
        val states = MutableStateFlow(1)
        var found: Int? = null
        val waiter =
            launch {
                found = states.awaitState { it >= 3 }
            }
        testScheduler.runCurrent()
        assertEquals(null, found)
        states.value = 2
        testScheduler.runCurrent()
        assertEquals(null, found)
        states.value = 3
        waiter.join()
        assertEquals(3, found)
    }

    @Test
    fun timesOutWithLastValueSeen() = runTest {
        val states = MutableStateFlow("last-seen")
        try {
            states.awaitState(timeout = 2.seconds) { it == "never" }
            fail("expected awaitState to time out")
        } catch (error: AssertionError) {
            if (error.message == "expected awaitState to time out") throw error
            val message = error.message ?: ""
            assertTrue(message.contains("timed out after 2s"))
            assertTrue(message.contains("Last value: last-seen"))
        }
    }

    @Test
    fun timesOutWithNoneWhenNothingWasEmitted() = runTest {
        try {
            flow<String> { awaitCancellation() }.awaitState(timeout = 1.seconds) { true }
            fail("expected awaitState to fail")
        } catch (error: AssertionError) {
            if (error.message == "expected awaitState to fail") throw error
            val message = error.message ?: ""
            assertTrue(message.contains("timed out after 1s"))
            assertTrue(message.contains("Last value: none"))
        }
    }

    @Test
    fun completedFlowReportsLastValue() = runTest {
        val states =
            flow {
                emit("only")
            }
        try {
            states.awaitState { it == "never" }
            fail("expected awaitState to fail")
        } catch (error: AssertionError) {
            if (error.message == "expected awaitState to fail") throw error
            assertTrue(error.message!!.contains("Last value: only"))
        }
    }
}
