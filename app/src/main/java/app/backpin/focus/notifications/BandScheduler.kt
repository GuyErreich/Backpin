package app.backpin.focus.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.ExactAlarmAccess
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatScheduleSync
import app.backpin.focus.domain.SystemAppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * One start, follow-up, and end alarm per band that has a reminder-on repeat.
 * Uses [AlarmManager.setExactAndAllowWhileIdle] when allowed, otherwise the
 * existing inexact WorkManager path.
 */
class BandScheduler(
    private val context: Context,
    private val repository: FocusRepository,
    private val scope: CoroutineScope,
    private val clock: AppClock = SystemAppClock,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : RepeatScheduleSync,
    ExactAlarmAccess {
    override fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    override fun resync() {
        scope.launch { resyncNow() }
    }

    suspend fun resyncNow() {
        val now = clock.nowEpochMillis()
        val times = repository.preferences.first().bandTimes
        val repeats = repository.observeRepeats().first()
        val alarms = nextBandAlarms(now, zone(), times, repeats)
        cancelAll()
        val exact = canScheduleExactAlarms()
        for (alarm in alarms) {
            if (exact) {
                setExactOrFallback(alarm)
            } else {
                enqueueWork(alarm, now)
            }
        }
    }

    private fun setExactOrFallback(alarm: BandAlarm) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: run {
            enqueueWork(alarm, clock.nowEpochMillis())
            return
        }
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.epochMillis,
                alarmPending(alarm),
            )
        } catch (_: SecurityException) {
            enqueueWork(alarm, clock.nowEpochMillis())
        }
    }

    private fun enqueueWork(
        alarm: BandAlarm,
        nowEpochMillis: Long,
    ) {
        val delay = (alarm.epochMillis - nowEpochMillis).coerceAtLeast(0L)
        val request =
            OneTimeWorkRequestBuilder<BandAlarmWorker>()
                .setInputData(
                    workDataOf(
                        NotificationContract.EXTRA_BAND to alarm.band.name,
                        NotificationContract.EXTRA_BAND_KIND to alarm.kind.name,
                    ),
                ).setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(alarm.band, alarm.kind),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelAll() {
        val manager = context.getSystemService(AlarmManager::class.java)
        for (band in RepeatBand.entries) {
            for (kind in BandAlarmKind.entries) {
                manager?.cancel(alarmPending(BandAlarm(band, kind, 0L)))
                workManager.cancelUniqueWork(uniqueWorkName(band, kind))
            }
        }
    }

    private fun alarmPending(alarm: BandAlarm): PendingIntent {
        val intent =
            Intent(context, BandAlarmReceiver::class.java).apply {
                action = NotificationContract.ACTION_BAND_ALARM
                data = Uri.parse("app.backpin.focus://band/${alarm.band.name}/${alarm.kind.name}")
                putExtra(NotificationContract.EXTRA_BAND, alarm.band.name)
                putExtra(NotificationContract.EXTRA_BAND_KIND, alarm.kind.name)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarm.band, alarm.kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun uniqueWorkName(
            band: RepeatBand,
            kind: BandAlarmKind,
        ): String = "repeat_band_${band.name}_${kind.name}"

        fun requestCode(
            band: RepeatBand,
            kind: BandAlarmKind,
        ): Int = 300 + band.ordinal * 10 + kind.ordinal
    }
}
