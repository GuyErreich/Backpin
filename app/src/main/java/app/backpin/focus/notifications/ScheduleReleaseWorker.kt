// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.backpin.focus.FocusStackApp
import app.backpin.focus.domain.ReleaseWakeUp
import java.util.concurrent.TimeUnit

/**
 * Inexact check for scheduled tasks whose moment has arrived, and for countdowns on their day.
 * Scheduled tasks move onto Today when a slot is free. A due countdown comes into focus
 * outside quiet hours and posts one silent arrival notice. Does not use an exact alarm
 * or run as a foreground service. The Today notice refreshes from the snapshot.
 */
class ScheduleReleaseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FocusStackApp ?: return Result.success()
        app.container.releaseDue()
        return Result.success()
    }
}

/**
 * Keeps one inexact periodic check. WorkManager may delay it.
 * The period cannot be shorter than 15 minutes, and this does not use exact alarms.
 * [wakeAt] adds one inexact check at a scheduled time, so a timed task does not wait
 * for the next period. It can still run a few minutes late.
 */
class ScheduleReleaseScheduler(
    private val workManager: WorkManager,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : ReleaseWakeUp {
    fun ensure() {
        val request =
            PeriodicWorkRequestBuilder<ScheduleReleaseWorker>(
                NudgeScheduler.MIN_PERIOD_MINUTES,
                TimeUnit.MINUTES,
            ).build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun wakeAt(epochMillis: Long) {
        val delay = (epochMillis - nowEpochMillis()).coerceAtLeast(0L)
        val request =
            OneTimeWorkRequestBuilder<ScheduleReleaseWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(
            "$UNIQUE_WORK_AT$epochMillis",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK: String = "focus_schedule_release"
        const val UNIQUE_WORK_AT: String = "focus_schedule_release_at_"
    }
}
