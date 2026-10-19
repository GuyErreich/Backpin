// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.UndoToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UndoSnackbarMessageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun repeatCheckOffUsesCheckedOffResourceAndIsNeverBlank() {
        val undo =
            PendingUndo(
                id = 1L,
                message = "",
                target =
                    UndoTarget.RepeatCheckOff(
                        repeatId = "r1",
                        epochDay = 0,
                        restore = RepeatDayStatus.OPEN,
                    ),
            )
        val label = context.getString(R.string.undo_checked_off)
        assertEquals("Checked off", label)
        assertEquals(label, undoSnackbarMessage(undo, label))
        assertTrue(undoSnackbarMessage(undo, label).isNotBlank())
    }

    @Test
    fun repeatCheckOffIgnoresAViewModelMessage() {
        val undo =
            PendingUndo(
                id = 3L,
                message = " leftover ",
                target =
                    UndoTarget.RepeatCheckOff(
                        repeatId = "r2",
                        epochDay = 0,
                        restore = RepeatDayStatus.OPEN,
                    ),
            )
        val label = context.getString(R.string.undo_checked_off)
        assertEquals(label, undoSnackbarMessage(undo, label))
    }

    @Test
    fun taskUndoKeepsItsOwnMessage() {
        val undo =
            PendingUndo(
                id = 2L,
                message = "Added to Today",
                target = UndoTarget.Tasks(UndoToken(tasks = emptyList(), frames = emptyList())),
            )
        assertEquals("Added to Today", undoSnackbarMessage(undo, context.getString(R.string.undo_checked_off)))
    }

    @Test
    fun discardedCaptureUsesKeptInInboxResource() {
        val undo =
            PendingUndo(
                id = 4L,
                message = "",
                target = UndoTarget.DiscardCapture("task-1"),
            )
        val kept = context.getString(R.string.kept_in_inbox)
        assertEquals("Kept in Inbox", kept)
        assertEquals(
            kept,
            undoSnackbarMessage(undo, context.getString(R.string.undo_checked_off), kept),
        )
        assertTrue(
            undoSnackbarMessage(undo, context.getString(R.string.undo_checked_off), kept).isNotBlank(),
        )
    }
}
