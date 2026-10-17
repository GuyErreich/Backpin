// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.backpin.focus.FocusStackApp

/** Best-effort nudge tick. A skip is still success so WorkManager does not retry in a loop. */
class NudgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FocusStackApp ?: return Result.success()
        app.container.notifications.onScheduledTick()
        return Result.success()
    }
}
