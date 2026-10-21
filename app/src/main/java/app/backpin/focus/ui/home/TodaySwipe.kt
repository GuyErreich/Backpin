// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import app.backpin.focus.ui.SlotRole

/** One side of a card swipe. [None] means that direction does nothing. */
internal enum class RowSwipe {
    Done,
    Start,
    Interrupt,
    BringBack,
    None,
}

internal data class RowSwipeSides(
    val right: RowSwipe,
    val left: RowSwipe,
)

/**
 * Home row swipes.
 * Nothing in focus: right starts an open row, left brings it back.
 * The head: right is done, left interrupts.
 * Another open row while something is in focus: left interrupts.
 * A waiting row does not start or finish from a swipe.
 */
internal fun todayRowSwipes(
    role: SlotRole,
    somethingCurrent: Boolean,
): RowSwipeSides = when (role) {
    SlotRole.NOW -> RowSwipeSides(right = RowSwipe.Done, left = RowSwipe.Interrupt)
    SlotRole.WAITING -> RowSwipeSides(right = RowSwipe.None, left = RowSwipe.BringBack)
    SlotRole.OPEN ->
        if (somethingCurrent) {
            RowSwipeSides(right = RowSwipe.None, left = RowSwipe.Interrupt)
        } else {
            RowSwipeSides(right = RowSwipe.Start, left = RowSwipe.BringBack)
        }
    SlotRole.EMPTY -> RowSwipeSides(right = RowSwipe.None, left = RowSwipe.None)
}

/** Inbox and the other piles. Right stays Today. Left starts, or interrupts when something is in focus. */
internal fun pileLeftSwipe(somethingCurrent: Boolean): RowSwipe = if (somethingCurrent) {
    RowSwipe.Interrupt
} else {
    RowSwipe.Start
}

/**
 * Puts the hidden head back into a visible reorder.
 * The head stays in Today, so a reorder of the rows under it still names every Today id.
 */
internal fun insertHiddenHead(
    fullIds: List<String>,
    visibleOrder: List<String>,
    hiddenId: String?,
): List<String> {
    if (hiddenId == null || hiddenId !in fullIds || hiddenId in visibleOrder) return visibleOrder
    val index = fullIds.indexOf(hiddenId).coerceIn(0, visibleOrder.size)
    return visibleOrder.toMutableList().apply { add(index, hiddenId) }
}
