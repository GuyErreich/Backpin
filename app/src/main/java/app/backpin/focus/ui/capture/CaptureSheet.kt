package app.backpin.focus.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import app.backpin.focus.ui.home.MintButton
import app.backpin.focus.ui.home.QuietButton
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(
    onDismiss: () -> Unit,
    onSaveToInbox: (String) -> Unit,
    onInterruptNow: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FocusColors.Surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Space),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Get it out of your head.",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close capture")
                }
            }
            Text(
                "One line. It goes to Inbox and your place stays put.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                label = { Text("Title") },
                placeholder = { Text("A stray thought") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = { onSaveToInbox(title) },
                    ),
            )
            MintButton("Save to Inbox", onClick = { onSaveToInbox(title) })
            QuietButton("Interrupt now", onClick = { onInterruptNow(title) })
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
