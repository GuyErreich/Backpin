package app.backpin.focus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.backpin.focus.FocusStackApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Later from a nudge, and Switch from the expanded current-task notice. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != NotificationContract.ACTION_LATER && action != NotificationContract.ACTION_SWITCH) return
        val pending = goAsync()
        val app = context.applicationContext as FocusStackApp
        val taskId = intent.getStringExtra(NotificationContract.EXTRA_TASK_ID)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    NotificationContract.ACTION_LATER -> app.container.notifications.handleLater(taskId)
                    NotificationContract.ACTION_SWITCH -> app.container.notifications.handleSwitch(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
