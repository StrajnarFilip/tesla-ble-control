package io.github.teslablecontrol.tile

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teslablecontrol.MainActivity
import io.github.teslablecontrol.R
import io.github.teslablecontrol.container
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.core.FailureKind
import io.github.teslablecontrol.data.VehicleProfile
import io.github.teslablecontrol.ui.commands.CommandInfos
import io.github.teslablecontrol.ui.components.DialogButtons
import io.github.teslablecontrol.ui.format.failureMessage
import io.github.teslablecontrol.ui.format.stageText
import io.github.teslablecontrol.ui.theme.AppTheme
import io.github.teslablecontrol.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Runs the tile's command once per tap; survives rotation so the result isn't lost. */
class QuickCommandModel(application: Application) : AndroidViewModel(application) {
    val profile = MutableStateFlow<VehicleProfile?>(null)
    val outcome = MutableStateFlow<CommandOutcome?>(null)
    private var started = false

    fun start(command: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            val app = getApplication<Application>().container
            // The process may have just started for this tap, so wait for the stores to load.
            val car = app.activeProfileFor(app.settings.awaitLoaded(), app.profiles.profiles.filterNotNull().first())
            if (car == null) {
                outcome.value = CommandOutcome.Failure(FailureKind.NOT_PAIRED, "no car set up")
                return@launch
            }
            profile.value = car
            outcome.value = app.controllerFor(car).execute(command)
        }
    }
}

/** Connects, runs one command from the Quick Settings tile, shows the result and closes itself. */
class QuickCommandActivity : ComponentActivity() {
    private val model: QuickCommandModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = container
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: return finish()
        model.start(command)
        setContent {
            AppTheme(app.settings.current.theme) {
                val profile by model.profile.collectAsState()
                val outcome by model.outcome.collectAsState()
                val activity = profile?.let { app.controllerFor(it).state.collectAsState().value.activity }
                LaunchedEffect(outcome) {
                    if (outcome is CommandOutcome.Success) {
                        delay(1500)
                        finish()
                    }
                }
                QuickCommandCard(
                    title = CommandInfos.get(command).title,
                    carName = profile?.name.orEmpty(),
                    progress = activity?.let { stageText(LocalContext.current, it.stage) },
                    outcome = outcome,
                    successText = CommandInfos.get(command).successText,
                    onClose = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_COMMAND = "command"

        fun intent(context: Context, command: String): Intent =
            Intent(context, QuickCommandActivity::class.java).putExtra(EXTRA_COMMAND, command)
    }
}

@Composable
private fun QuickCommandCard(
    title: String,
    carName: String,
    progress: String?,
    outcome: CommandOutcome?,
    successText: String,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(carName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.headlineSmall)
                val message = when (outcome) {
                    null -> progress ?: stringResource(R.string.status_connecting)
                    is CommandOutcome.Success -> successText
                    is CommandOutcome.Failure -> failureMessage(context, outcome)
                }
                when (outcome) {
                    null -> CircularProgressIndicator(Modifier.size(48.dp))
                    is CommandOutcome.Success -> Icon(
                        Icons.Outlined.CheckCircle, null, Modifier.size(48.dp), tint = LocalStatusColors.current.success,
                    )
                    is CommandOutcome.Failure -> Icon(
                        Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                // Always shown, so the card keeps its size when the result comes in.
                DialogButtons(stringResource(R.string.action_close), onClose, stringResource(R.string.action_open_app), onOpenApp)
            }
        }
    }
}
