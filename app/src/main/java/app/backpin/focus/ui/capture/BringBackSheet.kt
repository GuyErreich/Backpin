// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.PrimaryButton
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.components.SnapWheel
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import app.backpin.focus.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal const val BRING_BACK_MIN_DAYS = 1
internal const val BRING_BACK_MAX_DAYS = 365
internal const val BRING_BACK_DEFAULT_DAYS = 5

/** Wheel start for a task that already has a date after [today]; otherwise 5. */
internal fun bringBackInitialDays(
    scheduledEpochDay: Long?,
    today: LocalDate,
): Int {
    if (scheduledEpochDay == null) return BRING_BACK_DEFAULT_DAYS
    val daysAway = (scheduledEpochDay - today.toEpochDay()).toInt()
    if (daysAway <= 0) return BRING_BACK_DEFAULT_DAYS
    return daysAway.coerceIn(BRING_BACK_MIN_DAYS, BRING_BACK_MAX_DAYS)
}

private enum class BringBackPage {
    Days,
    Date,
}

/**
 * Days wheel, then an optional calendar. Dismiss without Save moves nothing.
 * [today] is the injected local day from ViewModel state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BringBackSheet(
    onDismiss: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onKeepInInbox: () -> Unit,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
    earliest: LocalDate? = null,
    initialDays: Int = BRING_BACK_DEFAULT_DAYS,
) {
    val minDate = earliest ?: today
    val startDays = initialDays.coerceIn(BRING_BACK_MIN_DAYS, BRING_BACK_MAX_DAYS)
    var page by remember { mutableStateOf(BringBackPage.Days) }
    var days by remember { mutableIntStateOf(startDays) }
    var picked by remember { mutableStateOf(today.plusDays(startDays.toLong())) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        containerColor = FocusColors.Live.Surface,
        contentWindowInsets = { WindowInsets.navigationBars },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        dragHandle = { BottomSheetDefaults.DragHandle(color = FocusColors.Live.Muted) },
    ) {
        BackHandler(onBack = onDismiss)
        when (page) {
            BringBackPage.Days ->
                BringBackDaysPane(
                    today = today,
                    days = days,
                    locale = locale,
                    onDays = { days = it },
                    onPickDate = {
                        picked = today.plusDays(days.toLong()).coerceAtLeast(minDate)
                        page = BringBackPage.Date
                    },
                    onSave = { onDate(today.plusDays(days.toLong())) },
                    onKeepInInbox = onKeepInInbox,
                )
            BringBackPage.Date ->
                BringBackDatePane(
                    today = today,
                    earliest = minDate,
                    selected = picked.coerceAtLeast(minDate),
                    locale = locale,
                    onSelect = { picked = it },
                    onSave = { onDate(picked.coerceAtLeast(minDate)) },
                )
        }
    }
}

@Composable
internal fun BringBackDaysPane(
    today: LocalDate,
    days: Int,
    locale: Locale,
    onDays: (Int) -> Unit,
    onPickDate: () -> Unit,
    onSave: () -> Unit,
    onKeepInInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landing = today.plusDays(days.toLong())
    val pickDate = remember { MutableInteractionSource() }
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space),
    ) {
        Text(
            text = stringResource(R.string.bring_back_sheet_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = FocusColors.Live.Text,
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.bring_back_sheet_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Live.Muted,
            modifier = Modifier.fillMaxWidth(),
        )
        DaysWheel(
            selectedDays = days,
            onSelectedDays = onDays,
        )
        Text(
            text = scheduleMomentLabel(landing.toEpochDay(), null, locale),
            style = MaterialTheme.typography.bodyLarge,
            color = FocusColors.Live.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.bring_back_pick_a_date),
            style = MaterialTheme.typography.bodyLarge,
            color = FocusColors.Live.Text,
            textAlign = TextAlign.Center,
            textDecoration = TextDecoration.Underline,
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = pickDate,
                    indication = null,
                    role = Role.Button,
                    onClick = onPickDate,
                )
                .padding(vertical = 8.dp),
        )
        PrimaryButton(
            label = pluralStringResource(R.plurals.bring_back_save_in_days, days, days),
            onClick = onSave,
        )
        ActionRow {
            QuietButton(
                label = stringResource(R.string.bring_back_keep_in_inbox),
                onClick = onKeepInInbox,
            )
        }
    }
}

@Composable
internal fun BringBackDatePane(
    today: LocalDate,
    earliest: LocalDate,
    selected: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = today.plusDays(BRING_BACK_MAX_DAYS.toLong())
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val minMonth = YearMonth.from(earliest)
    val maxMonth = YearMonth.from(latest)
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Space),
    ) {
        Text(
            text = stringResource(R.string.bring_back_pick_a_date_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = FocusColors.Live.Text,
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        MonthHeader(
            month = month,
            locale = locale,
            canGoBack = month.isAfter(minMonth),
            canGoForward = month.isBefore(maxMonth),
            onBack = { month = month.minusMonths(1) },
            onForward = { month = month.plusMonths(1) },
        )
        WeekdayRow(locale = locale)
        MonthGrid(
            month = month,
            today = today,
            earliest = earliest,
            selected = selected,
            onSelect = onSelect,
        )
        PrimaryButton(
            label =
            stringResource(
                R.string.bring_back_save_on_date,
                scheduleMomentLabel(selected.toEpochDay(), null, locale),
            ),
            onClick = onSave,
        )
    }
}

@Composable
internal fun DaysWheel(
    selectedDays: Int,
    onSelectedDays: (Int) -> Unit,
    maxDays: Int = BRING_BACK_MAX_DAYS,
) {
    val capped = maxDays.coerceAtLeast(BRING_BACK_MIN_DAYS)
    SnapWheel(
        count = capped,
        selectedIndex = (selectedDays - 1).coerceIn(0, capped - 1),
        onSelectedIndex = { index -> onSelectedDays(index + 1) },
        itemLabel = { index ->
            val days = index + 1
            pluralStringResource(R.plurals.bring_back_days, days, days)
        },
    )
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    locale: Locale,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val label = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = FocusColors.Live.Text,
            modifier = Modifier.weight(1f),
        )
        WithoutRipple {
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.bring_back_prev_month),
                    tint = if (canGoBack) FocusColors.Live.Text else FocusColors.Live.Muted,
                )
            }
        }
        WithoutRipple {
            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.bring_back_next_month),
                    tint = if (canGoForward) FocusColors.Live.Text else FocusColors.Live.Muted,
                )
            }
        }
    }
}

@Composable
private fun WeekdayRow(locale: Locale) {
    val labels = remember(locale) { sundayWeekdayLabels(locale) }
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = FocusColors.Live.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    earliest: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val days = remember(month) { sundayMonthCells(month) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        DayCell(
                            date = date,
                            inMonth = date.month == month.month,
                            today = today,
                            earliest = earliest,
                            selected = selected,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    today: LocalDate,
    earliest: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val enabled = inMonth && !date.isBefore(earliest)
    val isSelected = inMonth && date == selected
    val isToday = date == today
    val source = remember { MutableInteractionSource() }
    val textColor =
        when {
            isSelected -> FocusColors.Live.OnMint
            !enabled -> FocusColors.Live.Muted.copy(alpha = 0.45f)
            else -> FocusColors.Live.Text
        }
    val shape = CircleShape
    Box(
        modifier =
        Modifier
            .size(48.dp)
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.background(FocusColors.Live.Mint, shape)
                } else if (isToday && enabled) {
                    Modifier.border(1.5.dp, FocusColors.Live.Text, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = { onSelect(date) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

internal fun sundayWeekdayLabels(locale: Locale): List<String> = (0L..6L).map { offset ->
    DayOfWeek.SUNDAY.plus(offset).getDisplayName(TextStyle.NARROW_STANDALONE, locale)
}

internal fun sundayMonthCells(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7
    val start = first.minusDays(lead.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

private val PreviewToday = LocalDate.of(2026, 10, 18)

@Composable
private fun BringBackPreviewFrame(content: @Composable () -> Unit) {
    FocusStackTheme {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(FocusColors.Live.Midnight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        FocusColors.Live.Surface,
                        RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                    Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .size(32.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FocusColors.Live.Muted),
                )
                content()
            }
        }
    }
}

@Preview(name = "Bring it back in", widthDp = 412, heightDp = 720)
@Composable
internal fun BringBackDaysPreview() {
    BringBackPreviewFrame {
        BringBackDaysPane(
            today = PreviewToday,
            days = BRING_BACK_DEFAULT_DAYS,
            locale = Locale.US,
            onDays = {},
            onPickDate = {},
            onSave = {},
            onKeepInInbox = {},
        )
    }
}

@Preview(name = "Pick a date", widthDp = 412, heightDp = 720)
@Composable
internal fun BringBackDatePreview() {
    val today = PreviewToday
    BringBackPreviewFrame {
        BringBackDatePane(
            today = today,
            earliest = today,
            selected = today.plusDays(11),
            locale = Locale.US,
            onSelect = {},
            onSave = {},
        )
    }
}
