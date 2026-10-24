// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.backpin.focus.R
import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.CountdownUnit
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.domain.minuteOfDayClock
import app.backpin.focus.domain.parseClockToMinute
import app.backpin.focus.notifications.notificationsAllowed
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.ChoicePills
import app.backpin.focus.ui.components.PillButton
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.components.SnapWheel
import app.backpin.focus.ui.components.ThemeSwitch
import app.backpin.focus.ui.home.HomeMetrics
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalTime

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onNudgeSelected: (enabled: Boolean, minutes: Int) -> Unit,
    onQuietHours: (start: String?, end: String?) -> Unit,
    onCurrentNotification: (Boolean) -> Unit,
    onTodayLimit: (Int) -> Unit,
    onAddPhrase: (String) -> Unit,
    onRenamePhrase: (index: Int, text: String) -> Unit,
    onRemovePhrase: (index: Int) -> Unit,
    bandTimes: BandTimes = BandTimes.DEFAULT,
    onBandTime: (BandEdge, Int) -> Unit = { _, _ -> },
    showExactAlarmPrompt: Boolean = false,
    onDismissExactAlarmPrompt: () -> Unit = {},
    onRefreshExactAlarmPermission: () -> Unit = {},
    themeId: ThemeId = ThemeId.SIGNAL,
    onThemeId: (ThemeId) -> Unit = {},
    homeLayout: HomeLayout = HomeLayout.FLAT,
    onHomeLayout: (HomeLayout) -> Unit = {},
    showHomeLayout: Boolean = false,
    countdownUnit: CountdownUnit = CountdownUnit.DAYS,
    onCountdownUnit: (CountdownUnit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val allowed = remember(permissionTick, preferences) { notificationsAllowed(context) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pendingState = rememberUpdatedState(pending)
    val refreshExactAlarm = rememberUpdatedState(onRefreshExactAlarmPermission)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionTick += 1
            pendingState.value?.invoke()
            pending = null
        }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionTick += 1
                    refreshExactAlarm.value()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var picking by remember { mutableStateOf<QuietPick?>(null) }
    var bandPicking by remember { mutableStateOf<BandPick?>(null) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var newPhrase by remember { mutableStateOf("") }

    fun enableThen(action: () -> Unit) {
        val needsRuntime =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsRuntime) {
            pending = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        Column(Modifier.padding(top = HomeMetrics.EyebrowTop)) {
            Text(
                "SETTINGS",
                style = HomeMetrics.Eyebrow,
                color = FocusColors.Live.Muted,
                modifier = Modifier.padding(bottom = HomeMetrics.EyebrowBottom),
            )
            Text(
                "Kept on this phone.",
                style = HomeMetrics.ScreenTitle,
                color = FocusColors.Live.Text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Tasks stay on this phone. There is no account.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SettingsCard {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            ChoicePills {
                for ((id, label) in themeChoices) {
                    PillButton(
                        stringResource(label),
                        selected = themeId == id,
                        onClick = { onThemeId(id) },
                    )
                }
            }
        }
        SettingsCard {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            ChoicePills {
                for (limit in MIN_TODAY_LIMIT..MAX_TODAY_LIMIT) {
                    PillButton(
                        label = limit.toString(),
                        selected = preferences.todayLimit == limit,
                        onClick = { onTodayLimit(limit) },
                    )
                }
            }
        }
        SettingsCard {
            Text(stringResource(R.string.settings_countdown), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_countdown_copy),
                style = MaterialTheme.typography.bodyMedium,
            )
            ChoicePills {
                for ((unit, label) in countdownUnitChoices) {
                    PillButton(
                        stringResource(label),
                        selected = countdownUnit == unit,
                        onClick = { onCountdownUnit(unit) },
                    )
                }
            }
        }
        if (showHomeLayout) {
            SettingsCard {
                Text(stringResource(R.string.settings_home_layout), style = MaterialTheme.typography.titleMedium)
                ChoicePills {
                    for ((layout, label) in homeLayoutChoices) {
                        PillButton(
                            stringResource(label),
                            selected = homeLayout == layout,
                            onClick = { onHomeLayout(layout) },
                        )
                    }
                }
            }
        }
        SettingsCard {
            Text("Repeats", style = MaterialTheme.typography.titleMedium)
            BandTimeRow("Morning", bandTimes.morningStartMinute) { bandPicking = BandPick.MORNING }
            BandTimeRow("Noon", bandTimes.noonStartMinute) { bandPicking = BandPick.NOON }
            BandTimeRow("Night", bandTimes.nightStartMinute) { bandPicking = BandPick.NIGHT }
            BandTimeRow("Night ends", bandTimes.nightEndMinute) { bandPicking = BandPick.NIGHT_END }
        }
        SettingsCard {
            Text("Nudges", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nudges can arrive a few minutes late.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ChoicePills {
                PillButton(
                    "Off",
                    selected = !preferences.nudgesEnabled,
                    onClick = { onNudgeSelected(false, preferences.nudgeMinutes) },
                )
                for ((label, minutes) in listOf("30 min" to 30, "1 h" to 60, "2 h" to 120)) {
                    PillButton(
                        label,
                        selected = preferences.nudgesEnabled && preferences.nudgeMinutes == minutes,
                        onClick = { enableThen { onNudgeSelected(true, minutes) } },
                    )
                }
            }
            TimeBoundRow(
                label = "Quiet from",
                value = preferences.quietStart?.takeIf { it.isNotBlank() } ?: "Not set",
                onClick = { picking = QuietPick.START },
            )
            TimeBoundRow(
                label = "Quiet until",
                value = preferences.quietEnd?.takeIf { it.isNotBlank() } ?: "Not set",
                onClick = { picking = QuietPick.END },
            )
            val quietHoursSet =
                !preferences.quietStart.isNullOrBlank() || !preferences.quietEnd.isNullOrBlank()
            if (quietHoursSet) {
                ChoicePills {
                    PillButton("Clear", onClick = { onQuietHours(null, null) })
                }
            }
        }
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Show current task", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A silent notice of what is open.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ThemeSwitch(
                    checked = preferences.currentNotificationEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            enableThen { onCurrentNotification(true) }
                        } else {
                            onCurrentNotification(false)
                        }
                    },
                )
            }
        }
        if (showExactAlarmPrompt) {
            SettingsCard(color = FocusColors.Live.SurfaceHigh) {
                Text("On-time reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Repeat reminders arrive on time when exact alarms are allowed. They can still land a few minutes late.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                QuietButton(
                    label = "Allow exact alarms",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                QuietButton(
                    label = "Not now",
                    onClick = onDismissExactAlarmPrompt,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if ((preferences.nudgesEnabled || preferences.currentNotificationEnabled) && !allowed) {
            SettingsCard(color = FocusColors.Live.SurfaceHigh) {
                Text(
                    "Notifications are off for Backpin. Home, capture, and recovery keep working.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                QuietButton(
                    label = "Notification settings",
                    onClick = {
                        val intent =
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SettingsCard {
            Text("Phrases", style = MaterialTheme.typography.titleMedium)
            Text(
                "A finished phrase is one tap. Angle brackets ask only for that word.",
                style = MaterialTheme.typography.bodyMedium,
            )
            preferences.savedPhrases.forEachIndexed { index, phrase ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text(
                        phrase,
                        style = MaterialTheme.typography.bodyLarge,
                        color = FocusColors.Live.Text,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                        PillButton("Rename", onClick = { renaming = index })
                        PillButton("Remove", onClick = { removing = index })
                    }
                }
            }
            OutlinedTextField(
                value = newPhrase,
                onValueChange = { newPhrase = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New phrase") },
                placeholder = { Text("Talk to <name>") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                KeyboardActions(
                    onDone = {
                        onAddPhrase(newPhrase)
                        if (newPhrase.isNotBlank()) newPhrase = ""
                    },
                ),
            )
            PillButton(
                label = "Add",
                onClick = {
                    onAddPhrase(newPhrase)
                    if (newPhrase.isNotBlank()) newPhrase = ""
                },
                enabled = newPhrase.isNotBlank(),
            )
        }
    }

    val renameIndex = renaming
    val renameText = renameIndex?.let { preferences.savedPhrases.getOrNull(it) }
    LaunchedEffect(renameIndex, renameText) {
        if (renameIndex != null && renameText == null) renaming = null
    }
    if (renameIndex != null && renameText != null) {
        RenamePhraseDialog(
            initial = renameText,
            onDismiss = { renaming = null },
            onConfirm = { value ->
                onRenamePhrase(renameIndex, value)
                renaming = null
            },
        )
    }

    val removeIndex = removing
    val removeText = removeIndex?.let { preferences.savedPhrases.getOrNull(it) }
    LaunchedEffect(removeIndex, removeText) {
        if (removeIndex != null && removeText == null) removing = null
    }
    if (removeIndex != null && removeText != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = FocusColors.Live.Surface,
            titleContentColor = FocusColors.Live.Text,
            title = { Text("Remove this phrase?") },
            text = {
                Text(removeText, style = MaterialTheme.typography.bodyLarge)
            },
            confirmButton = {
                ActionRow {
                    QuietButton("Cancel", onClick = { removing = null })
                    QuietButton(
                        "Remove",
                        onClick = {
                            onRemovePhrase(removeIndex)
                            removing = null
                        },
                    )
                }
            },
        )
    }

    bandPicking?.let { which ->
        val initialMinute =
            when (which) {
                BandPick.MORNING -> bandTimes.morningStartMinute
                BandPick.NOON -> bandTimes.noonStartMinute
                BandPick.NIGHT -> bandTimes.nightStartMinute
                BandPick.NIGHT_END -> bandTimes.nightEndMinute
            }
        QuietTimeDialog(
            title =
            when (which) {
                BandPick.MORNING -> "Morning starts"
                BandPick.NOON -> "Noon starts"
                BandPick.NIGHT -> "Night starts"
                BandPick.NIGHT_END -> "Night ends"
            },
            initial = minuteOfDayClock(initialMinute),
            onDismiss = { bandPicking = null },
            onConfirm = { value ->
                val minute = parseClockToMinute(value)
                if (minute != null) {
                    val edge =
                        when (which) {
                            BandPick.MORNING -> BandEdge.MORNING
                            BandPick.NOON -> BandEdge.NOON
                            BandPick.NIGHT -> BandEdge.NIGHT
                            BandPick.NIGHT_END -> BandEdge.NIGHT_END
                        }
                    onBandTime(edge, minute)
                }
                bandPicking = null
            },
        )
    }

    picking?.let { which ->
        val initial = if (which == QuietPick.START) preferences.quietStart else preferences.quietEnd
        QuietTimeDialog(
            title = if (which == QuietPick.START) "Quiet hours start" else "Quiet hours end",
            initial = initial,
            onDismiss = { picking = null },
            onConfirm = { value ->
                val start = if (which == QuietPick.START) value else preferences.quietStart
                val end = if (which == QuietPick.END) value else preferences.quietEnd
                if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                    onQuietHours(start, end)
                } else if (which == QuietPick.START) {
                    onQuietHours(value, end ?: "07:00")
                } else {
                    onQuietHours(start ?: "22:00", value)
                }
                picking = null
            },
        )
    }
}

@Composable
private fun QuietTimeDialog(
    title: String,
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parsed = initial?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    var hour by remember(initial) { mutableIntStateOf(parsed?.hour ?: 22) }
    var minute by remember(initial) { mutableIntStateOf(parsed?.minute ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Live.Surface,
        titleContentColor = FocusColors.Live.Text,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            ) {
                SnapWheel(
                    count = 24,
                    selectedIndex = hour,
                    onSelectedIndex = { hour = it },
                    itemLabel = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                    outlineColor = FocusColors.Live.Now,
                    description = "Hour",
                )
                SnapWheel(
                    count = 60,
                    selectedIndex = minute,
                    onSelectedIndex = { minute = it },
                    itemLabel = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                    outlineColor = FocusColors.Live.Now,
                    description = "Minute",
                )
            }
        },
        confirmButton = {
            ActionRow {
                QuietButton("Cancel", onClick = onDismiss)
                QuietButton(
                    "Save",
                    onClick = { onConfirm("%02d:%02d".format(hour, minute)) },
                )
            }
        },
    )
}

@Composable
private fun RenamePhraseDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Live.Surface,
        titleContentColor = FocusColors.Live.Text,
        title = { Text("Rename phrase") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phrase") },
                singleLine = true,
            )
        },
        confirmButton = {
            ActionRow {
                QuietButton("Cancel", onClick = onDismiss)
                QuietButton("Save", onClick = { onConfirm(draft) }, enabled = draft.isNotBlank())
            }
        },
    )
}

@Composable
private fun SettingsCard(
    color: Color = FocusColors.Live.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ButtonRadius),
        color = color,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(Space),
            content = content,
        )
    }
}

@Composable
private fun BandTimeRow(
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
) {
    TimeBoundRow(label = label, value = minuteOfDayClock(minuteOfDay), onClick = onClick)
}

@Composable
private fun TimeBoundRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        PillButton(label = value, onClick = onClick)
    }
}

private val themeChoices =
    listOf(
        ThemeId.SIGNAL to R.string.theme_signal,
        ThemeId.STILL_WATER to R.string.theme_still_water,
        ThemeId.EMBER to R.string.theme_ember,
        ThemeId.CLEAR_LINE to R.string.theme_clear_line,
    )

private val homeLayoutChoices =
    listOf(
        HomeLayout.FLAT to R.string.home_layout_flat,
        HomeLayout.BANDS to R.string.home_layout_bands,
    )

private val countdownUnitChoices =
    listOf(
        CountdownUnit.DAYS to R.string.settings_countdown_days,
        CountdownUnit.MONTHS to R.string.settings_countdown_months,
    )

private enum class QuietPick { START, END }

private enum class BandPick { MORNING, NOON, NIGHT, NIGHT_END }
