package app.backpin.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = (application as FocusStackApp).container.viewModelFactory
        setContent {
            FocusStackTheme {
                val viewModel: FocusStackViewModel = viewModel(factory = factory)
                FocusApp(viewModel)
            }
        }
    }
}
