// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChangedTestClassesTest {
    @Test
    fun parseTopLevelTestClassesReturnsFullyQualifiedLineStartClasses() {
        parseCases().forEach { row ->
            assertEquals(row.expected, parseTopLevelTestClasses(row.source, row.filePath), row.name)
        }
    }

    @Test
    fun parseTopLevelTestClassesFailsWhenPackageLineIsMissing() {
        missingPackageCases().forEach { row ->
            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    parseTopLevelTestClasses(row.source, row.filePath)
                }
            assertTrue(
                thrown.message.orEmpty().contains(row.filePath),
                row.name,
            )
        }
    }

    @Test
    fun classInsideBlockCommentIsNotMatched() {
        val source =
            source(
                "package app.sample",
                "",
                "/*",
                "class InsideComment",
                "*/",
                "",
                "class RealTest",
            )
        assertEquals(
            listOf("app.sample.RealTest"),
            parseTopLevelTestClasses(source, "app/sample/RealTest.kt"),
        )
    }

    @Test
    fun classInsideRawStringIsNotMatched() {
        val source =
            source(
                "package app.sample",
                "",
                "val blob = \"\"\"",
                "class InsideRawString",
                "\"\"\"",
                "",
                "class RealTest",
            )
        assertEquals(
            listOf("app.sample.RealTest"),
            parseTopLevelTestClasses(source, "app/sample/RealTest.kt"),
        )
    }

    @Test
    fun packageComesFromSourceNotFilePath() {
        val source =
            source(
                "package app.sample",
                "",
                "class PathMismatchTest",
            )
        assertEquals(
            listOf("app.sample.PathMismatchTest"),
            parseTopLevelTestClasses(source, "other/place/PathMismatchTest.kt"),
        )
    }

    @Test
    fun isValidClassNameAcceptsOnlyLettersDigitsUnderscoresAndDots() {
        validNameCases().forEach { row ->
            assertTrue(isValidClassName(row.value), row.name)
        }
        invalidNameCases().forEach { row ->
            assertFalse(isValidClassName(row.value), row.name)
        }
    }

    @Test
    fun parseDispatchClassesSplitsOnCommasAndWhitespace() {
        dispatchCases().forEach { row ->
            assertEquals(row.expected, parseDispatchClasses(row.raw), row.name)
        }
    }

    @Test
    fun parseDispatchClassesRejectsInvalidEntries() {
        invalidDispatchCases().forEach { row ->
            assertThrows(IllegalStateException::class.java) {
                parseDispatchClasses(row.raw)
            }
        }
    }

    private fun parseCases(): List<ParseCase> =
        listOf(
            ParseCase(
                name = "two top-level classes in one file",
                source =
                    source(
                        "package app.sample",
                        "",
                        "class FirstTest",
                        "class SecondTest",
                    ),
                filePath = "app/sample/BundleTest.kt",
                expected = listOf("app.sample.FirstTest", "app.sample.SecondTest"),
            ),
            ParseCase(
                name = "internal class is included",
                source =
                    source(
                        "package app.sample",
                        "",
                        "internal class InternalTest",
                        "class PublicTest",
                    ),
                filePath = "app/sample/InternalTest.kt",
                expected = listOf("app.sample.InternalTest", "app.sample.PublicTest"),
            ),
            ParseCase(
                name = "private, abstract, data, and object helpers are excluded",
                source =
                    source(
                        "package app.sample",
                        "",
                        "class VisibleTest",
                        "private class HiddenHelper",
                        "abstract class AbstractHelper",
                        "data class DataHelper(val n: Int)",
                        "object ObjectHelper",
                    ),
                filePath = "app/sample/VisibleTest.kt",
                expected = listOf("app.sample.VisibleTest"),
            ),
            ParseCase(
                name = "open, sealed, enum, and inner prefixes are also excluded",
                source =
                    source(
                        "package app.sample",
                        "",
                        "class VisibleTest",
                        "open class OpenHelper",
                        "sealed class SealedHelper",
                        "enum class EnumHelper",
                        "inner class InnerHelper",
                    ),
                filePath = "app/sample/VisibleTest.kt",
                expected = listOf("app.sample.VisibleTest"),
            ),
            ParseCase(
                name = "indented or nested class at the start of a line is not matched",
                source =
                    source(
                        "package app.sample",
                        "",
                        "class OuterTest",
                        "    class NestedHelper",
                        " class SpacedHelper",
                    ),
                filePath = "app/sample/OuterTest.kt",
                expected = listOf("app.sample.OuterTest"),
            ),
            ParseCase(
                name = "package line may end with a semicolon or a line comment",
                source =
                    source(
                        "package app.sample; // flake",
                        "",
                        "class CommentPackageTest",
                    ),
                filePath = "app/sample/CommentPackageTest.kt",
                expected = listOf("app.sample.CommentPackageTest"),
            ),
        )

    private fun missingPackageCases(): List<NamedSource> =
        listOf(
            NamedSource("empty source", "", "Empty.kt"),
            NamedSource(
                "class without a package line",
                source("class MissingPackage"),
                "MissingPackage.kt",
            ),
            NamedSource(
                "commented package line does not count",
                source("// package app.sample", "", "class StillMissing"),
                "StillMissing.kt",
            ),
        )

    private fun validNameCases(): List<NameCase> =
        listOf(
            NameCase("simple class", "FocusRepositoryTest"),
            NameCase("fully qualified", "app.backpin.focus.data.FocusRepositoryTest"),
            NameCase("digits and underscores", "Suite_2"),
        )

    private fun invalidNameCases(): List<NameCase> =
        listOf(
            NameCase("empty", ""),
            NameCase("dollar nested name", "Outer\$Inner"),
            NameCase("spaces", "Focus Repository Test"),
            NameCase("semicolon", "app.Foo;Bar"),
            NameCase("newline", "Foo\nBar"),
            NameCase("shell-injection looking string", "; rm -rf /; echo \$(whoami)"),
        )

    private fun dispatchCases(): List<DispatchCase> =
        listOf(
            DispatchCase("comma separated", "app.Foo,app.Bar", listOf("app.Foo", "app.Bar")),
            DispatchCase(
                "commas and spaces",
                "app.Foo, app.Bar",
                listOf("app.Foo", "app.Bar"),
            ),
            DispatchCase(
                "tabs and newlines",
                "app.Foo\tapp.Bar\napp.Baz",
                listOf("app.Foo", "app.Bar", "app.Baz"),
            ),
            DispatchCase("blank tokens are dropped", "app.Foo,,  ,app.Bar", listOf("app.Foo", "app.Bar")),
            DispatchCase("empty raw", "", emptyList()),
            DispatchCase("whitespace only", "  \n\t  ", emptyList()),
            DispatchCase("duplicates are kept", "app.Foo,app.Foo", listOf("app.Foo", "app.Foo")),
        )

    private fun invalidDispatchCases(): List<NamedRaw> =
        listOf(
            NamedRaw("dollar sign", "app.Foo\$Bar"),
            NamedRaw("semicolon in a token", "app.Foo;"),
            NamedRaw("semicolon then another name", "app.Foo; app.Bar"),
            NamedRaw("shell-injection looking string", "; rm -rf /"),
        )

    private fun source(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

    private data class ParseCase(
        val name: String,
        val source: String,
        val filePath: String,
        val expected: List<String>,
    )

    private data class NamedSource(
        val name: String,
        val source: String,
        val filePath: String,
    )

    private data class NameCase(
        val name: String,
        val value: String,
    )

    private data class DispatchCase(
        val name: String,
        val raw: String,
        val expected: List<String>,
    )

    private data class NamedRaw(
        val name: String,
        val raw: String,
    )
}
