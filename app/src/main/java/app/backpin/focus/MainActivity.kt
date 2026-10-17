// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.backpin.focus.notifications.NotificationContract
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var launchDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchDestination = intent.getStringExtra(NotificationContract.EXTRA_DESTINATION)
        val app = application as FocusStackApp
        val factory = app.container.viewModelFactory
        val preferences = app.container.preferences
        lifecycleScope.launch {
            val initialTheme = preferences.themeId.first()
            val initialLayout = preferences.homeLayout.first()
            setContent {
                val themeId by preferences.themeId.collectAsStateWithLifecycle(initialTheme)
                val homeLayout by preferences.homeLayout.collectAsStateWithLifecycle(initialLayout)
                val scope = rememberCoroutineScope()
                FocusStackTheme(themeId = themeId) {
                    val viewModel: FocusStackViewModel = viewModel(factory = factory)
                    FocusApp(
                        viewModel = viewModel,
                        launchDestination = launchDestination,
                        onLaunchDestinationConsumed = { launchDestination = null },
                        themeId = themeId,
                        onThemeId = { id -> scope.launch { preferences.setThemeId(id) } },
                        homeLayout = homeLayout,
                        onHomeLayout = { layout -> scope.launch { preferences.setHomeLayout(layout) } },
                        showHomeLayout = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "preview",
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchDestination = intent.getStringExtra(NotificationContract.EXTRA_DESTINATION)
    }

    override fun onStart() {
        super.onStart()
        (application as FocusStackApp).onForeground()
    }

    override fun onStop() {
        (application as FocusStackApp).onBackground()
        super.onStop()
    }
}
