// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.MainActivity
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.minuteOfDayLabel
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.awaitState
import app.backpin.focus.testing.sampleSnapshot
import app.backpin.focus.testing.sampleTask
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.runBlocking
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
import java.time.Duration
import java.time.Instant

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
    fun idleNoticeNamesTodayAndCountsOpenTasks() {
        val rows =
            listOf(
                TodayNoticeRow("a", "Review CI failure", current = false),
                TodayNoticeRow("b", "Message John", current = false),
            )
        val copy = currentNoticeCopy(rows, target = null, todayLabel = "Today", inFocusLabel = "In focus")
        assertEquals("Today", copy.title)
        assertEquals("2 tasks", copy.body)
        assertFalse(copy.body.contains("·"))
        assertFalse(copy.body.contains("Review CI failure"))
        assertTrue(copy.idle)
        assertFalse(copy.offersInterrupt)
        assertEquals("1 task", todayTaskCount(1))
        val fuller =
            listOf("TASK 2", "dfasdsadasdsada", "dasdasdasd").mapIndexed { index, title ->
                TodayNoticeRow("t$index", title, current = false)
            }
        val counted = currentNoticeCopy(fuller, target = null, todayLabel = "Today", inFocusLabel = "In focus")
        assertEquals("3 tasks", counted.body)
        assertFalse(counted.body.contains("·"))
        assertFalse(fuller.any { counted.body.contains(it.title) })
    }

    @Test
    fun activeNoticeKeepsTheTaskTitleAndTheReturnNote() {
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
        assertEquals("Return note: Next: reproduce CI failure", copy.body)
        assertFalse(copy.body.contains("·"))
        assertFalse(copy.body.contains("Message John"))
        assertFalse(copy.idle)
        assertTrue(copy.offersInterrupt)
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
        assertTrue(copy.offersInterrupt)
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
        assertEquals("Return note: Next: send the recap", copy.body)
        assertFalse(copy.body.contains("·"))
        assertFalse(copy.body.contains("Quick fix"))
        assertFalse(copy.idle)
        assertFalse(copy.offersInterrupt)
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

        val notification =
            buildCurrentTaskNotification(
                context,
                "Review CI failure",
                "Next: reproduce CI failure",
                offersInterrupt = true,
            )
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
        assertNoInterruptAction(notification)

        val nudge = manager.getNotificationChannel(NotificationContract.CHANNEL_NUDGE)
        assertEquals(NotificationContract.CHANNEL_NUDGE, nudge.id)
        assertNotEquals(NotificationContract.CHANNEL_CURRENT, nudge.id)
    }

    @Test
    fun todayRowsFollowTheSavedLimitAndMarkTheCurrentTask() {
        val tasks =
            (1..6).map { index ->
                todayTask(id = "t$index", title = "Task $index", sortOrder = index.toLong())
            } + todayTask(id = "later", title = "Parked", sortOrder = 0, bucket = TaskBucket.LATER)
        val snapshot = sampleSnapshot(tasks = tasks)
        val rows = todayNoticeRows(snapshot, currentTaskId = "t2", limit = 5)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), rows.map { it.taskId })
        assertEquals(listOf(false, true, false, false, false), rows.map { it.current })
        assertEquals(listOf("t1", "t2"), todayNoticeRows(snapshot, "t1", limit = 2).map { it.taskId })
    }

    @Test
    fun noticeKeyChangesWhenTodayChangesWithoutANewCurrentTask() {
        val copy = CurrentNoticeCopy(title = "Task 1", body = "In focus", idle = false, offersInterrupt = true)
        val first = listOf(TodayNoticeRow("t1", "Task 1", current = true))
        val withAnother = first + TodayNoticeRow("t2", "Task 2", current = false)
        val next = copy.copy(body = "Task 2")
        assertNotEquals(currentNoticeKey(copy, first), currentNoticeKey(next, withAnother))
        val idle = CurrentNoticeCopy(title = "Today", body = "Task 1", idle = true, offersInterrupt = false)
        assertNotEquals(currentNoticeKey(copy, first), currentNoticeKey(idle, first))
        val waiting = copy.copy(offersInterrupt = false)
        assertNotEquals(currentNoticeKey(copy, first), currentNoticeKey(waiting, first))
    }

    @Test
    fun noticeKeyChangesWhenAWaitingTaskBecomesActive() {
        val rows = listOf(TodayNoticeRow("task", "Review CI failure", current = true))
        val active =
            currentNoticeCopy(
                rows = rows,
                target = FocusTarget("task", "Review CI failure", null, recovery = false),
                todayLabel = "Today",
                inFocusLabel = "In focus",
            )
        val waiting =
            currentNoticeCopy(
                rows = rows,
                target = FocusTarget("task", "Review CI failure", null, recovery = true),
                todayLabel = "Today",
                inFocusLabel = "In focus",
            )
        assertTrue(active.offersInterrupt)
        assertFalse(waiting.offersInterrupt)
        assertEquals(active.title, waiting.title)
        assertEquals(active.body, waiting.body)
        assertFalse(active.idle)
        assertFalse(waiting.idle)
        assertNotEquals(currentNoticeKey(active, rows), currentNoticeKey(waiting, rows))
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
        assertFalse(copy.offersInterrupt)
        assertEquals("Today", copy.title)
        assertEquals(todayNoticeChannel(idle = true), NotificationContract.CHANNEL_TODAY)

        val notification =
            buildCurrentTaskNotification(
                context,
                copy.title,
                copy.body,
                rows,
                idle = copy.idle,
                offersInterrupt = copy.offersInterrupt,
            )
        assertEquals(NotificationContract.CHANNEL_TODAY, notification.channelId)
        assertNotEquals(NotificationContract.CHANNEL_TODAY_IDLE, notification.channelId)
        assertEquals(Notification.PRIORITY_DEFAULT, notification.priority)
        assertEquals("Today", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("2 tasks", copy.body)
        assertEquals(
            "2 tasks",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("·"))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertNull(notification.fullScreenIntent)
        assertEquals(0, notification.color)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertNoInterruptAction(notification)
        assertNotNull(notification.bigContentView)
        val open = shadowOf(notification.contentIntent).savedIntent
        assertEquals(NotificationContract.DESTINATION_FOCUS, open.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        val firstTap = clickIntent(notification.bigContentView, R.id.today_row_0)
        assertEquals(NotificationContract.ACTION_SWITCH, firstTap.action)
        assertEquals("a", firstTap.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        assertNull(firstTap.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        val secondTap = clickIntent(notification.bigContentView, R.id.today_row_1)
        assertEquals(NotificationContract.ACTION_SWITCH, secondTap.action)
        assertEquals("b", secondTap.getStringExtra(NotificationContract.EXTRA_TASK_ID))

        manager.notify(NotificationContract.ID_CURRENT, notification)
        val posted = manager.activeNotifications.single()
        assertEquals(NotificationContract.ID_CURRENT, posted.id)
        assertEquals(NotificationContract.CHANNEL_TODAY, posted.notification.channelId)
        assertEquals("Today", posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        val applied = appliedShade(notification)
        val first = applied.findViewById<TextView>(R.id.today_row_0)
        val second = applied.findViewById<TextView>(R.id.today_row_1)
        assertEquals(View.VISIBLE, applied.findViewById<View>(R.id.today_item_0).visibility)
        assertEquals(View.VISIBLE, first.visibility)
        assertEquals("Review CI failure", first.text.toString())
        assertTrue(applied.findViewById<View>(R.id.today_item_0).paddingTop > 0)
        assertTrue(applied.findViewById<View>(R.id.today_item_0).paddingBottom > 0)
        assertEquals(
            context.getString(R.string.notification_start_row, "Review CI failure"),
            first.contentDescription,
        )
        assertEquals(View.VISIBLE, second.visibility)
        assertEquals("Message John", second.text.toString())
        assertEquals(
            context.getString(R.string.notification_start_row, "Message John"),
            second.contentDescription,
        )
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_2).visibility)
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_3).visibility)
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_4).visibility)
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
                offersInterrupt = true,
            )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertEquals(NotificationContract.CHANNEL_CURRENT, notification.channelId)
        val open = shadowOf(notification.contentIntent).savedIntent
        assertEquals(NotificationContract.DESTINATION_FOCUS, open.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        assertNoInterruptAction(notification)
        assertNotNull(notification.bigContentView)
        assertEquals(
            "Review CI failure",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )

        val content = notification.bigContentView
        val applied = appliedShade(notification)
        val current = applied.findViewById<TextView>(R.id.today_row_0)
        val other = applied.findViewById<TextView>(R.id.today_row_1)
        assertEquals(View.VISIBLE, current.visibility)
        assertEquals("Review CI failure", current.text.toString())
        assertRowHasNoMintFill(current)
        assertEquals(View.VISIBLE, other.visibility)
        assertEquals("Message John", other.text.toString())
        assertRowHasNoMintFill(other)
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_2).visibility)
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_3).visibility)
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_4).visibility)

        assertEquals(
            context.getString(R.string.notification_now_row, "Review CI failure"),
            current.contentDescription,
        )
        assertEquals(
            context.getString(R.string.notification_switch_row, "Message John"),
            other.contentDescription,
        )
        val currentTap = clickIntent(content, R.id.today_row_0)
        assertEquals(NotificationContract.DESTINATION_FOCUS, currentTap.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        val switchTap = clickIntent(content, R.id.today_row_1)
        assertEquals(NotificationContract.ACTION_SWITCH, switchTap.action)
        assertEquals("next", switchTap.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        assertEquals(NotificationActionReceiver::class.java.name, switchTap.component?.className)
    }

    @Test
    fun waitingNoticeHidesInterruptAndStartsAnUnstartedRow() {
        val rows =
            listOf(
                TodayNoticeRow("wait", "Message John", current = true),
                TodayNoticeRow("open", "Task 2", current = false),
            )
        val notification =
            buildCurrentTaskNotification(
                context,
                "Message John",
                "Next: send the recap",
                rows,
                idle = false,
                offersInterrupt = false,
            )
        assertEquals(NotificationContract.CHANNEL_CURRENT, notification.channelId)
        assertEquals(Notification.PRIORITY_DEFAULT, notification.priority)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)
        assertNull(notification.fullScreenIntent)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertNoInterruptAction(notification)

        val content = notification.bigContentView
        val applied = appliedShade(notification)
        val waiting = applied.findViewById<TextView>(R.id.today_row_0)
        val other = applied.findViewById<TextView>(R.id.today_row_1)
        assertRowHasNoMintFill(waiting)
        assertEquals(
            context.getString(R.string.notification_start_row, "Task 2"),
            other.contentDescription,
        )
        val waitingTap = clickIntent(content, R.id.today_row_0)
        assertEquals(NotificationContract.DESTINATION_FOCUS, waitingTap.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        val startTap = clickIntent(content, R.id.today_row_1)
        assertEquals(NotificationContract.ACTION_SWITCH, startTap.action)
        assertEquals("open", startTap.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        assertNull(startTap.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        assertEquals(NotificationActionReceiver::class.java.name, startTap.component?.className)
    }

    @Test
    fun expandedShadeSeparatesEachUnstartedTask() {
        val titles = listOf("TASK 2", "dfasdsadasdsada", "dasdasdasd")
        val rows = titles.mapIndexed { index, title -> TodayNoticeRow("t$index", title, current = false) }
        val copy = currentNoticeCopy(rows, target = null, todayLabel = "Today", inFocusLabel = "In focus")
        assertEquals("Today", copy.title)
        assertEquals("3 tasks", copy.body)
        assertFalse(copy.offersInterrupt)

        val notification =
            buildCurrentTaskNotification(
                context,
                copy.title,
                copy.body,
                rows,
                idle = true,
                offersInterrupt = copy.offersInterrupt,
            )
        assertEquals("3 tasks", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNoInterruptAction(notification)
        assertNull(notification.fullScreenIntent)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0)

        val applied = appliedShade(notification)
        val rowIds = intArrayOf(R.id.today_row_0, R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4)
        val itemIds =
            intArrayOf(R.id.today_item_0, R.id.today_item_1, R.id.today_item_2, R.id.today_item_3, R.id.today_item_4)
        titles.forEachIndexed { index, title ->
            val view = applied.findViewById<TextView>(rowIds[index])
            assertEquals(View.VISIBLE, applied.findViewById<View>(itemIds[index]).visibility)
            assertEquals(View.VISIBLE, view.visibility)
            assertEquals(title, view.text.toString())
            assertFalse(view.text.toString().contains("·"))
            val tap = clickIntent(notification.bigContentView, rowIds[index])
            assertEquals(NotificationContract.ACTION_SWITCH, tap.action)
            assertEquals("t$index", tap.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        }
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_3).visibility)
        assertEquals(View.GONE, applied.findViewById<View>(R.id.today_item_4).visibility)
    }

    @Test
    fun expandedRowsStayNeutralWithNoMintFill() {
        val rows =
            listOf(
                TodayNoticeRow("now", "Review CI failure", current = true),
                TodayNoticeRow("next", "Message John", current = false),
            )
        val notification =
            buildCurrentTaskNotification(
                context,
                "Review CI failure",
                "In focus",
                rows,
                offersInterrupt = true,
            )
        val applied = appliedShade(notification)
        assertRowHasNoMintFill(applied.findViewById(R.id.today_row_0))
        assertRowHasNoMintFill(applied.findViewById(R.id.today_row_1))
        assertRowHasNoMintFill(applied.findViewById(R.id.today_band_0))
        assertRowHasNoMintFill(applied.findViewById(R.id.today_band_1))
        assertEquals(
            context.getColor(R.color.mint),
            applied.findViewById<TextView>(R.id.today_done_0).currentTextColor,
        )
    }

    @Test
    fun expandedNoticeHasNoInterruptAction() {
        val rows =
            listOf(
                TodayNoticeRow("now", "Review CI failure", current = true),
                TodayNoticeRow("next", "Message John", current = false),
            )
        val active =
            buildCurrentTaskNotification(
                context,
                "Review CI failure",
                "In focus",
                rows,
                offersInterrupt = true,
            )
        val idle =
            buildCurrentTaskNotification(
                context,
                "Today",
                "2 tasks",
                rows,
                idle = true,
                offersInterrupt = false,
            )
        assertNoInterruptAction(active)
        assertNoInterruptAction(idle)
    }

    @Test
    fun eachExpandedDoneTargetsThatTaskAndDoesNotLaunch() {
        val rows =
            listOf(
                TodayNoticeRow("now", "Review CI failure", current = true),
                TodayNoticeRow("next", "Message John", current = false),
                TodayNoticeRow("open", "File the form", current = false),
            )
        val notification =
            buildCurrentTaskNotification(
                context,
                "Review CI failure",
                "In focus",
                rows,
                offersInterrupt = true,
            )
        assertNoInterruptAction(notification)
        val content = notification.bigContentView
        val applied = appliedShade(notification)
        val doneIds = intArrayOf(R.id.today_done_0, R.id.today_done_1, R.id.today_done_2)
        rows.forEachIndexed { index, row ->
            val done = applied.findViewById<TextView>(doneIds[index])
            assertEquals(View.VISIBLE, done.visibility)
            assertEquals(context.getString(R.string.action_done), done.text.toString())
            assertEquals(
                context.getString(R.string.notification_done_row, row.title),
                done.contentDescription,
            )
            assertDoneWritesWithoutLaunch(clickIntent(content, doneIds[index]), row.taskId)
        }
        assertDoneWritesWithoutLaunch(todayDoneBroadcast(context, "now"), "now")
    }

    @Test
    fun doneFromTheShadeCompletesThatTaskWithoutOpeningTheApp() {
        useTestFocus { fixture ->
            runBlocking {
                val review = fixture.repository.seedInbox("Review CI failure").single()
                val john = fixture.repository.seedInbox("Message John").single()
                fixture.repository.promote(review.id)
                fixture.repository.promote(john.id)
                val coordinator = NotificationCoordinator(fixture.context, fixture.repository)
                coordinator.handleDone(review.id)
                val after =
                    fixture.repository.observeSnapshot().awaitState { snapshot ->
                        snapshot.tasks.any { it.id == review.id && it.status == TaskStatus.DONE } &&
                            snapshot.tasks.any { it.id == john.id && it.status == TaskStatus.OPEN }
                    }
                assertEquals(TaskStatus.DONE, after.tasks.single { it.id == review.id }.status)
                assertEquals(TaskStatus.OPEN, after.tasks.single { it.id == john.id }.status)
                coordinator.handleDone(null)
                coordinator.handleDone("missing")
                val parked = fixture.repository.seedInbox("Parked").single()
                coordinator.handleDone(parked.id)
                val unchanged =
                    fixture.repository.observeSnapshot().awaitState { snapshot ->
                        snapshot.tasks.any { it.id == parked.id && it.status == TaskStatus.OPEN }
                    }
                assertEquals(TaskStatus.OPEN, unchanged.tasks.single { it.id == parked.id }.status)
                assertEquals(TaskBucket.INBOX, unchanged.tasks.single { it.id == parked.id }.bucket)
            }
        }
    }

    @Test
    fun secondLineComesFromTheRowNotTheClock() {
        val clock = FakeAppClock()
        val cases = secondLineCases()
        cases.forEach { row ->
            assertEquals(row.name, row.expected, row.label())
        }
        val first = cases.map { it.label() }
        clock.advanceBy(Duration.ofHours(12))
        clock.set(Instant.parse("2026-10-17T03:00:00Z"))
        assertEquals(first, cases.map { it.label() })
        cases.forEach { row ->
            assertEquals(row.name, row.expected, row.label())
        }

        val untimed = todayTask(id = "untimed", title = "File the form", sortOrder = 0)
        val timed =
            todayTask(
                id = "timed",
                title = "Call Dana",
                sortOrder = 1,
                scheduledAtMinuteOfDay = 10 * 60 + 30,
            )
        val snapshot = sampleSnapshot(tasks = listOf(untimed, timed))

        fun mapped(): List<String> = todayNoticeRows(snapshot, currentTaskId = "", limit = 5) { task ->
            todayNoticeRowSubtitle(context, scheduledAtMinuteOfDay = task.scheduledAtMinuteOfDay)
        }.map { it.subtitle }

        val anytime = context.getString(R.string.notification_today_row_anytime)
        val clockTime = minuteOfDayLabel(10 * 60 + 30)
        assertEquals(listOf(anytime, clockTime), mapped())
        clock.advanceBy(Duration.ofHours(8))
        assertEquals(listOf(anytime, clockTime), mapped())
    }

    @Test
    fun expandedSecondLineShowsTheRowSubtitle() {
        val cases = secondLineCases()
        val rows =
            cases.mapIndexed { index, row ->
                TodayNoticeRow("t$index", row.name, current = false, subtitle = row.expected)
            }
        val notification =
            buildCurrentTaskNotification(
                context,
                "Today",
                "${cases.size} tasks",
                rows,
                idle = true,
            )
        val applied = appliedShade(notification)
        val bandIds =
            intArrayOf(R.id.today_band_0, R.id.today_band_1, R.id.today_band_2, R.id.today_band_3, R.id.today_band_4)
        cases.forEachIndexed { index, row ->
            val band = applied.findViewById<TextView>(bandIds[index])
            assertEquals(row.name, View.VISIBLE, band.visibility)
            assertEquals(row.name, row.expected, band.text.toString())
        }
    }

    private fun secondLineCases(): List<SecondLineCase> {
        val timedMinute = 10 * 60 + 30
        return listOf(
            SecondLineCase(
                name = "morning repeat",
                repeatBand = RepeatBand.MORNING,
                expected = context.getString(
                    R.string.notification_today_row_repeat,
                    context.getString(R.string.home_band_morning),
                ),
            ),
            SecondLineCase(
                name = "noon repeat",
                repeatBand = RepeatBand.NOON,
                expected = context.getString(
                    R.string.notification_today_row_repeat,
                    context.getString(R.string.home_band_noon),
                ),
            ),
            SecondLineCase(
                name = "night repeat",
                repeatBand = RepeatBand.NIGHT,
                expected = context.getString(
                    R.string.notification_today_row_repeat,
                    context.getString(R.string.home_band_night),
                ),
            ),
            SecondLineCase(
                name = "timed one-off",
                scheduledAtMinuteOfDay = timedMinute,
                expected = minuteOfDayLabel(timedMinute),
            ),
            SecondLineCase(
                name = "untimed",
                expected = context.getString(R.string.notification_today_row_anytime),
            ),
        )
    }

    private data class SecondLineCase(
        val name: String,
        val scheduledAtMinuteOfDay: Int? = null,
        val repeatBand: RepeatBand? = null,
        val expected: String,
    )

    private fun SecondLineCase.label(): String = todayNoticeRowSubtitle(
        context,
        scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
        repeatBand = repeatBand,
    )

    private fun todayTask(
        id: String,
        title: String,
        sortOrder: Long,
        bucket: TaskBucket = TaskBucket.TODAY,
        scheduledAtMinuteOfDay: Int? = null,
    ): Task = sampleTask(
        id = id,
        title = title,
        bucket = bucket,
        status = TaskStatus.OPEN,
        sortOrder = sortOrder,
        createdAtEpochMillis = sortOrder,
        scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
    )

    private fun appliedShade(notification: Notification): ViewGroup = notification.bigContentView.apply(
        context,
        FrameLayout(context),
    ) as ViewGroup

    private fun assertNoInterruptAction(notification: Notification) {
        val titles = notification.actions.orEmpty().map { it.title.toString() }
        assertFalse(titles.contains(context.getString(R.string.action_interrupt)))
        assertTrue(notification.actions.isNullOrEmpty())
    }

    private fun assertRowHasNoMintFill(view: View) {
        val background = view.background
        if (background is ColorDrawable) {
            assertNotEquals(context.getColor(R.color.mint), background.color)
        }
        if (view is TextView) {
            assertNotEquals(context.getColor(R.color.on_mint), view.currentTextColor)
        }
    }

    private fun assertDoneWritesWithoutLaunch(
        intent: Intent,
        taskId: String,
    ) {
        assertEquals(NotificationContract.ACTION_TODAY_DONE, intent.action)
        assertEquals(taskId, intent.getStringExtra(NotificationContract.EXTRA_TASK_ID))
        assertEquals(NotificationActionReceiver::class.java.name, intent.component?.className)
        assertNull(intent.getStringExtra(NotificationContract.EXTRA_DESTINATION))
        assertNotEquals(MainActivity::class.java.name, intent.component?.className)
    }

    private fun clickIntent(
        views: RemoteViews,
        viewId: Int,
    ): Intent {
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
}
