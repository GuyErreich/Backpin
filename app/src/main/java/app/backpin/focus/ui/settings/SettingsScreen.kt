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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
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
        verticalArrangement = Arrangement.spacedBy(Space * 2),
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.labelLarge,
            color = FocusColors.Live.Muted,
        )
        Text(
            "Kept on this phone.",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Tasks and the interruption stack stay on this device. There is no account. Nudges are approximate: Android may delay them. Repeat reminders use exact alarms when you allow them, and otherwise land a few minutes late.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                ActionRow {
                    for ((id, label) in themeChoices) {
                        QuietButton(
                            stringResource(label),
                            selected = themeId == id,
                            onClick = { onThemeId(id) },
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text(stringResource(R.string.settings_countdown), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_countdown_copy),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionRow {
                    for ((unit, label) in countdownUnitChoices) {
                        QuietButton(
                            stringResource(label),
                            selected = countdownUnit == unit,
                            onClick = { onCountdownUnit(unit) },
                        )
                    }
                }
            }
        }
        if (showHomeLayout) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Live.Surface,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text(stringResource(R.string.settings_home_layout), style = MaterialTheme.typography.titleMedium)
                    ActionRow {
                        for ((layout, label) in homeLayoutChoices) {
                            QuietButton(
                                stringResource(label),
                                selected = homeLayout == layout,
                                onClick = { onHomeLayout(layout) },
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Text(
                    "How many open tasks Today can hold. Empty places stay empty until you add a task. A smaller number parks the extra Today tasks in Later. The task in focus stays when it still fits.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionRow {
                    for (limit in MIN_TODAY_LIMIT..MAX_TODAY_LIMIT) {
                        QuietButton(
                            label = limit.toString(),
                            selected = preferences.todayLimit == limit,
                            onClick = { onTodayLimit(limit) },
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text("Repeats", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Morning, noon, and night start times. A reminder uses the start of that band. Night ends when you say it does.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BandTimeRow("Morning", bandTimes.morningStartMinute) { bandPicking = BandPick.MORNING }
                BandTimeRow("Noon", bandTimes.noonStartMinute) { bandPicking = BandPick.NOON }
                BandTimeRow("Night", bandTimes.nightStartMinute) { bandPicking = BandPick.NIGHT }
                BandTimeRow("Night ends", bandTimes.nightEndMinute) { bandPicking = BandPick.NIGHT_END }
            }
        }
        if (showExactAlarmPrompt) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Live.SurfaceHigh,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text("On-time reminders", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Repeat reminders arrive on time when exact alarms are allowed. If you skip this, they can still land a few minutes late. Home, capture, and check-off keep working either way.",
                        style = MaterialTheme.typography.bodyLarge,
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
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
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
                        ActionRow {
                            QuietButton("Rename", onClick = { renaming = index })
                            QuietButton("Remove", onClick = { onRemovePhrase(index) })
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
                QuietButton(
                    label = "Add",
                    onClick = {
                        onAddPhrase(newPhrase)
                        if (newPhrase.isNotBlank()) newPhrase = ""
                    },
                    enabled = newPhrase.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text("Nudges", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Off, or about every 30 minutes, 1 hour, or 2 hours. Quiet hours are checked when a nudge is about to show.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionRow {
                    QuietButton(
                        "Off",
                        selected = !preferences.nudgesEnabled,
                        onClick = { onNudgeSelected(false, preferences.nudgeMinutes) },
                    )
                    for ((label, minutes) in listOf("30 min" to 30, "1 h" to 60, "2 h" to 120)) {
                        QuietButton(
                            label,
                            selected = preferences.nudgesEnabled && preferences.nudgeMinutes == minutes,
                            onClick = { enableThen { onNudgeSelected(true, minutes) } },
                        )
                    }
                }
                Text("Quiet hours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space))
                Text(quietHours(preferences), style = MaterialTheme.typography.bodyLarge)
                ActionRow {
                    QuietButton("Start", onClick = { picking = QuietPick.START })
                    QuietButton("End", onClick = { picking = QuietPick.END })
                    QuietButton("Clear", onClick = { onQuietHours(null, null) })
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.Surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Show current task", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "An ongoing notice while Today has an open task, including when nothing is started. With nothing in focus the title is Today and the collapsed line says how many tasks are open. Expanded, each task is its own row. While a task is current or waiting, it names that task. Interrupt is on that notice only while a task is in focus. A small status icon can show. Silent: no sound or vibration. Elapsed time stays in the app. No foreground service.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = preferences.currentNotificationEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            enableThen { onCurrentNotification(true) }
                        } else {
                            onCurrentNotification(false)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if ((preferences.nudgesEnabled || preferences.currentNotificationEnabled) && !allowed) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Live.SurfaceHigh,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimeDialog(
    title: String,
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parsed = initial?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val state =
        rememberTimePickerState(
            initialHour = parsed?.hour ?: 22,
            initialMinute = parsed?.minute ?: 0,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Live.Surface,
        titleContentColor = FocusColors.Live.Text,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            ActionRow {
                QuietButton("Cancel", onClick = onDismiss)
                QuietButton("Save", onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) })
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
private fun BandTimeRow(
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        QuietButton(label = minuteOfDayClock(minuteOfDay), onClick = onClick)
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

private fun quietHours(preferences: UserPreferences): String {
    val start = preferences.quietStart
    val end = preferences.quietEnd
    return if (start.isNullOrBlank() || end.isNullOrBlank()) "Not set" else "$start – $end"
}
