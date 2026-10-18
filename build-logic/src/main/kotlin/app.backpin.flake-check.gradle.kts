// SPDX-License-Identifier: GPL-3.0-or-later
import app.backpin.gradle.SelectChangedTestClassesTask
import app.backpin.gradle.SummarizeFlakeResultsTask

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
    repoRoot.set(layout.projectDirectory)
    outputFile.set(layout.buildDirectory.file("flake-check/classes.txt"))
}

tasks.register<SummarizeFlakeResultsTask>("summarizeFlakeResults") {
    group = "verification"
    description = "Write flake-check pass/fail summary from per-run JUnit XML."
    classesFile.set(layout.buildDirectory.file("flake-check/classes.txt"))
    resultsDir.set(layout.buildDirectory.dir("flake-check/runs"))
    expectedRuns.set(providers.environmentVariable("RUN_COUNT").orElse("30"))
    summaryFile.set(layout.buildDirectory.file("flake-check/summary.md"))
}
