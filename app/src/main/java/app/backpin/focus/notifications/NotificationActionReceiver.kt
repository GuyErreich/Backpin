// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.backpin.focus.FocusStackApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Later from a nudge, Switch or Done from the Today notice, and Done from a repeat child. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != NotificationContract.ACTION_LATER &&
            action != NotificationContract.ACTION_SWITCH &&
            action != NotificationContract.ACTION_TODAY_DONE &&
            action != NotificationContract.ACTION_REPEAT_DONE
        ) {
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as FocusStackApp
        val taskId = intent.getStringExtra(NotificationContract.EXTRA_TASK_ID)
        val repeatId = intent.getStringExtra(NotificationContract.EXTRA_REPEAT_ID)
        val epochDay = intent.getLongExtra(NotificationContract.EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    NotificationContract.ACTION_LATER -> app.container.notifications.handleLater(taskId)
                    NotificationContract.ACTION_SWITCH -> app.container.notifications.handleSwitch(taskId)
                    NotificationContract.ACTION_TODAY_DONE -> app.container.notifications.handleDone(taskId)
                    NotificationContract.ACTION_REPEAT_DONE -> {
                        if (epochDay != Long.MIN_VALUE) {
                            app.container.repeatNotifications.handleRepeatDone(repeatId, epochDay)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
