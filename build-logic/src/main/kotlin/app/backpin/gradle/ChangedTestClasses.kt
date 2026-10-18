// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

private val validName = Regex("^[A-Za-z0-9_.]+$")
private val packageLine = Regex("^package\\s+([A-Za-z0-9_.]+)\\s*;?\\s*(//.*)?$")
private val topLevelClass = Regex("^(internal )?class ([A-Za-z_][A-Za-z0-9_]*)\\b")

/** Full-string match: letters, digits, underscores, and dots only. */
fun isValidClassName(name: String): Boolean = validName.matches(name)

/**
 * Fully qualified names of line-start `class X` / `internal class X` declarations.
 *
 * Skips other kinds (`private`, `abstract`, `open`, `data`, `sealed`, `enum`,
 * `inner`, `object`) because those prefixes keep the line from matching.
 * Requires a `package` line. Throws if a package or class name fails
 * [isValidClassName].
 */
fun parseTopLevelTestClasses(source: String): List<String> {
    val pkg =
        source.lineSequence()
            .mapNotNull { packageLine.matchEntire(it.trim())?.groupValues?.get(1) }
            .firstOrNull()
            ?: error("No package line")
    check(isValidClassName(pkg)) { "Invalid package name" }
    return source.lineSequence().mapNotNull { line ->
        val match = topLevelClass.matchAt(line, 0) ?: return@mapNotNull null
        val simple = match.groupValues[2]
        check(isValidClassName(simple)) { "Invalid class name" }
        val fqcn = "$pkg.$simple"
        check(isValidClassName(fqcn)) { "Invalid test class name" }
        fqcn
    }.toList()
}

fun parseDispatchClasses(raw: String): List<String> =
    raw.split(',', ' ', '\t', '\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .onEach { check(isValidClassName(it)) { "Invalid test class name" } }
