package app.backpin.focus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Space * 2),
    ) {
        Text("Settings", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Kept on this phone.",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Tasks, return cues, and the interruption stack stay on this device. There is no account.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Text("Stored preferences", style = MaterialTheme.typography.titleMedium)
                Text(
                    "These values are saved for later. This version does not schedule nudges or post notifications.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PreferenceLine("Nudges", if (preferences.nudgesEnabled) "On" else "Off")
                PreferenceLine("Nudge interval", "${preferences.nudgeMinutes} min")
                PreferenceLine("Quiet hours", quietHours(preferences))
                PreferenceLine(
                    "Current-task notification",
                    if (preferences.currentNotificationEnabled) "On" else "Off",
                )
            }
        }
    }
}

@Composable
private fun PreferenceLine(
    label: String,
    value: String,
) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = FocusColors.Muted)
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

private fun quietHours(preferences: UserPreferences): String {
    val start = preferences.quietStart
    val end = preferences.quietEnd
    return if (start.isNullOrBlank() || end.isNullOrBlank()) "Not set" else "$start – $end"
}
