package app.backpin.focus.notifications

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun childIsSilentWithDoneAndTheFigmaBody() {
        val notification =
            buildRepeatChildNotification(
                context,
                RepeatBand.MORNING,
                repeatId = "r1",
                title = "Take meds",
                epochDay = 20_746,
                group = bandGroupKey(RepeatBand.MORNING),
            )
        assertEquals("Take meds", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Morning repeat", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(bandGroupKey(RepeatBand.MORNING), NotificationCompat.getGroup(notification))
        assertEquals(1, notification.actions.size)
        assertEquals(context.getString(R.string.action_done), notification.actions[0].title.toString())
    }
}
