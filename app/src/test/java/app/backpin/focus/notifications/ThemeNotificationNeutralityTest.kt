// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.app.Notification
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeNotificationNeutralityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun currentAndRepeatNoticesStayOnTheNeutralXmlColorsWhenTheAppThemeIsEmber() {
        val ember = paletteFor(ThemeId.EMBER)
        val xmlMint = context.getColor(R.color.mint)
        val xmlOnMint = context.getColor(R.color.on_mint)
        assertNotEquals(Color(xmlMint), ember.done)
        assertNotEquals(Color(xmlMint), ember.accent)
        assertNotEquals(Color(xmlOnMint), ember.onAccent)

        val rows =
            listOf(
                TodayNoticeRow("now", "Review CI failure", current = true),
                TodayNoticeRow("next", "Message John", current = false),
            )
        val current =
            buildCurrentTaskNotification(
                context,
                "Review CI failure",
                "Next: reproduce CI failure",
                rows,
                offersInterrupt = true,
            )
        val applied = current.bigContentView.apply(context, FrameLayout(context)) as ViewGroup
        val currentRow = applied.findViewById<TextView>(R.id.today_row_0)
        assertEquals(xmlMint, (currentRow.background as ColorDrawable).color)
        assertEquals(xmlOnMint, currentRow.currentTextColor)

        val summary =
            buildRepeatSummaryNotification(
                context,
                RepeatBand.MORNING,
                count = 3,
                group = bandGroupKey(RepeatBand.MORNING),
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
                group = bandGroupKey(RepeatBand.MORNING),
            )
        assertEquals(Notification.COLOR_DEFAULT, summary.color)
        assertEquals(Notification.COLOR_DEFAULT, child.color)
        assertEquals(Notification.COLOR_DEFAULT, current.color)
    }
}
