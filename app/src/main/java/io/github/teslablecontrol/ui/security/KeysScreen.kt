package io.github.teslablecontrol.ui.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tesla.generated.keys.Keys
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.R
import io.github.teslablecontrol.core.CommandOutcome
import io.github.teslablecontrol.keys.KeyManager
import io.github.teslablecontrol.protocol.crypto.KeyCodec
import io.github.teslablecontrol.protocol.toHex
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.KeyEntry
import io.github.teslablecontrol.ui.AddCarRoute
import io.github.teslablecontrol.ui.BackScaffold
import io.github.teslablecontrol.ui.CommandRoute
import io.github.teslablecontrol.ui.MainViewModel
import io.github.teslablecontrol.ui.Navigator
import io.github.teslablecontrol.ui.UiMessage
import io.github.teslablecontrol.ui.components.ButtonWithNote
import io.github.teslablecontrol.ui.components.ConfirmDialog
import io.github.teslablecontrol.ui.components.InfoRow
import io.github.teslablecontrol.ui.components.ItemRow
import io.github.teslablecontrol.ui.components.SectionGroup
import io.github.teslablecontrol.ui.components.SectionHeader
import io.github.teslablecontrol.ui.components.inGroup
import io.github.teslablecontrol.ui.format.FailureAction
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class KeyDialog { EXPORT, IMPORT, NEW }

