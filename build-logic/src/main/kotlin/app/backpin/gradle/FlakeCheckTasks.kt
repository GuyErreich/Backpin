// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

fun Project.configureFlakeCheck() {
    val classesFile = rootProject.layout.buildDirectory.file("flake-check/classes.txt")
    val resultsDir = rootProject.layout.buildDirectory.dir("flake-check/runs")
    val summaryFile = rootProject.layout.buildDirectory.file("flake-check/summary.md")
    val runCount = parseRunCount(providers.environmentVariable("RUN_COUNT").orElse("30").get())
    val plannedClasses = peekSelectedTestClasses()

    val select =
        tasks.register<SelectChangedTestClassesTask>("selectChangedTestClasses") {
            group = "verification"
            description = "Write changed unit test class names for flake-check."
            baseSha.set(providers.environmentVariable("FLAKE_CHECK_BASE").orElse(""))
            headSha.set(providers.environmentVariable("FLAKE_CHECK_HEAD").orElse(""))
            dispatchClasses.set(providers.environmentVariable("FLAKE_CHECK_CLASSES").orElse(""))
            useMergeParents.set(
                providers.environmentVariable("FLAKE_CHECK_MERGE_PARENTS")
                    .map { it == "true" }
                    .orElse(false),
            )
            repoRoot.set(rootProject.layout.projectDirectory)
            outputFile.set(classesFile)
        }

    val summarize =
        tasks.register<SummarizeFlakeResultsTask>("summarizeFlakeResults") {
            group = "verification"
            description = "Write flake-check pass/fail summary from per-run JUnit XML."
            this.classesFile.set(classesFile)
            this.resultsDir.set(resultsDir)
            expectedRuns.set(runCount.toString())
            this.summaryFile.set(summaryFile)
            dependsOn(select)
        }

    tasks.register("flakeCheck") {
        group = "verification"
        description = "Select changed unit tests, rerun them, and summarize results."
        dependsOn(select)
        dependsOn(summarize)
    }

    if (plannedClasses.isEmpty()) {
        return
    }

    afterEvaluate {
        val sourceTest = tasks.named<Test>("testDebugUnitTest")
        val selectedClasses = select.flatMap { it.outputFile }
        val flakeRuns =
            (1..runCount).map { index ->
                tasks.register<Test>("flakeRun$index") {
                    group = "verification"
                    description = "Flake-check unit test run $index of $runCount."
                    dependsOn(select)
                    val source = sourceTest.get()
                    testClassesDirs = source.testClassesDirs
                    classpath = source.classpath
                    workingDir = source.workingDir
                    minHeapSize = source.minHeapSize
                    maxHeapSize = source.maxHeapSize
                    jvmArgs = source.jvmArgs
                    enableAssertions = source.enableAssertions
                    forkEvery = source.forkEvery
                    maxParallelForks = source.maxParallelForks
                    source.systemProperties.forEach { (key, value) ->
                        systemProperty(key, value)
                    }
                    environment(source.environment)
                    source.jvmArgumentProviders.forEach { provider ->
                        jvmArgumentProviders.add(provider)
                    }
                    javaLauncher.set(source.javaLauncher)
                    useJUnit()
                    ignoreFailures = true
                    outputs.upToDateWhen { false }
                    reports.junitXml.required.set(true)
                    reports.junitXml.outputLocation.set(
                        rootProject.layout.buildDirectory.dir("flake-check/runs/$index"),
                    )
                    reports.html.outputLocation.set(
                        rootProject.layout.buildDirectory.dir("flake-check/html/$index"),
                    )
                    binaryResultsDirectory.set(
                        rootProject.layout.buildDirectory.dir("flake-check/binary/$index"),
                    )
                    inputs.file(selectedClasses)
                    onlyIf {
                        val file = selectedClasses.get().asFile
                        file.isFile && file.readText().isNotBlank()
                    }
                    doFirst {
                        val classes =
                            selectedClasses.get().asFile.readLines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        classes.forEach { name ->
                            check(isValidClassName(name)) { "Invalid test class name" }
                        }
                        filter.setIncludePatterns(*classes.toTypedArray())
                        filter.isFailOnNoMatchingTests = true
                    }
                }
            }
        flakeRuns.forEachIndexed { index, task ->
            if (index > 0) {
                task.configure { mustRunAfter(flakeRuns[index - 1]) }
            }
        }
        summarize.configure { dependsOn(flakeRuns) }
    }
}

internal fun parseRunCount(raw: String): Int {
    val value = raw.trim().toIntOrNull()
    check(value != null && value in 1..100) { "runs must be an integer from 1 to 100" }
    return value
}

private fun Project.peekSelectedTestClasses(): List<String> {
    val dispatch = providers.environmentVariable("FLAKE_CHECK_CLASSES").orElse("").get()
    val mergeParents =
        providers.environmentVariable("FLAKE_CHECK_MERGE_PARENTS").orElse("false").get() == "true"
    val base = providers.environmentVariable("FLAKE_CHECK_BASE").orElse("").get()
    val head = providers.environmentVariable("FLAKE_CHECK_HEAD").orElse("").get()
    return resolveSelectedTestClasses(
        dispatch,
        mergeParents,
        base,
        head,
        ::gitChangedTestFileNames,
    ) { relative ->
        val file = rootProject.file(relative)
        check(file.isFile) { "Changed test file is missing: $relative" }
        file.readText()
    }
}

private fun Project.gitChangedTestFileNames(revisionRange: String): List<String> {
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
            workingDir = rootProject.projectDir
        }.standardOutput.asText.get()
    return gitChangedTestFiles(output)
}
