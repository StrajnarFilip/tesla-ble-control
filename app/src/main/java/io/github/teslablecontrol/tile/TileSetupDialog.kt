package io.github.teslablecontrol.tile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ui.components.DialogButtons
import io.github.teslablecontrol.ui.guide.Illustration
import io.github.teslablecontrol.ui.guide.Picture

/**
 * What the Quick Settings tile does and how to add it: Android's one-tap prompt where there is one,
 * and the manual steps always, because some phones show no prompt.
 */
@Composable
fun TileSetupDialog(onResult: (LockTileService.Companion.AddResult) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tile_setup_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Illustration(Picture.TILE, stringResource(R.string.guide_done_picture), height = 120.dp)
                Text(stringResource(R.string.tile_setup_body), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.guide_tile_manual), style = MaterialTheme.typography.bodyMedium)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            DialogButtons(
                stringResource(R.string.action_close), onDismiss,
                stringResource(R.string.tile_setup_add).takeIf { LockTileService.canRequestAdd },
                { LockTileService.requestAdd(context, onResult) },
            )
        },
    )
}
