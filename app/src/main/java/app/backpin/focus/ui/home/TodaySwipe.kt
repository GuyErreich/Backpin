// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import app.backpin.focus.ui.SlotRole

/** Right swipe on a Today row. Slide left opens Bring back. */
internal enum class TodayRightSwipe {
    Done,
    Start,
    Interrupt,
}

/**
 * Picks the right-swipe word from the row and whether something is already current.
 * The current row is Done. A row that has not started is Start when nothing is
 * current, and Interrupt when another task is current. A row waiting for an
 * explicit resume stays Done so the swipe does not start or interrupt it.
 */
internal fun todayRightSwipe(
    role: SlotRole,
    somethingCurrent: Boolean,
): TodayRightSwipe =
    when {
        role == SlotRole.NOW || role == SlotRole.WAITING -> TodayRightSwipe.Done
        somethingCurrent -> TodayRightSwipe.Interrupt
        else -> TodayRightSwipe.Start
    }
