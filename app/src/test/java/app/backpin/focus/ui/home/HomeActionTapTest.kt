package app.backpin.focus.ui.home

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeActionTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachVisibleCenterReachesItsOwnActionAndNotTheNeighbor() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "home-tap-${UUID.randomUUID()}"),
                    clock = AppClock { System.currentTimeMillis() },
                ),
            )
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            runBlocking {
                todayTask(useCases, "Hold this")
                useCases.start(idOf(useCases, "Hold this"))
                todayTask(useCases, "Park me")
                todayTask(useCases, "Finish me")
            }
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    HomeScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenFocus = {},
                        onOpenInbox = {},
                        onOpenLater = {},
                        onOpenScheduled = {},
                        onOpenDone = {},
                    )
                }
            }
            await { viewModel.uiState.value.now?.title == "Hold this" && viewModel.uiState.value.todaySlots.count { it.task != null } == 3 }

            listOf(nowCard("Done"), nowCard("Interrupt"), nowCard("Later")).forEach(::assertTall)
            listOf(inRow("Start", "Park me"), inRow("Done", "Park me"), inRow("Later", "Park me")).forEach(::assertTall)

            tap(nowCard("Interrupt"))
            await { viewModel.uiState.value.captureOpen && viewModel.uiState.value.captureInterrupts }
            assertSnapshot(useCases) { activeTitle(it) == "Hold this" && open(it, "Park me") && open(it, "Finish me") }
            viewModel.dismissCapture()
            await { !viewModel.uiState.value.captureOpen }

            tap(inRow("Later", "Park me"))
            awaitSnapshot(useCases) { task(it, "Park me").bucket == TaskBucket.LATER }
            assertSnapshot(useCases) { activeTitle(it) == "Hold this" && open(it, "Finish me") }
            awaitToday(viewModel, "Hold this", "Finish me")

            tap(inRow("Done", "Finish me"))
            awaitSnapshot(useCases) { task(it, "Finish me").status == TaskStatus.DONE }
            assertSnapshot(useCases) { activeTitle(it) == "Hold this" && task(it, "Park me").bucket == TaskBucket.LATER }

            runBlocking { todayTask(useCases, "Start me") }
            awaitToday(viewModel, "Hold this", "Start me")
            tap(inRow("Start", "Start me"))
            awaitSnapshot(useCases) { activeTitle(it) == "Start me" }
            assertSnapshot(useCases) { task(it, "Hold this").status == TaskStatus.OPEN && task(it, "Start me").bucket == TaskBucket.TODAY }

            await { viewModel.uiState.value.now?.title == "Start me" }
            tap(nowCard("Later"))
            awaitSnapshot(useCases) { task(it, "Start me").bucket == TaskBucket.LATER }
            assertSnapshot(useCases) { activeTitle(it) != "Start me" && task(it, "Hold this").status == TaskStatus.OPEN }

            runBlocking { useCases.start(idOf(useCases, "Hold this")) }
            await { viewModel.uiState.value.now?.title == "Hold this" }
            awaitToday(viewModel, "Hold this")
            tap(nowCard("Done"))
            awaitSnapshot(useCases) { task(it, "Hold this").status == TaskStatus.DONE }
            assertSnapshot(useCases) { task(it, "Start me").status == TaskStatus.OPEN }
        } finally {
            store.clear()
            database.close()
        }
    }

    /** The NOW card sits above Today, so its button is the topmost one with that label. */
    private fun nowCard(label: String): SemanticsNodeInteraction {
        val nodes = compose.onAllNodesWithText(label)
        val found = nodes.fetchSemanticsNodes()
        return nodes[found.indices.minBy { found[it].boundsInRoot.top }]
    }

    /** The button with [label] closest below the Today row titled [title]. */
    private fun inRow(
        label: String,
        title: String,
    ): SemanticsNodeInteraction {
        val titleTop = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        val nodes = compose.onAllNodesWithText(label)
        val found = nodes.fetchSemanticsNodes()
        val below = found.indices.filter { found[it].boundsInRoot.top > titleTop }
        check(below.isNotEmpty()) { "No $label below $title" }
        return nodes[below.minBy { found[it].boundsInRoot.top }]
    }

    private fun assertTall(node: SemanticsNodeInteraction) {
        val bounds = node.getBoundsInRoot()
        assertTrue("Button is ${bounds.bottom - bounds.top} tall", bounds.bottom - bounds.top >= 56.dp - 0.5.dp)
    }

    private fun tap(node: SemanticsNodeInteraction) {
        compose.waitForIdle()
        node.performScrollTo()
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        compose.waitForIdle()
    }

    private suspend fun todayTask(
        useCases: FocusUseCases,
        title: String,
    ) {
        useCases.capture(title)
        useCases.promote(idOf(useCases, title))
    }

    private suspend fun idOf(
        useCases: FocusUseCases,
        title: String,
    ): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id

    private fun task(
        snapshot: FocusSnapshot,
        title: String,
    ) = snapshot.tasks.single { it.title == title }

    private fun open(
        snapshot: FocusSnapshot,
        title: String,
    ): Boolean = task(snapshot, title).let { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }

    private fun activeTitle(snapshot: FocusSnapshot): String? {
        val active = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE } ?: return null
        return snapshot.tasks.singleOrNull { it.id == active.taskId }?.title
    }

    /** Today shows exactly these rows, so button lookups by position are not stale. */
    private fun awaitToday(
        viewModel: FocusStackViewModel,
        vararg titles: String,
    ) {
        await { viewModel.uiState.value.todaySlots.mapNotNull { it.task?.title } == titles.toList() }
        compose.waitForIdle()
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun awaitSnapshot(
        useCases: FocusUseCases,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        await { runBlocking { predicate(useCases.observeSnapshot().first()) } }
    }

    private fun assertSnapshot(
        useCases: FocusUseCases,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        assertTrue(runBlocking { predicate(useCases.observeSnapshot().first()) })
    }
}
