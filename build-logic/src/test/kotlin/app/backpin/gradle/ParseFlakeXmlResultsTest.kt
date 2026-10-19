// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParseFlakeXmlResultsTest {
    @Test
    fun missingXmlFailsTheTally() {
        val tally =
            parseFlakeXmlResults(
                listOf("app.A"),
                listOf(mapOf("app.A" to null)),
            )
        assertTrue(tally.failed)
        assertEquals(1, tally.runCount)
        assertEquals(0, tally.classes.single().passCount)
        assertEquals(1, tally.classes.single().failCount)
        assertTrue(tally.issues.single().contains("Missing TEST-app.A.xml for run 1"))
    }

    @Test
    fun failedSuiteFailsTheTally() {
        val xml =
            """
            <?xml version="1.0"?>
            <testsuite failures="1" errors="0" tests="1">
              <testcase name="boom"><failure message="nope"/></testcase>
            </testsuite>
            """.trimIndent()
        val tally =
            parseFlakeXmlResults(
                listOf("app.A"),
                listOf(mapOf("app.A" to xml)),
            )
        assertTrue(tally.failed)
        assertEquals(0, tally.classes.single().passCount)
        assertEquals(1, tally.classes.single().failCount)
        assertEquals(listOf("boom"), tally.classes.single().failedMethods)
    }
}
