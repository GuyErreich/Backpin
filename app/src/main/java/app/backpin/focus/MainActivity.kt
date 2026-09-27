package app.backpin.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.backpin.focus.notifications.NotificationContract
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme

class MainActivity : ComponentActivity() {
    private var launchDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchDestination = intent.getStringExtra(NotificationContract.EXTRA_DESTINATION)
        val factory = (application as FocusStackApp).container.viewModelFactory
        setContent {
            FocusStackTheme {
                val viewModel: FocusStackViewModel = viewModel(factory = factory)
                FocusApp(
                    viewModel = viewModel,
                    launchDestination = launchDestination,
                    onLaunchDestinationConsumed = { launchDestination = null },
                )
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
