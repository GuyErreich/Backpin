// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BandNudgePolicyTest {
    private val open = listOf(occurrence(RepeatDayStatus.OPEN, reminderOn = true))

    @Test
    fun startPostsWhenItemsAreOpenAndThePersonIsFree() {
        assertEquals(
            BandNudgeDecision.Post,
            decideBandNudge(open, focusActive = false, notificationsAllowed = true, null, null, LocalTime.of(8, 0)),
        )
    }

    @Test
    fun startHoldsWhileAFocusFrameIsActive() {
        assertEquals(
            BandNudgeDecision.Hold,
            decideBandNudge(open, focusActive = true, notificationsAllowed = true, null, null, LocalTime.of(8, 0)),
        )
    }

    @Test
    fun startSkipsQuietHoursAndBlockedNotificationsAndEmptyBands() {
        assertEquals(
            "quiet-hours",
            (
                decideBandNudge(
                    open,
                    focusActive = false,
                    notificationsAllowed = true,
                    quietStart = "22:00",
                    quietEnd = "07:00",
                    time = LocalTime.of(23, 0),
                ) as BandNudgeDecision.Skip
            ).reason,
        )
        assertEquals(
            "notifications-blocked",
            (
                decideBandNudge(open, false, notificationsAllowed = false, null, null, LocalTime.of(8, 0))
                    as BandNudgeDecision.Skip
            ).reason,
        )
        assertEquals(
            "nothing-open",
            (
                decideBandNudge(
                    listOf(occurrence(RepeatDayStatus.DONE, reminderOn = true)),
                    false,
                    true,
                    null,
                    null,
                    LocalTime.of(8, 0),
                ) as BandNudgeDecision.Skip
            ).reason,
        )
        assertEquals(
            "nothing-open",
            (
                decideBandNudge(
                    listOf(occurrence(RepeatDayStatus.OPEN, reminderOn = false)),
                    false,
                    true,
                    null,
                    null,
                    LocalTime.of(8, 0),
                ) as BandNudgeDecision.Skip
            ).reason,
        )
    }

    @Test
    fun followUpNeverHoldsAndOnlyPostsOnce() {
        assertEquals(
            "focus-active",
            (
                decideBandFollowUp(open, focusActive = true, true, null, null, LocalTime.of(11, 30), alreadyPosted = false)
                    as BandNudgeDecision.Skip
            ).reason,
        )
        assertEquals(
            "follow-up-already-posted",
            (
                decideBandFollowUp(open, focusActive = false, true, null, null, LocalTime.of(11, 30), alreadyPosted = true)
                    as BandNudgeDecision.Skip
            ).reason,
        )
        assertEquals(
            BandNudgeDecision.Post,
            decideBandFollowUp(open, focusActive = false, true, null, null, LocalTime.of(11, 30), alreadyPosted = false),
        )
    }

    @Test
    fun openReminderOccurrencesDropsDoneMissedAndMuted() {
        val items =
            listOf(
                occurrence(RepeatDayStatus.OPEN, reminderOn = true, id = "a"),
                occurrence(RepeatDayStatus.DONE, reminderOn = true, id = "b"),
                occurrence(RepeatDayStatus.MISSED, reminderOn = true, id = "c"),
                occurrence(RepeatDayStatus.OPEN, reminderOn = false, id = "d"),
            )
        val openOnly = openReminderOccurrences(items)
        assertEquals(listOf("a"), openOnly.map { it.repeat.id })
        assertTrue(openOnly.all { it.status == RepeatDayStatus.OPEN && it.repeat.reminderOn })
    }

    private fun occurrence(
        status: RepeatDayStatus,
        reminderOn: Boolean,
        id: String = "r",
    ) = RepeatOccurrence(
        repeat =
            Repeat(
                id = id,
                title = "Take meds",
                band = RepeatBand.MORNING,
                everyNDays = 1,
                anchorEpochDay = 1,
                reminderOn = reminderOn,
                sortOrder = 1,
                createdAtEpochMillis = 1,
                archivedAtEpochMillis = null,
            ),
        epochDay = 1,
        status = status,
    )
}
