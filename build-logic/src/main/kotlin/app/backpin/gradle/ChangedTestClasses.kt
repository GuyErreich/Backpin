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
 * Skips line comments, block comments, and raw `"""` strings so a `class`
 * token inside them is not treated as a test.
 * Requires a `package` line. Throws if a package or class name fails
 * [isValidClassName]. [filePath] is included when no package line exists.
 * The package always comes from the source `package` line, never from
 * [filePath].
 */
fun parseTopLevelTestClasses(
    source: String,
    filePath: String,
): List<String> {
    val active = blankCommentsAndRawStrings(source)
    val pkg =
        active
            .lineSequence()
            .mapNotNull { packageLine.matchEntire(it.trim())?.groupValues?.get(1) }
            .firstOrNull()
            ?: error("No package line in $filePath")
    check(isValidClassName(pkg)) { "Invalid package name" }
    return active.lineSequence().mapNotNull { line ->
        val match = topLevelClass.matchAt(line, 0) ?: return@mapNotNull null
        val simple = match.groupValues[2]
        check(isValidClassName(simple)) { "Invalid class name" }
        val fqcn = "$pkg.$simple"
        check(isValidClassName(fqcn)) { "Invalid test class name" }
        fqcn
    }.toList()
}

/**
 * Replaces comment and raw-string bodies with spaces, keeping newlines so
 * line-start class matching still lines up with the original source.
 */
internal fun blankCommentsAndRawStrings(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0
    var inBlockComment = false
    var inRawString = false
    var inLineComment = false
    while (i < source.length) {
        val c = source[i]
        val next = source.getOrNull(i + 1)
        when {
            inLineComment -> {
                if (c == '\n') {
                    inLineComment = false
                    out.append('\n')
                } else {
                    out.append(' ')
                }
                i++
            }
            inBlockComment -> {
                if (c == '*' && next == '/') {
                    out.append("  ")
                    i += 2
                    inBlockComment = false
                } else {
                    out.append(if (c == '\n') '\n' else ' ')
                    i++
                }
            }
            inRawString -> {
                if (c == '"' && next == '"' && source.getOrNull(i + 2) == '"') {
                    out.append("   ")
                    i += 3
                    inRawString = false
                } else {
                    out.append(if (c == '\n') '\n' else ' ')
                    i++
                }
            }
            c == '/' && next == '/' -> {
                out.append("  ")
                i += 2
                inLineComment = true
            }
            c == '/' && next == '*' -> {
                out.append("  ")
                i += 2
                inBlockComment = true
            }
            c == '"' && next == '"' && source.getOrNull(i + 2) == '"' -> {
                out.append("   ")
                i += 3
                inRawString = true
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

fun parseDispatchClasses(raw: String): List<String> =
    raw.split(',', ' ', '\t', '\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .onEach { check(isValidClassName(it)) { "Invalid test class name" } }

fun isValidGitSha(value: String): Boolean = GIT_SHA.matches(value)

fun gitChangedTestFiles(diffOutput: String): List<String> =
    diffOutput.split('\u0000').map { it.trim() }.filter { it.endsWith("Test.kt") }

fun resolveSelectedTestClasses(
    dispatchClasses: String,
    useMergeParents: Boolean,
    baseSha: String,
    headSha: String,
    changedTestFiles: (revisionRange: String) -> List<String>,
    readSource: (relativePath: String) -> String,
): List<String> {
    val dispatch = dispatchClasses.trim()
    if (dispatch.isNotEmpty()) {
        return parseDispatchClasses(dispatch).distinct().sorted()
    }
    val revisionRange =
        if (useMergeParents) {
            "HEAD^1...HEAD^2"
        } else {
            val base = baseSha.trim()
            val head = headSha.trim()
            if (base.isEmpty() || head.isEmpty()) {
                return emptyList()
            }
            check(isValidGitSha(base) && isValidGitSha(head)) { "Invalid git SHA" }
            "$base...$head"
        }
    return changedTestFiles(revisionRange)
        .flatMap { relative -> parseTopLevelTestClasses(readSource(relative), relative) }
        .distinct()
        .sorted()
}

private val GIT_SHA = Regex("^[0-9a-f]{40}([0-9a-f]{24})?$")
