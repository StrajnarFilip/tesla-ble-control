package io.github.teslablecontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.teslablecontrol.ui.guide.SetupGuideScreen

/** The setup guide on first run, the main screens afterwards; a blank background while settings load. */
@Composable
fun AppRoot(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    // Leaving the guide shows the main screens at once instead of waiting for the saved setting.
    var leftGuide by rememberSaveable { mutableStateOf(false) }
    val s = settings
    when {
        s == null || profiles == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        !s.onboardingDone && !leftGuide -> SetupGuideScreen(
            viewModel = viewModel,
            showWelcome = true,
            onFinished = {
                leftGuide = true
                viewModel.updateSettings { it.copy(onboardingDone = true, demoMode = false) }
            },
            onTryDemo = {
                leftGuide = true
                viewModel.updateSettings { it.copy(onboardingDone = true, demoMode = true) }
            },
            onClose = null,
        )
        else -> MainScaffold(viewModel)
    }
}
