package app.backpin.focus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.backpin.focus.notifications.NotificationContract
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.home.MintButton
import app.backpin.focus.ui.settings.SettingsScreen
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private object Routes {
    const val HOME = "home"
    const val FOCUS = "focus"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusApp(
    viewModel: FocusStackViewModel,
    launchDestination: String? = null,
    onLaunchDestinationConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val showCapture = route == Routes.HOME || route == Routes.FOCUS

    LaunchedEffect(launchDestination) {
        when (launchDestination) {
            NotificationContract.DESTINATION_FOCUS -> navController.navigateTab(Routes.FOCUS)
            NotificationContract.DESTINATION_CAPTURE -> {
                navController.navigateTab(Routes.FOCUS)
                viewModel.openCapture()
            }
        }
        if (launchDestination != null) onLaunchDestinationConsumed()
    }

    LaunchedEffect(state.undo?.id) {
        val undo = state.undo ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = undo.message,
                actionLabel = "Undo",
                withDismissAction = true,
            )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undo(undo.id)
        } else {
            viewModel.dismissUndo(undo.id)
        }
    }

    Scaffold(
        containerColor = FocusColors.Midnight,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = FocusColors.SurfaceHigh,
                    contentColor = FocusColors.Text,
                    actionColor = FocusColors.Mint,
                    dismissActionContentColor = FocusColors.Muted,
                )
            }
        },
        bottomBar = {
            Column {
                if (showCapture) {
                    MintButton(
                        label = "+ Capture",
                        onClick = viewModel::openCapture,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = Space)
                                .heightIn(min = 48.dp),
                    )
                }
                NavigationBar(containerColor = FocusColors.Surface) {
                    NavigationBarItem(
                        selected = route == Routes.HOME,
                        onClick = { navController.navigateTab(Routes.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.FOCUS,
                        onClick = { navController.navigateTab(Routes.FOCUS) },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        label = { Text("Focus") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.SETTINGS,
                        onClick = { navController.navigateTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenFocus = { navController.navigateTab(Routes.FOCUS) },
                )
            }
            composable(Routes.FOCUS) {
                FocusScreen(state = state, viewModel = viewModel)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = state.preferences,
                    onNudgeSelected = viewModel::setNudge,
                    onQuietHours = viewModel::setQuietHours,
                    onCurrentNotification = viewModel::setCurrentNotification,
                )
            }
        }
    }

    if (state.captureOpen) {
        CaptureSheet(
            onDismiss = viewModel::dismissCapture,
            onSaveToInbox = viewModel::saveToInbox,
            onInterruptNow = viewModel::interruptNow,
        )
    }

    state.swapIncoming?.let { incoming ->
        AlertDialog(
            onDismissRequest = viewModel::cancelSwap,
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.Text,
            textContentColor = FocusColors.Muted,
            title = { Text("Today is full") },
            text = {
                Column {
                    Text("Move one Today task to Later so “${incoming.title}” can take its place. Cancel leaves Today as it is.")
                }
            },
            confirmButton = {
                Column {
                    state.swapChoices.forEach { choice ->
                        TextButton(
                            onClick = { viewModel.confirmSwap(choice.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Move “${choice.title}” to Later")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelSwap, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }

    if (state.switchOpen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSwitch,
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.Text,
            textContentColor = FocusColors.Muted,
            title = { Text("Switch") },
            text = {
                Text("Pauses Now and starts another Today task. The return cue stays on the paused task. This is not an interruption.")
            },
            confirmButton = {
                Column {
                    if (state.switchTargets.isEmpty()) {
                        Text("Nothing else is in Today.", color = FocusColors.Muted)
                    }
                    state.switchTargets.forEach { target ->
                        TextButton(
                            onClick = { viewModel.switchTo(target.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(target.title)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSwitch, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
