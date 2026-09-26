package app.backpin.focus.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CurrentNoticeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun channelIdsLeaveTheLowImportanceChannelUnused() {
        assertEquals("focus_current_ongoing", NotificationContract.CHANNEL_CURRENT)
        assertEquals("focus_current", NotificationContract.CHANNEL_CURRENT_LEGACY)
        assertNotEquals(NotificationContract.CHANNEL_CURRENT, NotificationContract.CHANNEL_CURRENT_LEGACY)
    }

    @Test
    fun nowUsesTheTaskTitleAndReturnNote() {
        val copy =
            currentNoticeCopy(
                target = FocusTarget("task", "Review CI failure", "Next: reproduce CI failure", recovery = false),
                inFocusLabel = "In focus",
                beforeThis = { "Before this: $it" },
            )
        assertEquals("Review CI failure", copy.title)
        assertEquals("Next: reproduce CI failure", copy.body)
    }

    @Test
    fun blankReturnNoteUsesInFocus() {
        val copy =
            currentNoticeCopy(
                target = FocusTarget("task", "Review CI failure", "  ", recovery = false),
                inFocusLabel = "In focus",
                beforeThis = { "Before this: $it" },
            )
        assertEquals("Review CI failure", copy.title)
        assertEquals("In focus", copy.body)
    }

    @Test
    fun waitingTaskKeepsBeforeThis() {
        val copy =
            currentNoticeCopy(
                target = FocusTarget("task", "Message John", "Next: send the recap", recovery = true),
                inFocusLabel = "In focus",
                beforeThis = { "Before this: $it" },
            )
        assertEquals("Before this: Message John", copy.title)
        assertEquals("Next: send the recap", copy.body)
    }

    @Test
    fun ongoingNoticeStaysOnTheDefaultChannelAndOpensFocus() {
        ensureFocusChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)!!
        val channel = manager.getNotificationChannel(NotificationContract.CHANNEL_CURRENT)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertNull(manager.getNotificationChannel(NotificationContract.CHANNEL_CURRENT_LEGACY))

        val notification = buildCurrentTaskNotification(context, "Review CI failure", "Next: reproduce CI failure")
        assertEquals(NotificationContract.CHANNEL_CURRENT, notification.channelId)
        assertEquals("Review CI failure", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Next: reproduce CI failure",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertTrue(channel.importance < NotificationManager.IMPORTANCE_HIGH)

        val open = shadowOf(notification.contentIntent).savedIntent
        assertEquals(NotificationContract.DESTINATION_FOCUS, open.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        assertEquals(1, notification.actions.size)
        assertEquals(context.getString(R.string.action_interrupt), notification.actions[0].title.toString())
        val interrupt = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(
            NotificationContract.DESTINATION_CAPTURE,
            interrupt.getStringExtra(NotificationContract.EXTRA_DESTINATION),
        )

        val nudge = manager.getNotificationChannel(NotificationContract.CHANNEL_NUDGE)
        assertEquals(NotificationContract.CHANNEL_NUDGE, nudge.id)
        assertNotEquals(NotificationContract.CHANNEL_CURRENT, nudge.id)
    }
}
