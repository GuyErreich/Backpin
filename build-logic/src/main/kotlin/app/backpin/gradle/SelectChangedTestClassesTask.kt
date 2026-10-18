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

    @get:Input
    abstract val useMergeParents: Property<Boolean>

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun select() {
        val classes =
            resolveSelectedTestClasses(
                dispatchClasses.get(),
                useMergeParents.get(),
                baseSha.get(),
                headSha.get(),
                ::changedTestFiles,
            ) { relative ->
                val file = repoRoot.get().file(relative).asFile
                check(file.isFile) { "Changed test file is missing: $relative" }
                file.readText()
            }
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

    private fun changedTestFiles(revisionRange: String): List<String> {
        val output =
            providers.exec {
                commandLine(
                    "git",
                    "diff",
                    "-z",
                    "--name-only",
                    "--diff-filter=ACMR",
                    revisionRange,
                    "--",
                    "app/src/test",
                )
                workingDir = repoRoot.get().asFile
            }.standardOutput.asText.get()
        return gitChangedTestFiles(output)
    }
}
