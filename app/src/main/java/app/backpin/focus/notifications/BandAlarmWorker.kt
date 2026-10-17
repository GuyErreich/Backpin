package app.backpin.focus.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.backpin.focus.FocusStackApp
import app.backpin.focus.domain.RepeatBand

/** Inexact fallback when exact alarms are not granted. A skip is still success. */
class BandAlarmWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FocusStackApp ?: return Result.success()
        val band =
            inputData.getString(NotificationContract.EXTRA_BAND)?.let {
                runCatching { RepeatBand.valueOf(it) }.getOrNull()
            } ?: return Result.success()
        val kind =
            inputData.getString(NotificationContract.EXTRA_BAND_KIND)?.let {
                runCatching { BandAlarmKind.valueOf(it) }.getOrNull()
            } ?: return Result.success()
        app.container.repeatNotifications.handleBandAlarm(band, kind)
        return Result.success()
    }
}
