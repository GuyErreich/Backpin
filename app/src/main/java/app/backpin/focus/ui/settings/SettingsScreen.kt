package app.backpin.focus.ui.settings

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.repeat.minuteOfDayClock
import app.backpin.focus.ui.repeat.parseClockToMinute
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
    onBandTimes: (BandTimes) -> Unit = {},
    canScheduleExactAlarms: () -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val allowed = remember(permissionTick, preferences) { notificationsAllowed(context) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pendingState = rememberUpdatedState(pending)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionTick += 1
            pendingState.value?.invoke()
            pending = null
        }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) permissionTick += 1
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var picking by remember { mutableStateOf<QuietPick?>(null) }
    var bandPicking by remember { mutableStateOf<BandPick?>(null) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    var newPhrase by remember { mutableStateOf("") }
    val exactPrefs = remember { context.getSharedPreferences(EXACT_ALARM_PREFS, Context.MODE_PRIVATE) }
    var exactPromptDismissed by remember {
        mutableStateOf(exactPrefs.getBoolean(EXACT_ALARM_DISMISSED_KEY, false))
    }
    val exactAllowed = remember(permissionTick) { canScheduleExactAlarms() }

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
            color = FocusColors.Muted,
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
            color = FocusColors.Surface,
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
            color = FocusColors.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text("Repeats", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Morning, noon, and night start times. A reminder uses the start of that band. Night ends when you say it does.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val times = preferences.bandTimes
                BandTimeRow("Morning", times.morningStartMinute) { bandPicking = BandPick.MORNING }
                BandTimeRow("Noon", times.noonStartMinute) { bandPicking = BandPick.NOON }
                BandTimeRow("Night", times.nightStartMinute) { bandPicking = BandPick.NIGHT }
                BandTimeRow("Night ends", times.nightEndMinute) { bandPicking = BandPick.NIGHT_END }
                if (!times.isValid()) {
                    Text(
                        "Morning has to start before noon, then night, then night end.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (!exactAllowed && !exactPromptDismissed) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.SurfaceHigh,
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
                        onClick = {
                            exactPrefs.edit().putBoolean(EXACT_ALARM_DISMISSED_KEY, true).apply()
                            exactPromptDismissed = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
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
                            color = FocusColors.Text,
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
            color = FocusColors.Surface,
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
            color = FocusColors.Surface,
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
                color = FocusColors.SurfaceHigh,
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
        val times = preferences.bandTimes
        val initialMinute =
            when (which) {
                BandPick.MORNING -> times.morningStartMinute
                BandPick.NOON -> times.noonStartMinute
                BandPick.NIGHT -> times.nightStartMinute
                BandPick.NIGHT_END -> times.nightEndMinute
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
                    val next =
                        when (which) {
                            BandPick.MORNING -> times.copy(morningStartMinute = minute)
                            BandPick.NOON -> times.copy(noonStartMinute = minute)
                            BandPick.NIGHT -> times.copy(nightStartMinute = minute)
                            BandPick.NIGHT_END -> times.copy(nightEndMinute = minute)
                        }
                    if (next.isValid()) onBandTimes(next)
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
        containerColor = FocusColors.Surface,
        titleContentColor = FocusColors.Text,
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
        containerColor = FocusColors.Surface,
        titleContentColor = FocusColors.Text,
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

private enum class QuietPick { START, END }

private enum class BandPick { MORNING, NOON, NIGHT, NIGHT_END }

private const val EXACT_ALARM_PREFS: String = "backpin_repeat_ui"
private const val EXACT_ALARM_DISMISSED_KEY: String = "exact_alarm_prompt_dismissed"

private fun quietHours(preferences: UserPreferences): String {
    val start = preferences.quietStart
    val end = preferences.quietEnd
    return if (start.isNullOrBlank() || end.isNullOrBlank()) "Not set" else "$start – $end"
}
