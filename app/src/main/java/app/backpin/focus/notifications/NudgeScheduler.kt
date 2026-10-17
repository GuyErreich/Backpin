// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Inexact periodic nudges. WorkManager may delay them, and the period cannot
 * be shorter than 15 minutes. This does not use exact alarms.
 */
class NudgeScheduler(
    private val workManager: WorkManager,
) {
    fun sync(
        enabled: Boolean,
        minutes: Int,
        notificationsAllowed: Boolean,
        hasRelevantTask: Boolean,
    ) {
        if (!shouldEnqueueNudges(enabled, notificationsAllowed, hasRelevantTask)) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val interval = minutes.toLong().coerceAtLeast(MIN_PERIOD_MINUTES)
        val request =
            PeriodicWorkRequestBuilder<NudgeWorker>(interval, TimeUnit.MINUTES)
                .setInitialDelay(interval, TimeUnit.MINUTES)
                .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK: String = "focus_nudge"
        const val MIN_PERIOD_MINUTES: Long = 15L
    }
}
