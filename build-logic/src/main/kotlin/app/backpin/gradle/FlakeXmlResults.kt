// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class FlakeClassTally(
    val className: String,
    val passCount: Int,
    val failCount: Int,
    val failedMethods: List<String>,
)

data class FlakeTally(
    val runCount: Int,
    val classes: List<FlakeClassTally>,
    val issues: List<String>,
) {
    val failed: Boolean
        get() = classes.any { it.failCount > 0 } || issues.isNotEmpty()
}

sealed class JunitXmlParse {
    data class Suite(val failedMethods: List<String>, val passed: Boolean) : JunitXmlParse()

    data class Rejected(val reason: String) : JunitXmlParse()
}

/**
 * Reads one JUnit `TEST-*.xml` document. A DOCTYPE is rejected.
 * Failures and errors on `<testcase>` elements become method names.
 */
fun parseJunitTestXml(xml: String): JunitXmlParse {
    if (DOCTYPE.containsMatchIn(xml)) {
        return JunitXmlParse.Rejected("DOCTYPE is not allowed in JUnit XML")
    }
    val document =
        try {
            secureDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (thrown: SAXParseException) {
            val message = thrown.message.orEmpty()
            if (message.contains("DOCTYPE", ignoreCase = true)) {
                return JunitXmlParse.Rejected("DOCTYPE is not allowed in JUnit XML")
            }
            return JunitXmlParse.Rejected("Invalid JUnit XML")
        } catch (_: Exception) {
            return JunitXmlParse.Rejected("Invalid JUnit XML")
        }
    val root = document.documentElement ?: return JunitXmlParse.Rejected("Invalid JUnit XML")
    val failedMethods = failedMethodNames(root)
    val failures = root.getAttribute("failures").toIntOrNull() ?: 0
    val errors = root.getAttribute("errors").toIntOrNull() ?: 0
    val passed = failures == 0 && errors == 0 && failedMethods.isEmpty()
    return JunitXmlParse.Suite(failedMethods = failedMethods.distinct(), passed = passed)
}

/**
 * Tallies per-class pass/fail across reruns.
 *
 * [runs] is one map per run: fully qualified class name to JUnit XML text,
 * or null when that run's `TEST-*.xml` is missing.
 */
fun parseFlakeXmlResults(
    classes: List<String>,
    runs: List<Map<String, String?>>,
): FlakeTally {
    val issues = mutableListOf<String>()
    val tallies =
        classes.map { className ->
            var passCount = 0
            var failCount = 0
            val failedMethods = linkedSetOf<String>()
            runs.forEachIndexed { index, xmlByClass ->
                val runNumber = index + 1
                val xml = xmlByClass[className]
                if (xml == null) {
                    failCount += 1
                    issues += "Missing TEST-$className.xml for run $runNumber"
                    return@forEachIndexed
                }
                when (val parsed = parseJunitTestXml(xml)) {
                    is JunitXmlParse.Suite ->
                        if (parsed.passed) {
                            passCount += 1
                        } else {
                            failCount += 1
                            failedMethods.addAll(parsed.failedMethods)
                        }
                    is JunitXmlParse.Rejected -> {
                        failCount += 1
                        issues += "$className run $runNumber: ${parsed.reason}"
                    }
                }
            }
            FlakeClassTally(
                className = className,
                passCount = passCount,
                failCount = failCount,
                failedMethods = failedMethods.toList(),
            )
        }
    return FlakeTally(runCount = runs.size, classes = tallies, issues = issues)
}

fun renderFlakeSummary(tally: FlakeTally): String {
    val lines = mutableListOf<String>()
    lines += "## Flake check"
    lines += ""
    lines += "Runs: ${tally.runCount}"
    lines += ""
    lines += "| Class | Pass | Fail |"
    lines += "| --- | ---: | ---: |"
    tally.classes.forEach { row ->
        lines += "| ${row.className} | ${row.passCount} | ${row.failCount} |"
    }
    val failedTests =
        tally.classes.flatMap { row ->
            row.failedMethods.map { method -> "- ${row.className}.$method" }
        }
    if (failedTests.isNotEmpty() || tally.issues.isNotEmpty()) {
        lines += ""
        lines += "### Failed tests"
        failedTests.forEach { lines += it }
        tally.issues.forEach { lines += "- $it" }
    }
    lines += ""
    return lines.joinToString("\n")
}

private fun failedMethodNames(root: Element): List<String> {
    val nodes = root.getElementsByTagName("testcase")
    return (0 until nodes.length).mapNotNull { index ->
        val element = nodes.item(index) as? Element ?: return@mapNotNull null
        val failed =
            element.getElementsByTagName("failure").length > 0 ||
                element.getElementsByTagName("error").length > 0
        if (!failed) {
            return@mapNotNull null
        }
        element.getAttribute("name").takeIf { it.isNotEmpty() }
    }
}

private fun secureDocumentBuilder(): javax.xml.parsers.DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature(DISALLOW_DOCTYPE, true)
    factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false)
    factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false)
    factory.setFeature(LOAD_EXTERNAL_DTD, false)
    factory.setXIncludeAware(false)
    factory.isExpandEntityReferences = false
    return factory.newDocumentBuilder()
}

private val DOCTYPE = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)
private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
private const val EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
private const val EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
private const val LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
