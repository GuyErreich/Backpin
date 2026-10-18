// SPDX-License-Identifier: GPL-3.0-or-later
import app.backpin.gradle.SelectChangedTestClassesTask

tasks.register<SelectChangedTestClassesTask>("selectChangedTestClasses") {
    group = "verification"
    description = "Write changed unit test class names for flake-check."
    baseSha.set(providers.environmentVariable("FLAKE_CHECK_BASE").orElse(""))
    headSha.set(providers.environmentVariable("FLAKE_CHECK_HEAD").orElse(""))
    dispatchClasses.set(providers.environmentVariable("FLAKE_CHECK_CLASSES").orElse(""))
    repoRoot.set(layout.projectDirectory)
    outputFile.set(layout.buildDirectory.file("flake-check/classes.txt"))
}
