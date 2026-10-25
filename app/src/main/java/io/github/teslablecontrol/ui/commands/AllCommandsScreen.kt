package io.github.teslablecontrol.ui.commands

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.protocol.commands.SessionRequirement
import io.github.teslablecontrol.ui.BackScaffold
import io.github.teslablecontrol.ui.CommandRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.components.ButtonWithNote
import io.github.teslablecontrol.ui.components.SectionHeader

/** Every command, grouped and searchable; each opens a form built from its parameters. */
@Composable
fun AllCommandsScreen(viewModel: MainViewModel, navigator: Navigator) {
    val running by viewModel.running.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val wakes = stringResource(R.string.commands_wakes_car)
    val asleep = stringResource(R.string.commands_works_asleep)
    val modelOnly = stringResource(R.string.commands_model_only)
    val all = remember { CommandInfos.all.filter { CommandCatalog.find(it.id) != null } }
    val resources = LocalResources.current
    val matches = remember(query, resources) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        all.filter { info ->
            val text = "${resources.getString(info.title)} ${resources.getString(info.description)} ${info.id} ${resources.getString(info.group.title)}".lowercase()
            words.all { it in text }
        }
    }
    val grouped = remember(matches) { matches.groupBy { it.group }.toSortedMap(compareBy { it.ordinal }) }

    BackScaffold(stringResource(R.string.commands_title), navigator) { modifier ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.commands_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.commands_clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.commands_no_results, query),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                grouped.forEach { (group, infos) ->
                    item(key = group.name) { SectionHeader(stringResource(group.title)) }
                    items(infos, key = { it.id }) { info ->
                        val spec = CommandCatalog[info.id]
                        val notes = listOfNotNull(
                            if (spec.requirement == SessionRequirement.INFOTAINMENT) wakes else asleep,
                            info.models?.joinToString(" / ") { it.label }?.let { modelOnly.format(it) },
                        )
                        ButtonWithNote(
                            label = stringResource(info.title),
                            onClick = { navigator.to(CommandRoute(info.id)) },
                            note = "${stringResource(info.description)}\n${notes.joinToString(" · ")}",
                            busy = running == info.id,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
                if (query.isBlank()) {
                    item { SectionHeader(stringResource(R.string.commands_unavailable)) }
                    items(CommandInfos.unavailableOverBluetooth, key = { it.first }) { (title, reason) ->
                        // Not buttons: these can't be sent from here at all.
                        Column(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
