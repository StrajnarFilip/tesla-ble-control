package io.github.teslablecontrol.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.data.AppSettings
import io.github.teslablecontrol.data.DiagnosticsLog
import io.github.teslablecontrol.protocol.ProtocolLog
import io.github.teslablecontrol.ui.BackScaffold
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.UiMessage
import io.github.teslablecontrol.ui.components.ButtonGrid
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.TeslaButton
import io.github.teslablecontrol.ui.components.ToggleRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The protocol log, newest first, with copy, share and clear. */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, navigator: Navigator) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val log = viewModel.app.diagnostics
    val entries by log.entries.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val s = settings ?: AppSettings()
    val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    fun asText(): String = entries.joinToString("\n") { "${clock.format(Date(it.timeMillis))} ${it.level} ${it.message}" }

    BackScaffold(stringResource(R.string.settings_diagnostics), navigator) { modifier ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(stringResource(R.string.diagnostics_desc), style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    SectionGroup {
                        ToggleRow(
                            title = stringResource(R.string.settings_detailed_log),
                            description = stringResource(R.string.diagnostics_detailed_desc),
                            checked = s.detailedLog,
                            onCheckedChange = { on -> viewModel.updateSettings { it.copy(detailedLog = on) } },
                        )
                        ButtonGrid(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), columns = 3) {
                            TeslaButton(
                                stringResource(R.string.diagnostics_copy),
                                {
                                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Log", asText()))
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) viewModel.emit(UiMessage(resources.getString(R.string.copied)))
                                },
                                enabled = entries.isNotEmpty(),
                            )
                            TeslaButton(
                                stringResource(R.string.diagnostics_share),
                                {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, asText())
                                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.diagnostics_share)))
                                },
                                enabled = entries.isNotEmpty(),
                            )
                            TeslaButton(
                                stringResource(R.string.diagnostics_clear),
                                { log.clear() },
                                enabled = entries.isNotEmpty(),
                            )
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.diagnostics_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(entries.asReversed()) { entry -> LogLine(entry, clock) }
            }
        }
    }
}

@Composable
private fun LogLine(entry: DiagnosticsLog.Entry, clock: SimpleDateFormat) {
    val color = when (entry.level) {
        ProtocolLog.Level.ERROR -> MaterialTheme.colorScheme.error
        ProtocolLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    SelectionContainer {
        Text(
            "${clock.format(Date(entry.timeMillis))}  ${entry.level}\n${entry.message}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
        )
    }
}
