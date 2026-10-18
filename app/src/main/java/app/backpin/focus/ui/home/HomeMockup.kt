// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandCounts
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.components.AddFab
import app.backpin.focus.ui.components.AppTab
import app.backpin.focus.ui.components.AppTabBar
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme

/**
 * Frozen Home mockup: NOW, bands, compact rows, footer, + Add, and the five tabs.
 */
@Composable
internal fun HomeMockup(
    modifier: Modifier = Modifier,
    todayLimit: Int = 3,
) {
    val bands = mockupBands()
    Box(
        modifier
            .fillMaxSize()
            .background(FocusColors.Live.Midnight),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = HomeMetrics.PageHorizontal,
                    end = HomeMetrics.PageHorizontal,
                    top = HomeMetrics.PageVertical,
                    bottom = HomeMetrics.ListBottom + HomeMetrics.TabBarHeight,
                ),
        ) {
            Text(
                text = stringResource(R.string.home_eyebrow),
                style = HomeMetrics.Eyebrow,
                color = FocusColors.Live.Muted,
                modifier = Modifier.padding(top = HomeMetrics.EyebrowTop, bottom = HomeMetrics.EyebrowBottom),
            )
            Text(
                text = stringResource(R.string.home_title),
                style = HomeMetrics.ScreenTitle,
                color = FocusColors.Live.Text,
                modifier =
                Modifier
                    .padding(bottom = HomeMetrics.TitleBottom)
                    .semantics { heading() },
            )
            MockNowCard()
            HomeBandsSection(
                bands = bands,
                filled = filledFrom(bands),
                hasRoom = false,
                todayLimit = todayLimit,
                reducedMotion = true,
                somethingCurrent = true,
                onOpenFocus = {},
                onStart = {},
                onDone = {},
                onLater = {},
                onInterrupt = {},
                onReorder = {},
                onEditTask = {},
                onCheckOffRepeat = {},
                onEditRepeat = {},
            )
            Text(
                text = stringResource(R.string.home_missed_footer),
                style = HomeMetrics.NowTime,
                color = FocusColors.Live.Muted,
                modifier = Modifier.padding(top = HomeMetrics.FlatHeaderTop),
            )
        }
        AddFab(
            onClick = {},
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = HomeMetrics.TabBarHeight + 16.dp),
        )
        AppTabBar(
            selected = AppTab.HOME,
            inboxCount = 4,
            comingUpCount = 3,
            onSelect = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MockNowCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeMetrics.NowRadius),
        color = FocusColors.Live.Surface,
        border = BorderStroke(HomeMetrics.NowBorder, FocusColors.Live.Lavender),
    ) {
        Column(
            Modifier.padding(
                horizontal = HomeMetrics.NowPaddingHorizontal,
                vertical = HomeMetrics.NowPaddingVertical,
            ),
        ) {
            Text(
                stringResource(R.string.home_now),
                color = FocusColors.Live.Lavender,
                style = HomeMetrics.NowLabel,
                modifier = Modifier.padding(bottom = HomeMetrics.NowLabelBottom),
            )
            Text(
                "Finish tax form",
                style = HomeMetrics.NowTitle,
                color = FocusColors.Live.Text,
                modifier = Modifier.padding(bottom = HomeMetrics.NowTitleBottom),
            )
            ElapsedText(
                accumulatedMillis = (12L * 60 + 4) * 60_000L,
                startedAtEpochMillis = 0L,
                running = false,
                clock = true,
            )
        }
    }
}

private fun mockupBands(): HomeBands = HomeBands(
    morning =
    group(
        HomeBand.MORNING,
        BandPhase.PAST,
        repeats =
        listOf(
            repeatRow("Meds", RepeatBand.MORNING, RepeatDayStatus.DONE, everyNDays = 1),
            repeatRow("Stretch", RepeatBand.MORNING, RepeatDayStatus.OPEN, everyNDays = 1),
        ),
        counts = HomeBandCounts(done = 1, left = 1),
    ),
    noon =
    group(
        HomeBand.NOON,
        BandPhase.OPEN,
        tasks = listOf(task("noon-1", "Review notes")),
        repeats = listOf(repeatRow("Water plants", RepeatBand.NOON, RepeatDayStatus.OPEN, everyNDays = 2)),
        counts = HomeBandCounts(left = 2),
    ),
    night =
    group(
        HomeBand.NIGHT,
        BandPhase.FUTURE,
        repeats = listOf(repeatRow("Lights out", RepeatBand.NIGHT, RepeatDayStatus.OPEN, everyNDays = 1)),
        counts = HomeBandCounts(left = 1),
    ),
    anytime =
    group(
        HomeBand.ANYTIME,
        BandPhase.ALWAYS_OPEN,
        tasks = listOf(task("any", "Call mom")),
        counts = HomeBandCounts(left = 1),
    ),
    openBand = HomeBand.NOON,
)

private fun group(
    band: HomeBand,
    phase: BandPhase,
    tasks: List<Task> = emptyList(),
    repeats: List<RepeatRow> = emptyList(),
    counts: HomeBandCounts = HomeBandCounts(),
): HomeBandGroup = HomeBandGroup(
    band = band,
    tasks = tasks,
    repeats = repeats,
    counts = counts,
    phase = phase,
)

private fun filledFrom(bands: HomeBands): List<Pair<SlotUi, TaskUi>> = bands.inOrder().flatMap { group ->
    group.tasks.map { task ->
        val ui =
            TaskUi(
                id = task.id,
                title = task.title,
                returnCue = task.returnCue,
                scheduledAtMinuteOfDay = task.scheduledAtMinuteOfDay,
            )
        SlotUi(SlotRole.OPEN, ui) to ui
    }
}

private fun task(
    id: String,
    title: String,
): Task = Task(
    id = id,
    title = title,
    bucket = TaskBucket.TODAY,
    status = TaskStatus.OPEN,
    returnCue = null,
    sortOrder = 0,
    createdAtEpochMillis = 0,
    completedAtEpochMillis = null,
)

private fun repeatRow(
    title: String,
    band: RepeatBand,
    status: RepeatDayStatus,
    everyNDays: Int,
): RepeatRow = RepeatRow(
    occurrence =
    RepeatOccurrence(
        repeat =
        Repeat(
            id = title,
            title = title,
            band = band,
            everyNDays = everyNDays,
            anchorEpochDay = 0,
            reminderOn = true,
            sortOrder = 0,
            createdAtEpochMillis = 0,
            archivedAtEpochMillis = null,
        ),
        epochDay = 0,
        status = status,
    ),
    everyNDays = everyNDays,
)

@Preview(name = "Home Signal", widthDp = 412, heightDp = 891)
@Composable
private fun HomeMockupSignalPreview() {
    FocusStackTheme(ThemeId.SIGNAL) {
        HomeMockup()
    }
}

@Preview(name = "Home Ember", widthDp = 412, heightDp = 891)
@Composable
private fun HomeMockupEmberPreview() {
    FocusStackTheme(ThemeId.EMBER) {
        HomeMockup()
    }
}
