package app.backpin.focus.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.backpin.focus.FocusStackApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Idempotent Later action from a nudge. Resume opens the app instead of starting a timer. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != NotificationContract.ACTION_LATER) return
        val pending = goAsync()
        val app = context.applicationContext as FocusStackApp
        val taskId = intent.getStringExtra(NotificationContract.EXTRA_TASK_ID)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.notifications.handleLater(taskId)
            } finally {
                pending.finish()
            }
        }
    }
}
