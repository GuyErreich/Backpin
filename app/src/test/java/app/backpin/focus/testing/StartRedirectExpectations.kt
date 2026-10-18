// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.Task
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * Parent named in `StartOutcome.RedirectedToInterruption` and
 * `R.string.start_redirect_message`. Jhon: the deepest task's direct parent.
 *
 * One-line change: `chainTitles.first()` names the task that was tapped.
 */
fun expectedStartRedirectParentTitle(chainTitles: List<String>): String {
    check(chainTitles.size >= 2) { "redirect parent needs a chain of at least two titles" }
    return chainTitles[chainTitles.lastIndex - 1]
}

fun expectedStartRedirectInterruptionTitle(chainTitles: List<String>): String {
    check(chainTitles.isNotEmpty()) { "redirect interruption needs a chain" }
    return chainTitles.last()
}

/**
 * Places [titles] as A, then nested interruptions B, C, … under the active
 * frame. Advances the fixture clock between children so "newest open" is
 * the last title.
 */
suspend fun TestFocusFixture.nestInterruptChain(titles: List<String>): List<Task> {
    check(titles.isNotEmpty()) { "nestInterruptChain needs at least one title" }
    val rootTitle = titles.first()
    val placed = repository.captureIntoToday(rootTitle)
    check(placed is PromoteOutcome.Promoted) {
        "could not place '$rootTitle' on Today: $placed"
    }
    val root =
        repository.observeSnapshot().first().tasks.single { it.title == rootTitle }
    repository.start(root.id)
    val chain = mutableListOf(root)
    for (title in titles.drop(1)) {
        clock.advanceBy(Duration.ofMillis(10))
        chain += repository.interrupt(title)
    }
    return chain
}
