// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.backpin.focus.R
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandCounts
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.testing.sampleTask
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeBandHeaderVisibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyBandHeadersStayHiddenAndTheOpenBandAlwaysShowsNow() {
        var bands by mutableStateOf(bandsFor(emptySet(), HomeBand.NOON))
        compose.setContent {
            FocusStackTheme {
                HomeBandsSection(
                    bands = bands,
                    filled = filledFrom(bands),
                    hasRoom = false,
                    todayLimit = 3,
                    reducedMotion = true,
                    somethingCurrent = false,
                    onOpenFocus = {},
                    onStart = {},
                    onDone = {},
                    onBringBack = { _, _ -> },
                    onInterrupt = {},
                    onReorder = {},
                    onEditTask = {},
                    onCheckOffRepeat = {},
                    onEditRepeat = {},
                )
            }
        }
        compose.waitForIdle()
        val headers = allHeaders()
        CASES.forEach { case ->
            compose.runOnIdle { bands = bandsFor(case.filled, case.open) }
            compose.waitForIdle()
            val expected = expectedHeaders(case)
            headers.forEach { header ->
                val node = compose.onNodeWithText(header)
                if (header in expected) {
                    node.assertExists()
                } else {
                    node.assertDoesNotExist()
                }
            }
        }
    }

    private fun expectedHeaders(case: Case): Set<String> {
        val shown = mutableSetOf(nowHeader(case.open))
        case.filled.forEach { band ->
            if (band != case.open) shown += plainHeader(band)
        }
        return shown
    }

    private fun bandsFor(
        filled: Set<HomeBand>,
        open: HomeBand,
    ): HomeBands = HomeBands(
        morning = group(HomeBand.MORNING, filled, open),
        noon = group(HomeBand.NOON, filled, open),
        night = group(HomeBand.NIGHT, filled, open),
        anytime = group(HomeBand.ANYTIME, filled, open),
        openBand = open,
    )

    private fun group(
        band: HomeBand,
        filled: Set<HomeBand>,
        open: HomeBand,
    ): HomeBandGroup {
        val hasItems = band in filled
        val tasks =
            if (hasItems) {
                listOf(sampleTask(id = band.name, title = "${band.name} item"))
            } else {
                emptyList()
            }
        return HomeBandGroup(
            band = band,
            tasks = tasks,
            counts = if (hasItems) HomeBandCounts(left = 1) else HomeBandCounts(),
            phase = phaseFor(band, open),
        )
    }

    private fun phaseFor(
        band: HomeBand,
        open: HomeBand,
    ): BandPhase = when {
        band == HomeBand.ANYTIME -> BandPhase.ALWAYS_OPEN
        band == open -> BandPhase.OPEN
        band.ordinal < open.ordinal -> BandPhase.PAST
        else -> BandPhase.FUTURE
    }

    private fun filledFrom(bands: HomeBands): List<Pair<SlotUi, TaskUi>> = bands.inOrder().flatMap { group ->
        group.tasks.map { task ->
            val ui = TaskUi(id = task.id, title = task.title, returnCue = task.returnCue)
            SlotUi(SlotRole.OPEN, ui) to ui
        }
    }

    private fun plainHeader(band: HomeBand): String = compose.activity.getString(band.titleRes()).uppercase()

    private fun nowHeader(band: HomeBand): String = compose.activity.getString(R.string.home_band_current, plainHeader(band))

    private fun allHeaders(): List<String> {
        val timed = listOf(HomeBand.MORNING, HomeBand.NOON, HomeBand.NIGHT)
        return timed.flatMap { listOf(plainHeader(it), nowHeader(it)) } + plainHeader(HomeBand.ANYTIME)
    }

    private fun HomeBand.titleRes(): Int = when (this) {
        HomeBand.MORNING -> R.string.home_band_morning
        HomeBand.NOON -> R.string.home_band_noon
        HomeBand.NIGHT -> R.string.home_band_night
        HomeBand.ANYTIME -> R.string.home_band_anytime
    }

    private data class Case(
        val name: String,
        val filled: Set<HomeBand>,
        val open: HomeBand,
    )

    private companion object {
        val CASES: List<Case> =
            listOf(
                Case("all empty, noon now", emptySet(), HomeBand.NOON),
                Case("morning empty, noon now", setOf(HomeBand.NOON, HomeBand.NIGHT, HomeBand.ANYTIME), HomeBand.NOON),
                Case("noon empty, noon now", setOf(HomeBand.MORNING, HomeBand.NIGHT, HomeBand.ANYTIME), HomeBand.NOON),
                Case("night empty, noon now", setOf(HomeBand.MORNING, HomeBand.NOON, HomeBand.ANYTIME), HomeBand.NOON),
                Case("anytime empty, noon now", setOf(HomeBand.MORNING, HomeBand.NOON, HomeBand.NIGHT), HomeBand.NOON),
                Case("morning filled, noon now", setOf(HomeBand.MORNING), HomeBand.NOON),
                Case("noon filled, noon now", setOf(HomeBand.NOON), HomeBand.NOON),
                Case("night filled, noon now", setOf(HomeBand.NIGHT), HomeBand.NOON),
                Case("anytime filled, noon now", setOf(HomeBand.ANYTIME), HomeBand.NOON),
                Case("all empty, morning now", emptySet(), HomeBand.MORNING),
                Case("all empty, night now", emptySet(), HomeBand.NIGHT),
            )
    }
}
