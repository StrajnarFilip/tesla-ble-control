package io.github.teslablecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.teslablecontrol.data.ThemeMode
import io.github.teslablecontrol.ui.AppRoot
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            AppTheme(settings?.theme ?: ThemeMode.SYSTEM) {
                AppRoot(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppVisible()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onAppHidden()
    }
}
