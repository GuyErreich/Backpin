package app.backpin.focus.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.backpin.focus.FocusStackApp
import java.util.concurrent.TimeUnit

/**
 * Inexact check for scheduled tasks whose local date has arrived.
 * Moves them onto Today when a slot is free. Does not post its own notice,
 * use an exact alarm, or run as a foreground service. The Today notice
 * refreshes from the snapshot once a task is actually on Today.
 */
class ScheduleReleaseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FocusStackApp ?: return Result.success()
        app.container.repository.releaseDueScheduled()
        return Result.success()
    }
}

/**
 * Keeps one inexact periodic check. WorkManager may delay it.
 * The period cannot be shorter than 15 minutes, and this does not use exact alarms.
 */
class ScheduleReleaseScheduler(
    private val workManager: WorkManager,
) {
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

    companion object {
        const val UNIQUE_WORK: String = "focus_schedule_release"
    }
}
