// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class SummarizeFlakeResultsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesFile: RegularFileProperty

    @get:Internal
    abstract val resultsDir: DirectoryProperty

    @get:Input
    abstract val expectedRuns: Property<String>

    @get:OutputFile
    abstract val summaryFile: RegularFileProperty

    @TaskAction
    fun summarize() {
        val classes =
            classesFile.get().asFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val out = summaryFile.get().asFile
        out.parentFile.mkdirs()
        if (classes.isEmpty()) {
            out.writeText("No changed unit test classes under `app/src/test`; skipping.\n")
            return
        }
        classes.forEach { check(isValidClassName(it)) { "Invalid test class name" } }
        val runCount = expectedRuns.get().trim().toIntOrNull()
        check(runCount != null && runCount in 1..100) { "runs must be an integer from 1 to 100" }
        val root = resultsDir.get().asFile
        val runs =
            (1..runCount).map { runNumber ->
                val dir = File(root, runNumber.toString())
                classes.associateWith { className ->
                    val xml = File(dir, "TEST-$className.xml")
                    if (xml.isFile) xml.readText() else null
                }
            }
        val tally = parseFlakeXmlResults(classes, runs)
        out.writeText(renderFlakeSummary(tally))
        if (tally.failed) {
            error("Flake check failed")
        }
    }
}
