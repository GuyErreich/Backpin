// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParseRunCountTest {
    @Test
    fun acceptsIntegersFromOneToOneHundred() {
        listOf(
            "1" to 1,
            "30" to 30,
            "100" to 100,
            " 7 " to 7,
        ).forEach { (raw, expected) ->
            assertEquals(expected, parseRunCount(raw), raw)
        }
    }

    @Test
    fun rejectsOutOfRangeAndNonIntegers() {
        listOf("0", "101", "-1", "", "abc", "30.0", "1e2").forEach { raw ->
            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    parseRunCount(raw)
                }
            assertTrue(
                thrown.message.orEmpty().contains("1 to 100"),
                raw,
            )
        }
    }
}
