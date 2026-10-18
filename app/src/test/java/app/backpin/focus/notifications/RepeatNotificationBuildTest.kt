// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.MainActivity
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatNotificationBuildTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledSummaryUsesTheFigmaTitleAndAlerts() {
        val notification =
            buildRepeatSummaryNotification(
                context,
                RepeatBand.MORNING,
                count = 3,
                group = bandGroupKey(RepeatBand.MORNING),
                alert = true,
            )
        assertEquals("Morning · 3 things", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(bandGroupKey(RepeatBand.MORNING), NotificationCompat.getGroup(notification))
        assertTrue(NotificationCompat.isGroupSummary(notification))
    }

    @Test
    fun afterOneDoneTheSummaryCountDropsToTwo() {
        val notification =
            buildRepeatSummaryNotification(
                context,
                RepeatBand.MORNING,
                count = 2,
                group = bandGroupKey(RepeatBand.MORNING),
                alert = false,
            )
        assertEquals("Morning · 2 things", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun childIsSilentWithDoneAndCadenceBody() {
        val notification =
            buildRepeatChildNotification(
                context,
                RepeatBand.MORNING,
                repeatId = "r1",
                title = "Take meds",
                everyNDays = 1,
                epochDay = 20_746,
                group = bandGroupKey(RepeatBand.MORNING),
            )
        assertEquals("Take meds", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Every day", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(bandGroupKey(RepeatBand.MORNING), NotificationCompat.getGroup(notification))
        assertEquals(1, notification.actions.size)
        assertEquals(context.getString(R.string.action_done), notification.actions[0].title.toString())
        val everyTwo =
            buildRepeatChildNotification(
                context,
                RepeatBand.NOON,
                repeatId = "r2",
                title = "Water plants",
                everyNDays = 2,
                epochDay = 20_746,
                group = bandGroupKey(RepeatBand.NOON),
            )
        assertEquals("Every 2 days", everyTwo.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun groupedRepeatsKeepTheAlertingSummaryAndSilentChildDone() {
        val group = bandGroupKey(RepeatBand.MORNING)
        val summary =
            buildRepeatSummaryNotification(
                context,
                RepeatBand.MORNING,
                count = 3,
                group = group,
                alert = true,
            )
        val child =
            buildRepeatChildNotification(
                context,
                RepeatBand.MORNING,
                repeatId = "r1",
                title = "Take meds",
                everyNDays = 1,
                epochDay = 20_746,
                group = group,
            )
        assertEquals("Morning · 3 things", summary.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(NotificationContract.CHANNEL_REPEATS, summary.channelId)
        assertEquals(group, NotificationCompat.getGroup(summary))
        assertTrue(NotificationCompat.isGroupSummary(summary))
        assertFalse(
            summary.actions.orEmpty().any {
                it.title.toString() == context.getString(R.string.action_interrupt)
            },
        )
        assertEquals("Take meds", child.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Every day", child.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationContract.CHANNEL_REPEATS, child.channelId)
        assertEquals(group, NotificationCompat.getGroup(child))
        assertFalse(NotificationCompat.isGroupSummary(child))
        assertEquals(1, child.actions.size)
        assertEquals(context.getString(R.string.action_done), child.actions[0].title.toString())
        assertFalse(
            child.actions.any { it.title.toString() == context.getString(R.string.action_interrupt) },
        )
        assertTrue(child.flags and Notification.FLAG_GROUP_SUMMARY == 0)
    }

    @Test
    fun summaryAndChildBodyOpenMainActivityOnHome() {
        val group = bandGroupKey(RepeatBand.MORNING)
        val summary =
            buildRepeatSummaryNotification(
                context,
                RepeatBand.MORNING,
                count = 3,
                group = group,
                alert = true,
            )
        val child =
            buildRepeatChildNotification(
                context,
                RepeatBand.MORNING,
                repeatId = "r1",
                title = "Take meds",
                everyNDays = 1,
                epochDay = 20_746,
                group = group,
            )
        assertOpensHome(summary.contentIntent)
        assertOpensHome(child.contentIntent)
    }

    private fun assertOpensHome(pending: PendingIntent) {
        val intent = shadowOf(pending).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNull(intent.getStringExtra(NotificationContract.EXTRA_DESTINATION))
    }
}
