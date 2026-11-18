// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import app.backpin.focus.ui.SlotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class TodaySwipeTest {
    @Test
    fun swipesFollowWhetherSomethingIsInFocus() {
        val cases =
            listOf(
                Case(SlotRole.NOW, somethingCurrent = true, RowSwipe.Done, RowSwipe.Interrupt),
                Case(SlotRole.OPEN, somethingCurrent = false, RowSwipe.Start, RowSwipe.BringBack),
                Case(SlotRole.OPEN, somethingCurrent = true, RowSwipe.None, RowSwipe.Interrupt),
                Case(SlotRole.WAITING, somethingCurrent = true, RowSwipe.None, RowSwipe.BringBack),
                Case(SlotRole.WAITING, somethingCurrent = false, RowSwipe.None, RowSwipe.BringBack),
                Case(SlotRole.EMPTY, somethingCurrent = false, RowSwipe.None, RowSwipe.None),
            )
        cases.forEach { case ->
            val sides = todayRowSwipes(case.role, case.somethingCurrent)
            assertEquals(case.role.name, case.right, sides.right)
            assertEquals(case.role.name, case.left, sides.left)
        }
        assertEquals(RowSwipe.Start, pileLeftSwipe(somethingCurrent = false))
        assertEquals(RowSwipe.Interrupt, pileLeftSwipe(somethingCurrent = true))
    }

    @Test
    fun hiddenHeadStaysInTheReorder() {
        val cases =
            listOf(
                Reorder(listOf("head", "a", "b"), listOf("b", "a"), "head", listOf("head", "b", "a")),
                Reorder(listOf("a", "head", "b"), listOf("b", "a"), "head", listOf("b", "head", "a")),
                Reorder(listOf("a", "b"), listOf("b", "a"), null, listOf("b", "a")),
                Reorder(listOf("head"), emptyList(), "head", listOf("head")),
            )
        cases.forEach { case ->
            assertEquals(
                case.fullIds.toString(),
                case.expected,
                insertHiddenHead(case.fullIds, case.visibleOrder, case.hiddenId),
            )
        }
    }

    private data class Case(
        val role: SlotRole,
        val somethingCurrent: Boolean,
        val right: RowSwipe,
        val left: RowSwipe,
    )

    private data class Reorder(
        val fullIds: List<String>,
        val visibleOrder: List<String>,
        val hiddenId: String?,
        val expected: List<String>,
    )
}
