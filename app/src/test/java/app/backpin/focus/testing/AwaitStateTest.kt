// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AwaitStateTest {
    @Test
    fun returnsTheMatchingValue() = runTest {
        val rows =
            listOf(
                "ready" to { value: String -> value == "ready" },
                "later" to { value: String -> value.startsWith("lat") },
            )
        for ((value, matches) in rows) {
            val found = MutableStateFlow(value).awaitState(predicate = matches)
            assertEquals(value, found)
        }
    }

    @Test
    fun skipsStaleValuesUntilThePredicateMatches() = runTest {
        val states = MutableStateFlow(1)
        var found: Int? = null
        val waiter = launch { found = states.awaitState { it >= 3 } }
        for (stale in listOf(1, 2)) {
            states.value = stale
            testScheduler.runCurrent()
            assertEquals("stale $stale", null, found)
        }
        states.value = 3
        waiter.join()
        assertEquals(3, found)
    }

    @Test
    fun timeoutMessageIncludesTheLastValue() = runTest {
        val rows =
            listOf(
                TimeoutRow(
                    name = "stale state",
                    flow = MutableStateFlow("last-seen"),
                    timeout = 2.seconds,
                    matches = { it == "never" },
                    parts = listOf("timed out after 2s", "Last value: last-seen"),
                ),
                TimeoutRow(
                    name = "nothing emitted",
                    flow = flow { awaitCancellation() },
                    timeout = 1.seconds,
                    matches = { true },
                    parts = listOf("timed out after 1s", "Last value: none"),
                ),
                TimeoutRow(
                    name = "completed without match",
                    flow = flow { emit("only") },
                    timeout = 5.seconds,
                    matches = { it == "never" },
                    parts = listOf("flow completed without a match", "Last value: only"),
                ),
            )
        for (row in rows) {
            try {
                row.flow.awaitState(timeout = row.timeout, predicate = row.matches)
                fail("${row.name}: expected awaitState to fail")
            } catch (error: AssertionError) {
                if (error.message?.startsWith("${row.name}:") == true) throw error
                val message = error.message ?: ""
                for (part in row.parts) {
                    assertTrue("${row.name} missing '$part' in $message", message.contains(part))
                }
            }
        }
    }

    private data class TimeoutRow(
        val name: String,
        val flow: Flow<String>,
        val timeout: Duration,
        val matches: (String) -> Boolean,
        val parts: List<String>,
    )
}
