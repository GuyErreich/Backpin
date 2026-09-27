package app.backpin.focus

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.SystemAppClock
import app.backpin.focus.notifications.NotificationCoordinator
import app.backpin.focus.notifications.ScheduleReleaseScheduler
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
        container = AppContainer(this)
        container.notifications.start(appScope)
        ScheduleReleaseScheduler(WorkManager.getInstance(this)).ensure()
        appScope.launch {
            container.preferences.ensureDefaultsStored()
            container.repository.releaseDueScheduled()
        }
    }

    /** Each time the activity is visible, due scheduled tasks take a free Today slot. Nothing starts. */
    fun onForeground() {
        container.notifications.setAppVisible(true)
        appScope.launch { container.repository.releaseDueScheduled() }
    }

    fun onBackground() {
        container.notifications.setAppVisible(false)
    }
}

class AppContainer(
    app: Application,
) {
    val preferences = PreferencesDataStore(app)
    private val database = FocusStackDatabase.create(app)
    val repository = FocusRepositoryImpl(database, preferences, SystemAppClock)
    val useCases = FocusUseCases(repository)
    val viewModelFactory = FocusStackViewModelFactory(useCases)
    val notifications = NotificationCoordinator(app, repository)
}
