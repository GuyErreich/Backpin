// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.components.APP_TAB_BAR_TAG
import app.backpin.focus.ui.components.AppTab
import app.backpin.focus.ui.components.AppTabBar
import app.backpin.focus.ui.components.AppTabBarHeight
import app.backpin.focus.ui.components.TAB_BADGE_TAG
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppTabBarQaTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tabBarMatchesPenpotHeightAndHomeMetrics() {
        assertEquals(AppTabBarHeight, HomeMetrics.TabBarHeight)
        compose.setContent {
            FocusStackTheme {
                AppTabBar(
                    selected = AppTab.HOME,
                    inboxCount = 0,
                    comingUpCount = 0,
                    onSelect = {},
                )
            }
        }
        compose.waitForIdle()
        val density = compose.density
        val heightPx =
            compose
                .onNodeWithTag(APP_TAB_BAR_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .height
        val expectedPx = with(density) { AppTabBarHeight.toPx() }
        assertTrue(
            "tab bar height $heightPx px should be about ${AppTabBarHeight.value}dp ($expectedPx px)",
            kotlin.math.abs(heightPx - expectedPx) < 2f,
        )
        assertTrue(
            "tab bar must be taller than the old 64dp bar",
            heightPx > with(density) { 64.dp.toPx() } + 1f,
        )
    }

    @Test
    fun inboxBadgeUsesSurfaceHighFillNotMuted() {
        compose.setContent {
            FocusStackTheme {
                AppTabBar(
                    selected = AppTab.HOME,
                    inboxCount = 5,
                    comingUpCount = 0,
                    onSelect = {},
                )
            }
        }
        compose.waitForIdle()
        val badge = compose.onNodeWithTag(TAB_BADGE_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val bar = compose.onNodeWithTag(APP_TAB_BAR_TAG).fetchSemanticsNode()
        val bitmap = compose.onNodeWithTag(APP_TAB_BAR_TAG).captureToImage().asAndroidBitmap()
        try {
            val density = compose.density
            val left = ((badge.boundsInRoot.left - bar.boundsInRoot.left) * bitmap.width / bar.boundsInRoot.width).toInt()
            val top = ((badge.boundsInRoot.top - bar.boundsInRoot.top) * bitmap.height / bar.boundsInRoot.height).toInt()
            val right = ((badge.boundsInRoot.right - bar.boundsInRoot.left) * bitmap.width / bar.boundsInRoot.width).toInt()
            val bottom = ((badge.boundsInRoot.bottom - bar.boundsInRoot.top) * bitmap.height / bar.boundsInRoot.height).toInt()
            val samples = mutableListOf<Int>()
            val midY = ((top + bottom) / 2).coerceIn(0, bitmap.height - 1)
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                samples += bitmap.getPixel(x, midY)
            }
            for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
                samples += bitmap.getPixel(left.coerceIn(0, bitmap.width - 1), y)
                samples += bitmap.getPixel((right - 1).coerceIn(0, bitmap.width - 1), y)
            }
            assertTrue("badge region should yield pixels", samples.isNotEmpty())
            val surfaceHigh = FocusColors.SurfaceHigh
            val muted = FocusColors.Muted
            fun near(
                pixel: Int,
                color: Color,
                tolerance: Int = 28,
            ): Boolean {
                val a = android.graphics.Color.alpha(pixel)
                if (a < 200) return false
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                return kotlin.math.abs(r - (color.red * 255).toInt()) <= tolerance &&
                    kotlin.math.abs(g - (color.green * 255).toInt()) <= tolerance &&
                    kotlin.math.abs(b - (color.blue * 255).toInt()) <= tolerance
            }
            val surfaceHighHits = samples.count { near(it, surfaceHigh) }
            val mutedHits = samples.count { near(it, muted) }
            assertTrue(
                "badge fill should sample SurfaceHigh (hits=$surfaceHighHits muted=$mutedHits density=$density)",
                surfaceHighHits > 0,
            )
            assertTrue(
                "badge fill must not be dominated by the old Muted grey (surface=$surfaceHighHits muted=$mutedHits)",
                surfaceHighHits > mutedHits,
            )
            assertTrue(surfaceHigh != muted)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun inboxFocusAndSettingsTabsNameThemselvesAndHideAZeroBadge() {
        var inboxCount by mutableIntStateOf(0)
        compose.setContent {
            FocusStackTheme {
                AppTabBar(
                    selected = AppTab.HOME,
                    inboxCount = inboxCount,
                    comingUpCount = 0,
                    onSelect = {},
                )
            }
        }
        compose.waitForIdle()
        listOf("Inbox", "Focus", "Settings").forEach { name ->
            val node = compose.onNode(hasText(name) and isTab()).fetchSemanticsNode()
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue(
                "$name should name the tab (text=$texts desc=$descriptions)",
                texts.any { it == name } || descriptions.any { it == name },
            )
        }
        assertTrue("no Inbox badge at 0", !inboxBadge("0"))
        for (count in listOf(1, 2, 3, 0)) {
            compose.runOnIdle { inboxCount = count }
            compose.waitForIdle()
            if (count == 0) {
                assertTrue("no Inbox badge at 0", !inboxBadge("0"))
                assertTrue("stale Inbox badge 1", !inboxBadge("1"))
                assertTrue("stale Inbox badge 2", !inboxBadge("2"))
                assertTrue("stale Inbox badge 3", !inboxBadge("3"))
            } else {
                assertTrue("Inbox badge $count", inboxBadge(count.toString()))
            }
        }
    }

    @Test
    fun inboxBadgeTracksCaptureAndPromote() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await("FocusApp ready") { viewModel.uiState.value.ready }
                assertTrue("no Inbox badge at 0", !inboxBadge("0"))
                mainDispatcherRule.runTest { fixture.useCases.capture("Note one") }
                await("inbox count 1") { viewModel.uiState.value.inboxCount == 1 }
                assertTrue("Inbox badge 1 after capture", inboxBadge("1"))
                mainDispatcherRule.runTest { fixture.useCases.capture("Note two") }
                await("inbox count 2") { viewModel.uiState.value.inboxCount == 2 }
                assertTrue("Inbox badge 2 after second capture", inboxBadge("2"))
                mainDispatcherRule.runTest {
                    val id =
                        fixture.useCases
                            .observeSnapshot()
                            .first()
                            .tasks
                            .single { it.title == "Note two" }
                            .id
                    fixture.useCases.promote(id)
                }
                await("inbox count 1 after promote") { viewModel.uiState.value.inboxCount == 1 }
                assertTrue("Inbox badge 1 after promote", inboxBadge("1"))
                assertTrue("stale Inbox badge 2 after promote", !inboxBadge("2"))
                mainDispatcherRule.runTest {
                    val id =
                        fixture.useCases
                            .observeSnapshot()
                            .first()
                            .tasks
                            .single { it.title == "Note one" }
                            .id
                    fixture.useCases.promote(id)
                }
                await("inbox count 0 after emptying") { viewModel.uiState.value.inboxCount == 0 }
                assertTrue("no Inbox badge at 0 after emptying", !inboxBadge("0"))
                assertTrue("stale Inbox badge 1 after emptying", !inboxBadge("1"))
            }
        }
    }

    private fun isTab(): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun inboxBadge(count: String): Boolean {
        val inbox = compose.onNode(hasText("Inbox") and isTab()).fetchSemanticsNode().boundsInRoot
        val coming = compose.onNode(hasText("Coming up") and isTab()).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodesWithText(count, useUnmergedTree = true).fetchSemanticsNodes().any { node ->
            val bounds = node.boundsInRoot
            val mid = (bounds.left + bounds.right) / 2f
            val inboxMid = (inbox.left + inbox.right) / 2f
            val comingMid = (coming.left + coming.right) / 2f
            kotlin.math.abs(mid - inboxMid) < kotlin.math.abs(mid - comingMid)
        }
    }

    private fun await(
        step: String,
        predicate: () -> Boolean,
    ) {
        compose.waitUntil(conditionDescription = step, timeoutMillis = 5_000) {
            pump()
            predicate()
        }
    }

    private fun pump() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        mainDispatcherRule.scheduler.advanceUntilIdle()
    }
}
