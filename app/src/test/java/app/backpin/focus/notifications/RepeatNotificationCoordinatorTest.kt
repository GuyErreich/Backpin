package app.backpin.focus.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.RepeatBand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatNotificationCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 10, 16)
    private val clock = SettableClock(0L)
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepositoryImpl
    private lateinit var coordinator: RepeatNotificationCoordinator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        clock.now = today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            FocusRepositoryImpl(
                database,
                PreferencesDataStore(context, "repeat-notice-${UUID.randomUUID()}"),
                clock,
            )
        coordinator =
            RepeatNotificationCoordinator(
                context,
                repository,
                BandScheduler(context, repository, scope, clock),
                clock,
            )
        ensureFocusChannels(context)
    }

    @After
    fun tearDown() {
        database.close()
        notificationManager().cancelAll()
    }

    @Test
    fun lastDoneCancelsTheSummaryAndTheWholeGroup() =
        runBlocking {
            val first =
                repository.createRepeat("Take meds", RepeatBand.MORNING, 1, today.toEpochDay())!!
            val second =
                repository.createRepeat("Vitamins", RepeatBand.MORNING, 1, today.toEpochDay())!!
            coordinator.handleBandAlarm(RepeatBand.MORNING, BandAlarmKind.START)
            val posted = groupNotifications()
            assertEquals(3, posted.size)
            assertTrue(posted.any { it.id == repeatSummaryNotificationId(RepeatBand.MORNING) })

            coordinator.handleRepeatDone(first, today.toEpochDay())
            val afterOne = groupNotifications()
            assertEquals(2, afterOne.size)
            assertTrue(afterOne.any { it.id == repeatSummaryNotificationId(RepeatBand.MORNING) })

            coordinator.handleRepeatDone(second, today.toEpochDay())
            assertTrue(groupNotifications().isEmpty())
            assertEquals(0, notificationManager().activeNotifications.size)
        }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun groupNotifications() =
        notificationManager().activeNotifications.filter {
            it.notification.group == bandGroupKey(RepeatBand.MORNING)
        }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }
}
