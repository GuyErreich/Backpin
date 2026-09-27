package app.backpin.focus.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun todayRowsFollowTheSavedLimitAndMarkTheCurrentTask() {
        val tasks =
            (1..6).map { index ->
                task(id = "t$index", title = "Task $index", sortOrder = index.toLong())
            } + task(id = "later", title = "Parked", sortOrder = 0, bucket = TaskBucket.LATER)
        val snapshot = FocusSnapshot(tasks = tasks)
        val rows = todayNoticeRows(snapshot, currentTaskId = "t2", limit = 5)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), rows.map { it.taskId })
        assertEquals(listOf(false, true, false, false, false), rows.map { it.current })
        assertEquals(listOf("t1", "t2"), todayNoticeRows(snapshot, "t1", limit = 2).map { it.taskId })
    }

    @Test
    fun noticeKeyChangesWhenTodayChangesWithoutANewCurrentTask() {
        val target = FocusTarget("t1", "Task 1", null, recovery = false)
        val copy = CurrentNoticeCopy(title = "Task 1", body = "In focus")
        val first = listOf(TodayNoticeRow("t1", "Task 1", current = true))
        val withAnother =
            first + TodayNoticeRow("t2", "Task 2", current = false)
        assertNotEquals(currentNoticeKey(target, copy, first), currentNoticeKey(target, copy, withAnother))
    }

    @Test
    fun expandedNoticeListsTodayAndDoesNotSwitchTheCurrentRow() {
        val rows =
            listOf(
                TodayNoticeRow("now", "Review CI failure", current = true),
                TodayNoticeRow("next", "Message John", current = false),
            )
        val notification =
            buildCurrentTaskNotification(
                context,
                "Review CI failure",
                "Next: reproduce CI failure",
                rows,
            )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertEquals(NotificationContract.CHANNEL_CURRENT, notification.channelId)
        val open = shadowOf(notification.contentIntent).savedIntent
        assertEquals(NotificationContract.DESTINATION_FOCUS, open.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.bigContentView)
        assertEquals(
            "Review CI failure",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )

        val applied = todayBigContent(context, rows).apply(context, FrameLayout(context)) as ViewGroup
        assertEquals(2, applied.childCount)
        assertEquals("Now · Review CI failure", (applied.getChildAt(0) as TextView).text.toString())
        assertEquals("Message John", (applied.getChildAt(1) as TextView).text.toString())

        val switch = switchBroadcast(context, "next")
        assertEquals(NotificationContract.ACTION_SWITCH, switch.action)
        assertEquals("next", switch.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        assertEquals(NotificationActionReceiver::class.java.name, switch.component?.className)
    }

    private fun task(
        id: String,
        title: String,
        sortOrder: Long,
        bucket: TaskBucket = TaskBucket.TODAY,
    ) = Task(
        id = id,
        title = title,
        bucket = bucket,
        status = TaskStatus.OPEN,
        returnCue = null,
        sortOrder = sortOrder,
        createdAtEpochMillis = sortOrder,
        completedAtEpochMillis = null,
    )
}