/** The key ID cars use: the first 4 bytes of the public key's SHA-1. */
fun keyId(publicKey: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(publicKey).copyOf(4).toHex()

@Composable
fun KeysScreen(viewModel: MainViewModel, navigator: Navigator) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyStatus by viewModel.keyStatus.collectAsState()
    val running by viewModel.running.collectAsState()
    val ownPublicKey = remember(keyStatus) { viewModel.app.keys.currentKey()?.publicKey }
    var carKeys by remember { mutableStateOf<List<KeyEntry>?>(null) }
    var removing by remember { mutableStateOf<KeyEntry?>(null) }
    var dialog by remember { mutableStateOf<KeyDialog?>(null) }

    fun loadKeys() = viewModel.run("list-keys") { outcome ->
        ((outcome as? CommandOutcome.Success)?.result as? CommandResult.KeyList)?.let { carKeys = it.keys }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = runCatching {
                withContext(Dispatchers.IO) {
                    val pem = viewModel.app.keys.exportPrivatePem() ?: error("no key")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(pem.toByteArray()) } ?: error("can't open the file")
                }
            }
            viewModel.emit(
                written.fold(
                    onSuccess = { UiMessage(resources.getString(R.string.keys_saved)) },
                    onFailure = { UiMessage(resources.getString(R.string.keys_save_failed, it.message), isError = true) },
                ),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readAtMost(64 * 1024).decodeToString() } ?: error("empty file")
                }
            }.fold(
                onSuccess = viewModel::importKey,
                onFailure = { viewModel.emit(UiMessage(resources.getString(R.string.keys_read_failed, it.message), isError = true)) },
            )
        }
    }

    removing?.let { entry ->
        val own = ownPublicKey != null && entry.publicKey.contentEquals(ownPublicKey)
        ConfirmDialog(
            title = stringResource(R.string.keys_remove_title),
            text = stringResource(if (own) R.string.keys_remove_self else R.string.keys_remove_confirm),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                removing = null
                viewModel.run("remove-key", mapOf("PUBLIC_KEY" to entry.publicKey.toHex())) { outcome ->
                    if (outcome is CommandOutcome.Success) loadKeys()
                }
            },
            onDismiss = { removing = null },
        )
    }

    when (dialog) {
        KeyDialog.EXPORT -> ConfirmDialog(
            title = stringResource(R.string.keys_export_title),
            text = stringResource(R.string.keys_export_warning),
            confirmLabel = stringResource(R.string.keys_export_confirm),
            onConfirm = {
                dialog = null
                exportLauncher.launch("tesla-ble-private-key.pem")
            },
            onDismiss = { dialog = null },
        )
        KeyDialog.IMPORT -> ConfirmDialog(
            title = stringResource(R.string.keys_import_title),
            text = stringResource(R.string.keys_import_warning),
            confirmLabel = stringResource(R.string.keys_import_confirm),
            onConfirm = {
                dialog = null
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { dialog = null },
        )
        KeyDialog.NEW -> ConfirmDialog(
            title = stringResource(R.string.keys_new_title),
            text = stringResource(R.string.keys_new_warning),
            confirmLabel = stringResource(R.string.keys_new_confirm),
            onConfirm = {
                dialog = null
                carKeys = null
                viewModel.createNewKey()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    BackScaffold(stringResource(R.string.keys_title), navigator) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(R.string.keys_this_phone))
                SectionGroup {
                    Text(
                        stringResource(
                            when (keyStatus) {
                                KeyManager.Status.READY -> R.string.keys_status_ready
                                KeyManager.Status.NONE -> R.string.keys_status_none
                                KeyManager.Status.UNUSABLE -> R.string.keys_status_unusable
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (keyStatus == KeyManager.Status.UNUSABLE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (ownPublicKey != null) {
                        val pem = remember(ownPublicKey) { KeyCodec.publicKeyToPem(ownPublicKey) }
                        InfoRow(stringResource(R.string.keys_public_key), stringResource(R.string.keys_id, keyId(ownPublicKey)))
                        ButtonWithNote(
                            label = stringResource(R.string.keys_share_public),
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, pem)
                                context.startActivity(Intent.createChooser(send, resources.getString(R.string.keys_share_public)))
                            },
                            note = stringResource(R.string.keys_add_desc),
                            modifier = Modifier.inGroup(),
                        )
                        ButtonWithNote(
                            label = stringResource(R.string.action_copy),
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.keys_public_key), pem))
                                // Android 13 and later confirm copies themselves.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) viewModel.emit(UiMessage(resources.getString(R.string.copied)))
                            },
                            modifier = Modifier.inGroup(),
                        )
                    }
                    ButtonWithNote(
                        label = stringResource(R.string.keys_check),
                        onClick = {
                            viewModel.recheckPairing { paired ->
                                when (paired) {
                                    true -> viewModel.emit(UiMessage(resources.getString(R.string.keys_check_ok)))
                                    false -> viewModel.emit(UiMessage(resources.getString(R.string.failure_not_paired), FailureAction.PAIR, isError = true))
                                    null -> viewModel.emit(UiMessage(resources.getString(R.string.failure_connection_failed), isError = true))
                                }
                            }
                        },
                        busy = running == "session-info",
                        enabled = ownPublicKey != null,
                        modifier = Modifier.inGroup(),
                    )
                    ButtonWithNote(
                        label = stringResource(R.string.keys_pair_again),
                        onClick = { navigator.to(AddCarRoute) },
                        modifier = Modifier.inGroup(),
                    )
                }

                SectionHeader(stringResource(R.string.keys_on_car))
                SectionGroup {
                    // Actions first: loading or removing keys changes only the list below them.
                    ButtonWithNote(
                        label = stringResource(R.string.keys_load),
                        onClick = { loadKeys() },
                        busy = running == "list-keys",
                        modifier = Modifier.inGroup(),
                    )
                    ButtonWithNote(
                        label = stringResource(R.string.keys_add),
                        onClick = { navigator.to(CommandRoute("add-key")) },
                        note = stringResource(R.string.keys_add_desc),
                        modifier = Modifier.inGroup(),
                    )
                    val list = carKeys
                    if (list == null) {
                        Text(
                            stringResource(R.string.keys_none_loaded),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    list?.forEach { entry ->
                        val own = ownPublicKey != null && entry.publicKey.contentEquals(ownPublicKey)
                        ItemRow(
                            title = listOfNotNull(
                                stringResource(R.string.keys_slot, entry.slot),
                                roleLabel(entry.role),
                                stringResource(R.string.keys_this_phone_marker).takeIf { own },
                            ).joinToString(" · "),
                            description = "${formFactorLabel(entry.formFactor)} · ${stringResource(R.string.keys_id, keyId(entry.publicKey))}",
                            actionLabel = stringResource(R.string.action_remove),
                            onAction = { removing = entry },
                            busy = running == "remove-key",
                        )
                    }
                }

                SectionHeader(stringResource(R.string.keys_advanced))
                SectionGroup {
                    ButtonWithNote(
                        label = stringResource(R.string.keys_import),
                        onClick = { dialog = KeyDialog.IMPORT },
                        note = stringResource(R.string.keys_import_desc),
                        modifier = Modifier.inGroup(),
                    )
                    ButtonWithNote(
                        label = stringResource(R.string.keys_export_private),
                        onClick = { dialog = KeyDialog.EXPORT },
                        note = stringResource(R.string.keys_export_warning),
                        enabled = ownPublicKey != null,
                        modifier = Modifier.inGroup(),
                    )
                    ButtonWithNote(
                        label = stringResource(R.string.keys_new),
                        onClick = { dialog = KeyDialog.NEW },
                        note = stringResource(R.string.keys_new_warning),
                        destructive = true,
                        modifier = Modifier.inGroup(),
                    )
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/** Reads up to [limit] bytes; key files are small, and this keeps a wrong pick from filling memory. */
private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var filled = 0
    while (filled < limit) {
        val read = read(buffer, filled, limit - filled)
        if (read < 0) break
        filled += read
    }
    return buffer.copyOf(filled)
}

@Composable
private fun roleLabel(role: Keys.Role): String = when (role) {
    Keys.Role.ROLE_OWNER -> stringResource(R.string.keys_role_owner)
    Keys.Role.ROLE_DRIVER -> stringResource(R.string.keys_role_driver)
    Keys.Role.ROLE_FM -> "Fleet manager"
    Keys.Role.ROLE_VEHICLE_MONITOR -> "Vehicle monitor"
    Keys.Role.ROLE_CHARGING_MANAGER -> "Charging manager"
    Keys.Role.ROLE_GUEST -> "Guest"
    Keys.Role.ROLE_SERVICE -> "Service"
    else -> stringResource(R.string.keys_role_other)
}

@Composable
private fun formFactorLabel(formFactor: Vcsec.KeyFormFactor): String = stringResource(
    when (formFactor) {
        Vcsec.KeyFormFactor.KEY_FORM_FACTOR_NFC_CARD -> R.string.keys_form_card
        Vcsec.KeyFormFactor.KEY_FORM_FACTOR_IOS_DEVICE -> R.string.keys_form_ios
        Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE -> R.string.keys_form_android
        Vcsec.KeyFormFactor.KEY_FORM_FACTOR_CLOUD_KEY -> R.string.keys_form_cloud
        else -> R.string.keys_form_other
    },
)
