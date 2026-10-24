package io.github.teslablecontrol.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R

/** Help topics, each with a picture and numbered steps. Error messages link straight to one. */
enum class TroubleTopic(val title: Int, val picture: Picture, val pictureDescription: Int, val steps: List<Int>) {
    NOT_FOUND(
        R.string.trouble_not_found_title, Picture.SCANNING, R.string.guide_find_picture,
        listOf(R.string.trouble_not_found_1, R.string.trouble_not_found_2, R.string.trouble_not_found_3, R.string.trouble_not_found_4),
    ),
    TOO_MANY_CONNECTIONS(
        R.string.trouble_full_title, Picture.TOO_MANY_CONNECTIONS, R.string.trouble_full_picture,
        listOf(R.string.trouble_full_1, R.string.trouble_full_2),
    ),
    PAIRING(
        R.string.trouble_pairing_title, Picture.KEY_CARD_CHARGER, R.string.guide_pair_picture_charger,
        listOf(R.string.trouble_pairing_1, R.string.trouble_pairing_2, R.string.trouble_pairing_3, R.string.trouble_pairing_4),
    ),
    NOT_PAIRED(
        R.string.trouble_unpaired_title, Picture.KEY_CARD_CONSOLE, R.string.guide_pair_picture_console,
        listOf(R.string.trouble_unpaired_1, R.string.trouble_unpaired_2, R.string.trouble_unpaired_3),
    ),
    ASLEEP(
        R.string.trouble_asleep_title, Picture.ASLEEP, R.string.trouble_asleep_picture,
        listOf(R.string.trouble_asleep_1, R.string.trouble_asleep_2, R.string.trouble_asleep_3),
    ),
    BLUETOOTH(
        R.string.trouble_bluetooth_title, Picture.BLUETOOTH_OFF, R.string.trouble_bluetooth_picture,
        listOf(R.string.trouble_bluetooth_1, R.string.trouble_bluetooth_2, R.string.trouble_bluetooth_3),
    ),
    SEVERAL_CARS(
        R.string.trouble_cars_title, Picture.WHAT_YOU_NEED, R.string.guide_need_picture,
        listOf(R.string.trouble_cars_1, R.string.trouble_cars_2, R.string.trouble_cars_3),
    ),
}

@Composable
fun TroubleshootingContent(initial: TroubleTopic?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(TroubleTopic.entries) { topic -> TopicCard(topic, expandedInitially = topic == initial) }
    }
}

@Composable
private fun TopicCard(topic: TroubleTopic, expandedInitially: Boolean) {
    var expanded by rememberSaveable(topic) { mutableStateOf(expandedInitially) }
    val expandedText = stringResource(if (expanded) R.string.action_close else R.string.action_help)
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = expandedText },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(
                    stringResource(topic.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Spacer(Modifier.width(8.dp))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Illustration(topic.picture, stringResource(topic.pictureDescription))
                    topic.steps.forEachIndexed { index, step ->
                        Row {
                            Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(28.dp))
                            Text(stringResource(step), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
