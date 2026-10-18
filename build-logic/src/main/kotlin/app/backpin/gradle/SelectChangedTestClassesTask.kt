// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import javax.inject.Inject

@UntrackedTask(because = "Reads git state and source files at execution time")
abstract class SelectChangedTestClassesTask : DefaultTask() {
    @get:Inject
    abstract val providers: ProviderFactory
    @get:Input
    abstract val baseSha: Property<String>

    @get:Input
    abstract val headSha: Property<String>

    @get:Input
    abstract val dispatchClasses: Property<String>

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun select() {
        val classes = selectedClasses().distinct().sorted()
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(if (classes.isEmpty()) "" else classes.joinToString("\n", postfix = "\n"))
        if (classes.isEmpty()) {
            logger.lifecycle("No changed unit test classes under app/src/test; skipping.")
        } else {
            logger.lifecycle("Selected test classes:")
            classes.forEach { logger.lifecycle(it) }
        }
    }

    private fun selectedClasses(): List<String> {
        val dispatch = dispatchClasses.get().trim()
        if (dispatch.isNotEmpty()) {
            return parseDispatchClasses(dispatch)
        }
        val base = baseSha.get().trim()
        val head = headSha.get().trim()
        if (base.isEmpty() || head.isEmpty()) {
            return emptyList()
        }
        check(isValidSha(base) && isValidSha(head)) { "Invalid git SHA" }
        return changedTestFiles(base, head).flatMap { relative ->
            val file = repoRoot.get().file(relative).asFile
            check(file.isFile) { "Changed test file is missing: $relative" }
            parseTopLevelTestClasses(file.readText())
        }
    }

    private fun changedTestFiles(base: String, head: String): List<String> {
        val output =
            providers.exec {
                commandLine(
                    "git",
                    "diff",
                    "-z",
                    "--name-only",
                    "--diff-filter=ACMR",
                    "$base...$head",
                    "--",
                    "app/src/test",
                )
                workingDir = repoRoot.get().asFile
            }.standardOutput.asText.get()
        return output.split('\u0000')
            .map { it.trim() }
            .filter { it.endsWith("Test.kt") }
    }

    private fun isValidSha(value: String): Boolean = SHA.matches(value)

    private companion object {
        val SHA = Regex("^[0-9a-fA-F]+$")
    }
}
