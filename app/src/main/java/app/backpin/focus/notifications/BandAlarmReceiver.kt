// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.backpin.focus.FocusStackApp
import app.backpin.focus.domain.RepeatBand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Exact band start, follow-up, or end. One alarm per band event, never per repeat. */
class BandAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != NotificationContract.ACTION_BAND_ALARM) return
        val band =
            intent.getStringExtra(NotificationContract.EXTRA_BAND)?.let {
                runCatching { RepeatBand.valueOf(it) }.getOrNull()
            } ?: return
        val kind =
            intent.getStringExtra(NotificationContract.EXTRA_BAND_KIND)?.let {
                runCatching { BandAlarmKind.valueOf(it) }.getOrNull()
            } ?: return
        val pending = goAsync()
        val app = context.applicationContext as FocusStackApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.repeatNotifications.handleBandAlarm(band, kind)
            } finally {
                pending.finish()
            }
        }
    }
}
