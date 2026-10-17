// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.SystemAppClock
import app.backpin.focus.notifications.BandScheduler
import app.backpin.focus.notifications.NotificationCoordinator
import app.backpin.focus.notifications.RepeatNotificationCoordinator
import app.backpin.focus.notifications.ScheduleReleaseScheduler
import app.backpin.focus.repeats.RepeatsApi
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.ui.FocusStackViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Constructor-injection root. No DI framework.
 */
class FocusStackApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, Configuration.Builder().build())
        }
        container = AppContainer(this, appScope)
        container.notifications.start(appScope)
        container.repeatNotifications.start(appScope)
        container.releaseScheduler.ensure()
        appScope.launch {
            container.preferences.ensureDefaultsStored()
            container.releaseDue()
            container.bandScheduler.resyncNow()
        }
    }

    /**
     * Each time the activity is visible, due scheduled tasks take a free Today slot and a
     * countdown on its day comes into focus. No paused task resumes.
     */
    fun onForeground() {
        container.notifications.setAppVisible(true)
        appScope.launch { container.releaseDue() }
    }

    fun onBackground() {
        container.notifications.setAppVisible(false)
    }
}

class AppContainer(
    app: Application,
    scope: CoroutineScope,
) {
    val preferences = PreferencesDataStore(app)
    private val database = FocusStackDatabase.create(app)
    val repository = FocusRepositoryImpl(database, preferences, SystemAppClock)
    val releaseScheduler = ScheduleReleaseScheduler(WorkManager.getInstance(app))
    val bandScheduler = BandScheduler(app, repository, scope)
    val useCases = FocusUseCases(repository, releaseScheduler)
    val repeats: RepeatsApi = RepeatsService(repository, bandScheduler, bandScheduler)
    val viewModelFactory = FocusStackViewModelFactory(useCases, repeats)
    val notifications = NotificationCoordinator(app, repository)
    val repeatNotifications = RepeatNotificationCoordinator(app, repository, bandScheduler)

    /** Runs both inexact release checks and posts the arrival notice when a countdown came into focus. */
    suspend fun releaseDue() {
        repository.releaseDueScheduled()
        repository.releaseDueCountdowns()?.let { notifications.postCountdownArrival(it.title) }
    }
}
