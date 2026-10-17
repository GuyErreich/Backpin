// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.backpin.focus.FocusStackApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rebuilds band alarms after reboot, a clock or zone change, or an app update.
 * Repeat edits and band-time edits resync from the repository path instead.
 */
class BandResyncReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val pending = goAsync()
        val app = context.applicationContext as FocusStackApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.bandScheduler.resyncNow()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val HANDLED =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
    }
}
