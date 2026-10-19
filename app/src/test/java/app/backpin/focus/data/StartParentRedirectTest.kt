// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.StartOutcome
import app.backpin.focus.domain.Task
import app.backpin.focus.testing.InMemoryFocusRule
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.expectedStartRedirectInterruptionTitle
import app.backpin.focus.testing.expectedStartRedirectParentTitle
import app.backpin.focus.testing.nestInterruptChain
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID

/**
 * Starting a parent with an open interruption walks the newest-open-child
 * chain, makes that leaf NOW, and returns [StartOutcome.RedirectedToInterruption].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartParentRedirectTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher = UnconfinedTestDispatcher())

    @get:Rule
    val focus = InMemoryFocusRule()

    @Test
    fun startWalksTheNewestOpenChildAndNamesItsDirectParent() = runBlocking {
        val rows =
            listOf(
                WalkRow(
                    name = "two-level chain",
                    titles = listOf("Write the brief", "Call the school"),
                    startAt = 0,
                ),
                WalkRow(
                    name = "three-level chain from the root",
                    titles = listOf("Write the brief", "Call the school", "Ping the vendor"),
                    startAt = 0,
                ),
                WalkRow(
                    name = "three-level chain from the middle",
                    titles = listOf("Write the brief", "Call the school", "Ping the vendor"),
                    startAt = 1,
                ),
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val chain = fixture.nestInterruptChain(row.titles)
                fixture.repository.pauseActive()
                val fromStart = chain.drop(row.startAt)
                val started = chain[row.startAt]
                val outcome = fixture.repository.start(started.id)
                val redirect = outcome as StartOutcome.RedirectedToInterruption
                val snapshot = fixture.repository.observeSnapshot().first()
                val leaf = chain.last()
                val parentTitle = expectedStartRedirectParentTitle(fromStart.map { it.title })
                val interruptionTitle = expectedStartRedirectInterruptionTitle(fromStart.map { it.title })
                assertEquals(row.name, leaf.id, redirect.interruptionId)
                assertEquals(row.name, interruptionTitle, redirect.interruptionTitle)
                assertEquals(row.name, parentTitle, redirect.parentTitle)
                assertEquals(
                    row.name,
                    chain[chain.lastIndex - 1].id,
                    redirect.parentId,
                )
                assertNow(row.name, snapshot, leaf)
                if (started.id != leaf.id) {
                    assertEquals(
                        row.name,
                        FrameState.PAUSED,
                        snapshot.frames.single { it.taskId == started.id }.state,
                    )
                }
            }
        }
    }

    @Test
    fun newestOpenSiblingWinsAndClosedNewerSiblingsAreSkipped() = runBlocking {
        val rows =
            listOf(
                SiblingRow(
                    name = "newest open sibling wins",
                    closeNewer = false,
                    expectedChildTitle = "Newer ping",
                ),
                SiblingRow(
                    name = "closed newer sibling is skipped",
                    closeNewer = true,
                    expectedChildTitle = "Older ping",
                ),
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val chain = fixture.nestInterruptChain(listOf("Write the brief", "Older ping"))
                val parent = chain.first()
                val older = chain.last()
                fixture.repository.pauseActive()
                fixture.clock.advanceBy(Duration.ofMillis(25))
                val newer = fixture.repository.seedInbox("Newer ping").single()
                val parentFrame =
                    fixture.repository.observeSnapshot().first()
                        .frames
                        .single { it.taskId == parent.id }
                fixture.database.focusFrameDao().upsert(
                    FocusFrameEntity(
                        id = "sibling-${UUID.randomUUID()}",
                        taskId = newer.id,
                        parentFrameId = parentFrame.id,
                        state =
                        if (row.closeNewer) {
                            FrameState.CLOSED.name
                        } else {
                            FrameState.PAUSED.name
                        },
                        startedAtEpochMillis = fixture.clock.nowEpochMillis(),
                        pausedAtEpochMillis = fixture.clock.nowEpochMillis(),
                        accumulatedMillis = 0,
                    ),
                )
                val outcome = fixture.repository.start(parent.id)
                val redirect = outcome as StartOutcome.RedirectedToInterruption
                val snapshot = fixture.repository.observeSnapshot().first()
                val expectedChild = if (row.closeNewer) older else newer
                assertEquals(row.name, expectedChild.id, redirect.interruptionId)
                assertEquals(row.name, row.expectedChildTitle, redirect.interruptionTitle)
                assertEquals(row.name, parent.id, redirect.parentId)
                assertEquals(row.name, parent.title, redirect.parentTitle)
                assertNow(row.name, snapshot, expectedChild)
            }
        }
    }

    @Test
    fun startWithNoOpenChildrenStartsTheParent() = runBlocking {
        val rows =
            listOf(
                "root with no children",
                "leaf of a two-level chain",
            )
        for (name in rows) {
            useTestFocus { fixture ->
                val started =
                    if (name == "leaf of a two-level chain") {
                        val chain =
                            fixture.nestInterruptChain(
                                listOf("Write the brief", "Call the school"),
                            )
                        fixture.repository.pauseActive()
                        chain.last()
                    } else {
                        fixture.nestInterruptChain(listOf("Write the brief")).single()
                    }
                val outcome = fixture.repository.start(started.id)
                val snapshot = fixture.repository.observeSnapshot().first()
                assertEquals(name, PromoteOutcome.Promoted, outcome)
                assertNow(name, snapshot, started)
            }
        }
    }

    @Test
    fun startStopsOnAFrameLoopWithADefinedRedirect() = runBlocking {
        val chain = focus.fixture.nestInterruptChain(listOf("Write the brief", "Call the school"))
        val parent = chain.first()
        val child = chain.last()
        focus.repository.pauseActive()
        val snapshot = focus.repository.observeSnapshot().first()
        val parentFrame = snapshot.frames.single { it.taskId == parent.id }
        val childFrame = snapshot.frames.single { it.taskId == child.id }
        val stored = focus.fixture.database.focusFrameDao().getById(parentFrame.id)
            ?: error("Missing parent frame")
        focus.fixture.database.focusFrameDao().upsert(
            stored.copy(parentFrameId = childFrame.id),
        )

        val outcome = focus.repository.start(parent.id)
        val after = focus.repository.observeSnapshot().first()
        val redirect = outcome as StartOutcome.RedirectedToInterruption
        assertEquals(1, after.frames.count { it.state == FrameState.ACTIVE })
        assertEquals(parent.id, redirect.interruptionId)
        assertEquals(child.id, redirect.parentId)
        assertEquals(parent.title, redirect.interruptionTitle)
        assertEquals(child.title, redirect.parentTitle)
        assertEquals(parent.id, after.frames.single { it.state == FrameState.ACTIVE }.taskId)
    }

    private fun assertNow(
        name: String,
        snapshot: FocusSnapshot,
        task: Task,
    ) {
        val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
        assertEquals(name, task.id, active.taskId)
        assertEquals(name, 1, snapshot.frames.count { it.state == FrameState.ACTIVE })
    }

    private data class WalkRow(
        val name: String,
        val titles: List<String>,
        val startAt: Int,
    )

    private data class SiblingRow(
        val name: String,
        val closeNewer: Boolean,
        val expectedChildTitle: String,
    )
}
