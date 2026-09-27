package app.backpin.focus.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("focus_today_ongoing", NotificationContract.CHANNEL_TODAY)
        assertEquals("focus_today_idle", NotificationContract.CHANNEL_TODAY_IDLE)
        assertNotEquals(NotificationContract.CHANNEL_TODAY, NotificationContract.CHANNEL_TODAY_IDLE)
    }

    @Test
    fun noticeStaysWhileTodayHasOpenTasks() {
        assertTrue(shouldShowTodayNotice(enabled = true, notificationsAllowed = true, openTodayCount = 1))
        assertFalse(shouldShowTodayNotice(enabled = true, notificationsAllowed = true, openTodayCount = 0))
        assertFalse(shouldShowTodayNotice(enabled = false, notificationsAllowed = true, openTodayCount = 2))
        assertFalse(shouldShowTodayNotice(enabled = true, notificationsAllowed = false, openTodayCount = 2))
    }

    @Test
    fun idleNoticeNamesTodayAndListsTitlesInOrder() {
        val rows =
            listOf(
                TodayNoticeRow("a", "Review CI failure", current = false),
                TodayNoticeRow("b", "Message John", current = false),
            )
        val copy = currentNoticeCopy(rows, target = null, todayLabel = "Today", inFocusLabel = "In focus")
        assertEquals("Today", copy.title)
        assertEquals("Review CI failure\nMessage John", copy.body)
        assertTrue(copy.idle)
        val fuller =
            listOf("Review CI failure", "Message John", "Write notes", "Call Sam").mapIndexed { index, title ->
                TodayNoticeRow("t$index", title, current = false)
            }
        assertEquals(
            "Review CI failure · Message John\nWrite notes · Call Sam",
            currentNoticeCopy(fuller, target = null, todayLabel = "Today", inFocusLabel = "In focus").body,
        )
    }

    @Test
    fun activeNoticeKeepsTheTaskTitleAndAddsTheRest() {
        val rows =
            listOf(
                TodayNoticeRow("a", "Review CI failure", current = true),
                TodayNoticeRow("b", "Message John", current = false),
            )
        val copy =
            currentNoticeCopy(
                rows = rows,
                target = FocusTarget("a", "Review CI failure", "Next: reproduce CI failure", recovery = false),
                todayLabel = "Today",
                inFocusLabel = "In focus",
            )
        assertEquals("Review CI failure", copy.title)
        assertEquals("Message John\nNext: reproduce CI failure", copy.body)
        assertFalse(copy.idle)
    }

    @Test
    fun blankReturnNoteUsesInFocusWhenNothingElseIsOpen() {
        val rows = listOf(TodayNoticeRow("task", "Review CI failure", current = true))
        val copy =
            currentNoticeCopy(
                rows = rows,
                target = FocusTarget("task", "Review CI failure", "  ", recovery = false),
                todayLabel = "Today",
                inFocusLabel = "In focus",
            )
        assertEquals("Review CI failure", copy.title)
        assertEquals("In focus", copy.body)
    }

    @Test
    fun waitingTaskUsesItsTitleAndTheReturnNote() {
        val rows =
            listOf(
                TodayNoticeRow("task", "Message John", current = true),
                TodayNoticeRow("other", "Quick fix", current = false),
            )
        val copy =
            currentNoticeCopy(
                rows = rows,
                target = FocusTarget("task", "Message John", "Next: send the recap", recovery = true),
                todayLabel = "Today",
                inFocusLabel = "In focus",
            )
        assertEquals("Message John", copy.title)
        assertEquals("Quick fix\nNext: send the recap", copy.body)
        assertFalse(copy.idle)
        assertFalse(copy.title.contains("warning", ignoreCase = true))
        assertFalse(copy.body.contains("alert", ignoreCase = true))
    }

    @Test
    fun ongoingNoticeStaysOnTheDefaultChannelAndOpensFocus() {
        ensureFocusChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)!!
        val channel = manager.getNotificationChannel(NotificationContract.CHANNEL_CURRENT)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertNull(manager.getNotificationChannel(NotificationContract.CHANNEL_CURRENT_LEGACY))
        val idleChannel = manager.getNotificationChannel(NotificationContract.CHANNEL_TODAY)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, idleChannel.importance)
        assertNull(idleChannel.sound)
        assertFalse(idleChannel.shouldVibrate())
        assertTrue(idleChannel.importance > NotificationManager.IMPORTANCE_LOW)
        assertTrue(idleChannel.importance < NotificationManager.IMPORTANCE_HIGH)
        assertNotEquals(NotificationManager.IMPORTANCE_NONE, idleChannel.importance)
        assertNotEquals(NotificationManager.IMPORTANCE_MIN, idleChannel.importance)
        assertNull(manager.getNotificationChannel(NotificationContract.CHANNEL_TODAY_IDLE))
        assertNotEquals(NotificationContract.CHANNEL_CURRENT, idleChannel.id)
        assertFalse(idleChannel.name.toString().contains("alert", ignoreCase = true))
        assertFalse(idleChannel.description.contains("warning", ignoreCase = true))

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

        assertNull(notification.fullScreenIntent)
        assertEquals(0, notification.color)
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
        val copy = CurrentNoticeCopy(title = "Task 1", body = "In focus", idle = false)
        val first = listOf(TodayNoticeRow("t1", "Task 1", current = true))
        val withAnother = first + TodayNoticeRow("t2", "Task 2", current = false)
        val next = copy.copy(body = "Task 2")
        assertNotEquals(currentNoticeKey(copy, first), currentNoticeKey(next, withAnother))
        val idle = CurrentNoticeCopy(title = "Today", body = "Task 1", idle = true)
        assertNotEquals(currentNoticeKey(copy, first), currentNoticeKey(idle, first))
    }

    @Test
    fun unstartedTodayStillPostsTheNotice() {
        val manager = context.getSystemService(NotificationManager::class.java)!!
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationContract.CHANNEL_TODAY_IDLE,
                "Today",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        ensureFocusChannels(context)
        assertNull(manager.getNotificationChannel(NotificationContract.CHANNEL_TODAY_IDLE))
        val channel = manager.getNotificationChannel(NotificationContract.CHANNEL_TODAY)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertTrue(channel.importance > NotificationManager.IMPORTANCE_LOW)
        assertTrue(channel.importance < NotificationManager.IMPORTANCE_HIGH)
        assertNotEquals(NotificationManager.IMPORTANCE_NONE, channel.importance)
        assertNotEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())

        val rows =
            listOf(
                TodayNoticeRow("a", "Review CI failure", current = false),
                TodayNoticeRow("b", "Message John", current = false),
            )
        assertTrue(shouldShowTodayNotice(enabled = true, notificationsAllowed = true, openTodayCount = rows.size))
        val copy =
            currentNoticeCopy(
                rows = rows,
                target = null,
                todayLabel = context.getString(R.string.notification_today_title),
                inFocusLabel = "In focus",
            )
        assertTrue(copy.idle)
        assertEquals("Today", copy.title)
        assertEquals(todayNoticeChannel(idle = true), NotificationContract.CHANNEL_TODAY)

        val notification = buildCurrentTaskNotification(context, copy.title, copy.body, rows, idle = true)
        assertEquals(NotificationContract.CHANNEL_TODAY, notification.channelId)
        assertNotEquals(NotificationContract.CHANNEL_TODAY_IDLE, notification.channelId)
        assertEquals(Notification.PRIORITY_DEFAULT, notification.priority)
        assertEquals("Today", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Review CI failure\nMessage John",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertNull(notification.fullScreenIntent)
        assertEquals(0, notification.color)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertNotNull(notification.bigContentView)
        val open = shadowOf(notification.contentIntent).savedIntent
        assertEquals(NotificationContract.DESTINATION_FOCUS, open.getStringExtra(NotificationContract.EXTRA_DESTINATION))

        manager.notify(NotificationContract.ID_CURRENT, notification)
        val posted = manager.activeNotifications.single()
        assertEquals(NotificationContract.ID_CURRENT, posted.id)
        assertEquals(NotificationContract.CHANNEL_TODAY, posted.notification.channelId)
        assertEquals("Today", posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        val applied = todayBigContent(context, rows).apply(context, FrameLayout(context)) as ViewGroup
        assertEquals(5, applied.childCount)
        assertEquals(View.VISIBLE, applied.getChildAt(0).visibility)
        assertEquals("Review CI failure", (applied.getChildAt(0) as TextView).text.toString())
        assertEquals(View.VISIBLE, applied.getChildAt(1).visibility)
        assertEquals("Message John", (applied.getChildAt(1) as TextView).text.toString())
        for (index in 2..4) {
            assertEquals(View.GONE, applied.getChildAt(index).visibility)
        }
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

        val content = todayBigContent(context, rows)
        val applied = content.apply(context, FrameLayout(context)) as ViewGroup
        assertEquals(5, applied.childCount)
        val current = applied.getChildAt(0) as TextView
        val other = applied.getChildAt(1) as TextView
        assertEquals(View.VISIBLE, current.visibility)
        assertEquals("Review CI failure", current.text.toString())
        assertEquals(context.getColor(R.color.mint), (current.background as ColorDrawable).color)
        assertEquals(context.getColor(R.color.on_mint), current.currentTextColor)
        assertEquals(View.VISIBLE, other.visibility)
        assertEquals("Message John", other.text.toString())
        assertEquals(android.graphics.Color.TRANSPARENT, (other.background as ColorDrawable).color)
        for (index in 2..4) {
            assertEquals(View.GONE, applied.getChildAt(index).visibility)
        }

        val currentTap = clickIntent(content, R.id.today_row_0)
        assertEquals(NotificationContract.DESTINATION_FOCUS, currentTap.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        val switchTap = clickIntent(content, R.id.today_row_1)
        assertEquals(NotificationContract.ACTION_SWITCH, switchTap.action)
        assertEquals("next", switchTap.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        assertEquals(NotificationActionReceiver::class.java.name, switchTap.component?.className)
    }

    private fun clickIntent(
        views: RemoteViews,
        viewId: Int,
    ): android.content.Intent {
        val actionsField = RemoteViews::class.java.getDeclaredField("mActions")
        actionsField.isAccessible = true
        val actions = actionsField.get(views) as Iterable<*>
        val pending =
            actions.firstNotNullOf { action ->
                if (action == null || !action.javaClass.simpleName.contains("SetOnClickResponse")) {
                    return@firstNotNullOf null
                }
                var type: Class<*>? = action.javaClass
                var id: Int? = null
                while (type != null && id == null) {
                    val idField = type.declaredFields.firstOrNull { it.name == "mViewId" }
                    if (idField != null) {
                        idField.isAccessible = true
                        id = idField.getInt(action)
                    }
                    type = type.superclass
                }
                if (id != viewId) return@firstNotNullOf null
                val responseField = action.javaClass.getDeclaredField("mResponse")
                responseField.isAccessible = true
                val response = responseField.get(action)
                val intentField = response.javaClass.getDeclaredField("mPendingIntent")
                intentField.isAccessible = true
                intentField.get(response) as PendingIntent
            }
        return shadowOf(pending).savedIntent
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
