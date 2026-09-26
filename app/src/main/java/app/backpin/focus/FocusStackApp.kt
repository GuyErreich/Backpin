package app.backpin.focus

import android.app.Application
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.SystemAppClock
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
        container = AppContainer(this)
        appScope.launch {
            container.preferences.ensureDefaultsStored()
        }
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
}
