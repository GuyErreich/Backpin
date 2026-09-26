package app.backpin.focus.notifications

import app.backpin.focus.domain.FocusFrame
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NudgePolicyTest {
    @Test
    fun quietHoursCrossMidnight() {
        assertTrue(isQuietNow("22:00", "07:00", LocalTime.of(23, 15)))
        assertTrue(isQuietNow("22:00", "07:00", LocalTime.of(22, 0)))
        assertFalse(isQuietNow("22:00", "07:00", LocalTime.of(7, 0)))
        assertFalse(isQuietNow("22:00", "07:00", LocalTime.of(12, 0)))
        assertFalse(isQuietNow(null, "07:00", LocalTime.of(23, 0)))
        assertFalse(isQuietNow("22:00", "22:00", LocalTime.of(22, 30)))
    }

    @Test
    fun nudgePostsOnlyWhenThePersonIsAwayAndDue() {
        val prefs =
            UserPreferences(
                nudgesEnabled = true,
                nudgeMinutes = 30,
                quietStart = "22:00",
                quietEnd = "07:00",
            )
        val target = FocusTarget("task", "Review CI failure", "Next: reproduce CI failure", recovery = false)
        val posted =
            decideNudge(
                nowEpochMillis = 3_600_000L,
                time = LocalTime.of(15, 0),
                appVisible = false,
                notificationsAllowed = true,
                preferences = prefs,
                target = target,
            )
        assertEquals(NudgeDecision.Post("task", "Review CI failure"), posted)
        assertEquals(
            "app-visible",
            (decideNudge(3_600_000L, LocalTime.of(15, 0), true, true, prefs, target) as NudgeDecision.Skip).reason,
        )
        assertEquals(
            "quiet-hours",
            (decideNudge(3_600_000L, LocalTime.of(23, 0), false, true, prefs, target) as NudgeDecision.Skip).reason,
        )
        assertEquals(
            "notifications-blocked",
            (decideNudge(3_600_000L, LocalTime.of(15, 0), false, false, prefs, target) as NudgeDecision.Skip).reason,
        )
        val recent =
            prefs.copy(lastInteractionAtEpochMillis = 3_600_000L - 60_000L)
        assertEquals(
            "recent-activity",
            (decideNudge(3_600_000L, LocalTime.of(15, 0), false, true, recent, target) as NudgeDecision.Skip).reason,
        )
        val repeated =
            prefs.copy(lastNudgedAtEpochMillis = 3_600_000L - 60_000L, lastNudgedTaskId = "task")
        assertEquals(
            "recent-nudge",
            (decideNudge(3_600_000L, LocalTime.of(15, 0), false, true, repeated, target) as NudgeDecision.Skip).reason,
        )
        assertFalse(shouldEnqueueNudges(enabled = true, notificationsAllowed = false, hasRelevantTask = true))
        assertTrue(shouldEnqueueNudges(enabled = true, notificationsAllowed = true, hasRelevantTask = true))
    }

    @Test
    fun relevantFocusPrefersTheActiveFrame() {
        val active = frame("active", "now", FrameState.ACTIVE)
        val waiting = frame("waiting", "before", FrameState.AWAITING_RESUME)
        val snapshot =
            FocusSnapshot(
                tasks =
                    listOf(
                        task("now", "Message John"),
                        task("before", "Review CI failure"),
                    ),
                frames = listOf(waiting, active),
            )
        assertEquals("Message John", relevantFocus(snapshot)?.title)
        assertEquals(false, relevantFocus(snapshot)?.recovery)
        val recoveryOnly = snapshot.copy(frames = listOf(waiting))
        assertEquals("Review CI failure", relevantFocus(recoveryOnly)?.title)
        assertEquals(true, relevantFocus(recoveryOnly)?.recovery)
    }

    private fun task(
        id: String,
        title: String,
    ) = Task(
        id = id,
        title = title,
        bucket = TaskBucket.TODAY,
        status = TaskStatus.OPEN,
        returnCue = null,
        sortOrder = 1,
        createdAtEpochMillis = 1,
        completedAtEpochMillis = null,
    )

    private fun frame(
        id: String,
        taskId: String,
        state: FrameState,
    ) = FocusFrame(
        id = id,
        taskId = taskId,
        parentFrameId = null,
        state = state,
        startedAtEpochMillis = 10,
        pausedAtEpochMillis = null,
        accumulatedMillis = 0,
    )
}
