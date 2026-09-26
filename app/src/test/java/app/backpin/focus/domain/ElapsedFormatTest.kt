package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedFormatTest {
    @Test
    fun formatsStoredElapsedTime() {
        assertEquals("0m", formatElapsed(0))
        assertEquals("0m", formatElapsed(59_000))
        assertEquals("12m", formatElapsed(12 * 60_000L))
        assertEquals("1h 12m", formatElapsed((72 * 60_000L)))
    }

    @Test
    fun runningSegmentAddsToAccumulatedTime() {
        val elapsed =
            elapsedMillis(
                accumulatedMillis = 5 * 60_000L,
                startedAtEpochMillis = 1_000L,
                nowEpochMillis = 61_000L,
                running = true,
            )
        assertEquals(6 * 60_000L, elapsed)
        assertEquals(
            5 * 60_000L,
            elapsedMillis(5 * 60_000L, 1_000L, 61_000L, running = false),
        )
    }
}
