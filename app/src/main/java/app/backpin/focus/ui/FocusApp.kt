// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.notifications.NotificationContract
import app.backpin.focus.ui.capture.CaptureEdit
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.countdown.CountdownScreen
import app.backpin.focus.ui.repeat.RepeatSheet
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.home.DoneScreen
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.home.InboxScreen
import app.backpin.focus.ui.home.LaterScreen
import app.backpin.focus.ui.home.ScheduledScreen
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.pileEnter
import app.backpin.focus.ui.motion.pileExit
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.settings.SettingsScreen
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private object Routes {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val LATER = "later"
    const val SCHEDULED = "scheduled"
    const val DONE = "done"
    const val FOCUS = "focus"
    const val COUNTDOWN = "countdown"
    const val SETTINGS = "settings"
}

private val homeRoutes = setOf(Routes.HOME, Routes.INBOX, Routes.LATER, Routes.SCHEDULED, Routes.DONE)
private val pileRoutes = setOf(Routes.INBOX, Routes.LATER, Routes.SCHEDULED, Routes.DONE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusApp(
    viewModel: FocusStackViewModel,
    launchDestination: String? = null,
    onLaunchDestinationConsumed: () -> Unit = {},
    themeId: ThemeId = ThemeId.SIGNAL,
    onThemeId: (ThemeId) -> Unit = {},
    homeLayout: HomeLayout = HomeLayout.FLAT,
    onHomeLayout: (HomeLayout) -> Unit = {},
    showHomeLayout: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checkedOffMessage = stringResource(R.string.undo_checked_off)
    val repeatDraft by viewModel.editingRepeat.collectAsStateWithLifecycle()
    val bandTimes by viewModel.bandTimes.collectAsStateWithLifecycle()
    val showExactAlarmPrompt by viewModel.showExactAlarmPrompt.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val showCapture = route == Routes.HOME || route == Routes.FOCUS
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(launchDestination) {
        when (launchDestination) {
            NotificationContract.DESTINATION_FOCUS -> navController.navigateTab(Routes.FOCUS)
            NotificationContract.DESTINATION_CAPTURE -> {
                navController.navigateTab(Routes.FOCUS)
                viewModel.openCapture(interrupts = true)
            }
        }
        if (launchDestination != null) onLaunchDestinationConsumed()
    }

    LaunchedEffect(state.undo?.id) {
        val undo = state.undo ?: return@LaunchedEffect
        val message = undoSnackbarMessage(undo, checkedOffMessage)
        val result =
            snackbarHostState.showSnackbar(
                message = message,
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
        containerColor = FocusColors.Live.Midnight,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = FocusColors.Live.SurfaceHigh,
                    contentColor = FocusColors.Live.Text,
                    actionColor = FocusColors.Live.Mint,
                    dismissActionContentColor = FocusColors.Live.Muted,
                )
            }
        },
        bottomBar = {
            Column {
                if (showCapture) {
                    AccentButton(
                        label = "+ Capture",
                        tone = AccentTone.Mint,
                        onClick = { viewModel.openCapture(interrupts = false) },
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = Space),
                    )
                }
                NavigationBar(containerColor = FocusColors.Live.Surface) {
                    NavigationBarItem(
                        selected = route in homeRoutes,
                        onClick = { navController.showToday() },
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
                        selected = route == Routes.COUNTDOWN,
                        onClick = { navController.navigateTab(Routes.COUNTDOWN) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("Countdown") },
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
            composable(
                Routes.HOME,
                exitTransition = {
                    if (targetState.destination.route in pileRoutes) {
                        pileExit(forward = true, reducedMotion = reducedMotion)
                    } else {
                        null
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route in pileRoutes) {
                        pileEnter(forward = false, reducedMotion = reducedMotion)
                    } else {
                        null
                    }
                },
            ) {
                HomeScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenFocus = { navController.navigateTab(Routes.FOCUS) },
                    onOpenInbox = { navController.openPile(Routes.INBOX) },
                    onOpenLater = { navController.openPile(Routes.LATER) },
                    onOpenScheduled = { navController.openPile(Routes.SCHEDULED) },
                    onOpenDone = { navController.openPile(Routes.DONE) },
                    homeLayout = homeLayout,
                )
            }
            composable(
                Routes.INBOX,
                enterTransition = { homePileEnter(reducedMotion) },
                popExitTransition = { homePileExit(reducedMotion) },
            ) {
                InboxScreen(
                    tasks = state.inboxTasks,
                    ready = state.ready,
                    canInterrupt = state.canInterrupt,
                    onBack = { navController.popBackStack() },
                    onToday = viewModel::promote,
                    onStart = viewModel::start,
                    onInterrupt = viewModel::interruptFromPile,
                    onEdit = viewModel::openEdit,
                )
            }
            composable(
                Routes.LATER,
                enterTransition = { homePileEnter(reducedMotion) },
                popExitTransition = { homePileExit(reducedMotion) },
            ) {
                LaterScreen(
                    tasks = state.laterTasks,
                    ready = state.ready,
                    canInterrupt = state.canInterrupt,
                    onBack = { navController.popBackStack() },
                    onToday = viewModel::promote,
                    onStart = viewModel::start,
                    onInterrupt = viewModel::interruptFromPile,
                    onEdit = viewModel::openEdit,
                )
            }
            composable(
                Routes.SCHEDULED,
                enterTransition = { homePileEnter(reducedMotion) },
                popExitTransition = { homePileExit(reducedMotion) },
            ) {
                ScheduledScreen(
                    tasks = state.scheduledTasks,
                    ready = state.ready,
                    canInterrupt = state.canInterrupt,
                    onBack = { navController.popBackStack() },
                    onToday = viewModel::promote,
                    onStart = viewModel::start,
                    onInterrupt = viewModel::interruptFromPile,
                    onInbox = viewModel::moveToInbox,
                    onLater = viewModel::demote,
                    onRemove = viewModel::removeScheduled,
                    onChangeDate = viewModel::changeScheduledDate,
                    onChangeTime = viewModel::changeScheduledTime,
                    onEdit = viewModel::openEdit,
                )
            }
            composable(
                Routes.DONE,
                enterTransition = { homePileEnter(reducedMotion) },
                popExitTransition = { homePileExit(reducedMotion) },
            ) {
                DoneScreen(
                    tasks = state.archiveTasks,
                    ready = state.ready,
                    onBack = { navController.popBackStack() },
                    onRestore = viewModel::restore,
                )
            }
            composable(Routes.FOCUS) {
                FocusScreen(state = state, viewModel = viewModel)
            }
            composable(Routes.COUNTDOWN) {
                CountdownScreen(
                    countdowns = state.countdowns,
                    ready = state.ready,
                    onAdd = viewModel::addCountdown,
                    onChangeDate = viewModel::changeCountdownDate,
                    onRemove = viewModel::removeCountdown,
                    onEdit = viewModel::openEdit,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = state.preferences,
                    onNudgeSelected = viewModel::setNudge,
                    onQuietHours = viewModel::setQuietHours,
                    onCurrentNotification = viewModel::setCurrentNotification,
                    onTodayLimit = viewModel::setTodayLimit,
                    onAddPhrase = viewModel::addSavedPhrase,
                    onRenamePhrase = viewModel::renameSavedPhrase,
                    onRemovePhrase = viewModel::removeSavedPhrase,
                    bandTimes = bandTimes,
                    onBandTime = viewModel::setBandTime,
                    showExactAlarmPrompt = showExactAlarmPrompt,
                    onDismissExactAlarmPrompt = viewModel::dismissExactAlarmPrompt,
                    onRefreshExactAlarmPermission = viewModel::refreshExactAlarmPermission,
                    themeId = themeId,
                    onThemeId = onThemeId,
                    homeLayout = homeLayout,
                    onHomeLayout = onHomeLayout,
                    showHomeLayout = showHomeLayout,
                )
            }
        }
    }

    val editingRepeat = repeatDraft
    val editing = state.editing
    if (editingRepeat != null) {
        RepeatSheet(
            draft = editingRepeat,
            onCommit = viewModel::commitRepeatDraft,
            onDismiss = viewModel::dismissRepeatSheet,
            onArchive = viewModel::archiveRepeat,
        )
    } else if (editing != null) {
        CaptureSheet(
            phrases = emptyList(),
            frameActive = state.now != null,
            onDismiss = viewModel::dismissEdit,
            onSaveToInbox = {},
            onInterruptNow = {},
            onPlaceToday = {},
            onPlaceStart = {},
            onPlaceLater = {},
            onSchedule = { _, _, _ -> },
            edit =
            CaptureEdit(
                title = editing.title,
                place = editing.place,
                scheduledEpochDay = editing.scheduledOnEpochDay,
            ),
            onCommitEdit = viewModel::saveEdit,
        )
    } else if (state.captureOpen) {
        CaptureSheet(
            phrases = state.preferences.savedPhrases,
            frameActive = state.now != null,
            onDismiss = viewModel::dismissCapture,
            onSaveToInbox = viewModel::saveToInbox,
            onInterruptNow = viewModel::interruptNow,
            onPlaceToday = viewModel::placeOnToday,
            onPlaceStart = viewModel::placeAndStart,
            onPlaceLater = viewModel::placeLater,
            onSchedule = viewModel::placeScheduled,
            onRepeat = viewModel::openRepeatCreate,
        )
    }

    val swapTitle = state.swapIncoming?.title ?: state.freshSwapTitle
    if (swapTitle != null) {
        val canInterrupt =
            state.now != null && state.todaySlots.any { it.task?.id == state.now?.taskId }
        AlertDialog(
            onDismissRequest = viewModel::cancelSwap,
            containerColor = FocusColors.Live.Surface,
            titleContentColor = FocusColors.Live.Text,
            textContentColor = FocusColors.Live.Muted,
            title = { Text("Today is full") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text(
                        if (canInterrupt) {
                            "“$swapTitle” needs a Today slot. Interrupt the current task, or replace one. Replacing parks that task in Later and does not start the new one."
                        } else {
                            "“$swapTitle” needs a Today slot. Replace one Today task. That task moves to Later, and the new one takes its place without starting."
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
                        if (canInterrupt) {
                            AccentButton(
                                label = "Interrupt current task",
                                tone = AccentTone.Lavender,
                                onClick = viewModel::confirmInterrupt,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        state.swapChoices.forEach { choice ->
                            QuietButton(
                                label = "Replace “${choice.title}”",
                                onClick = { viewModel.confirmReplace(choice.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                QuietButton("Cancel", onClick = viewModel::cancelSwap, modifier = Modifier.fillMaxWidth())
            },
        )
    }

    if (state.switchOpen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSwitch,
            containerColor = FocusColors.Live.Surface,
            titleContentColor = FocusColors.Live.Text,
            textContentColor = FocusColors.Live.Muted,
            title = { Text("Switch") },
            text = {
                Text("Pauses Now and starts another Today task. The return cue stays on the paused task. This is not an interruption.")
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
                    if (state.switchTargets.isEmpty()) {
                        Text("Nothing else is in Today.", color = FocusColors.Live.Muted)
                    }
                    state.switchTargets.forEach { target ->
                        QuietButton(
                            label = target.title,
                            onClick = { viewModel.switchTo(target.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    QuietButton("Cancel", onClick = viewModel::dismissSwitch, modifier = Modifier.fillMaxWidth())
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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.homePileEnter(
    reducedMotion: Boolean,
): EnterTransition? = if (initialState.destination.route == Routes.HOME) pileEnter(forward = true, reducedMotion) else null

private fun AnimatedContentTransitionScope<NavBackStackEntry>.homePileExit(
    reducedMotion: Boolean,
): ExitTransition? = if (targetState.destination.route == Routes.HOME) pileExit(forward = false, reducedMotion) else null

/** Push a pile above Today. Task actions on that screen do not call this. */
private fun androidx.navigation.NavController.openPile(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** Home is Today. Pile screens stay off this tab until the user opens one again. */
private fun androidx.navigation.NavController.showToday() {
    navigate(Routes.HOME) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
