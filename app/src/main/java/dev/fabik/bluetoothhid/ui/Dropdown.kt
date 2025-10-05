package dev.fabik.bluetoothhid.ui

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.SettingsActivity
import dev.fabik.bluetoothhid.bt.BluetoothService
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.rememberPreference

@Composable
fun Dropdown() {
    val context = LocalContext.current
    val activity = LocalActivity.current

    var showMenu by rememberSaveable {
        mutableStateOf(false)
    }

    val developerMode by context.getPreferenceState(PreferenceStore.DEVELOPER_MODE)

    Box {
        IconButton(
            onClick = { showMenu = !showMenu },
            modifier = Modifier.tooltip(stringResource(R.string.more))
        ) {
            Icon(Icons.Default.MoreVert, "More options")
        }

        DropdownMenu(
            expanded = showMenu,
            modifier = Modifier.widthIn(min = 150.dp),
            onDismissRequest = { showMenu = false }) {
            if (developerMode == true) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.refresh_proxy)) },
                    onClick = {
                        showMenu = false
                        context.startForegroundService(
                            Intent(
                                context,
                                BluetoothService::class.java
                            ).apply {
                                action = BluetoothService.ACTION_REGISTER
                            }
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stop_proxy)) },
                    onClick = {
                        showMenu = false
                        context.startService(
                            Intent(
                                context,
                                BluetoothService::class.java
                            ).apply {
                                action = BluetoothService.ACTION_STOP
                            }
                        )
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                onClick = {
                    showMenu = false
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exit)) },
                leadingIcon = { Icon(Icons.Default.Close, null) },
                onClick = {
                    activity?.finishAfterTransition()
                }
            )
        }
    }
}

@Composable
fun SettingsDropdown() {
    var showMenu by rememberSaveable {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    var developerMode by rememberPreference(PreferenceStore.DEVELOPER_MODE)
    var ocrSupport by rememberPreference(PreferenceStore.OCR_COMPAT)
    var insecureRfcomm by rememberPreference(PreferenceStore.INSECURE_RFCOMM)
    val connectionMode by context.getPreferenceState(PreferenceStore.CONNECTION_MODE)

    val ocrInfoDialog = rememberDialogState()
    val insecureRfcommDialog = rememberDialogState()

    Box {
        IconButton(
            onClick = { showMenu = !showMenu },
            modifier = Modifier.tooltip(stringResource(R.string.more))
        ) {
            Icon(Icons.Default.MoreVert, "More options")
        }

        DropdownMenu(
            expanded = showMenu,
            modifier = Modifier.widthIn(min = 150.dp),
            onDismissRequest = { showMenu = false }) {

            DropdownMenuItem(
                text = { Text(stringResource(R.string.developer_mode)) },
                trailingIcon = {
                    Checkbox(
                        checked = developerMode,
                        onCheckedChange = null
                    )
                },
                onClick = {
                    developerMode = !developerMode
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.ext_ocr_support)) },
                trailingIcon = {
                    Checkbox(
                        checked = ocrSupport,
                        onCheckedChange = null
                    )
                },
                onClick = {
                    if (!ocrSupport) {
                        ocrInfoDialog.open()
                    }
                    ocrSupport = !ocrSupport
                }
            )

            if (connectionMode == 1) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.insecure_rfcomm)) },
                    leadingIcon = { Icon(Icons.Default.Shield, null) },
                    trailingIcon = {
                        Checkbox(
                            checked = insecureRfcomm,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        if (!insecureRfcomm) {
                            insecureRfcommDialog.open()
                        }
                        insecureRfcomm = !insecureRfcomm
                    }
                )

                var preserveUnsupportedPlaceholders by rememberPreference(PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.preserve_unsupported_placeholders)) },
                    leadingIcon = { Icon(Icons.Default.DataObject, null) },
                    trailingIcon = {
                        Checkbox(
                            checked = preserveUnsupportedPlaceholders,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        preserveUnsupportedPlaceholders = !preserveUnsupportedPlaceholders
                    }
                )
            }
        }
    }

    InfoDialog(
        ocrInfoDialog, stringResource(R.string.note),
        icon = { Icon(Icons.Default.Info, null) }
    ) {
        val uriHandler = LocalUriHandler.current

        Column {
            Text(stringResource(R.string.ocr_note_desc))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton({
                    uriHandler.openUri("https://github.com/mtotschnig/OCR")
                }) { Text(stringResource(R.string.ocr_app_github)) }
            }

            Text(stringResource(R.string.ocr_note_bottom))
        }
    }

    InfoDialog(
        insecureRfcommDialog, stringResource(R.string.note),
        icon = { Icon(Icons.Default.Shield, null) }
    ) {
        Column {
            Text(stringResource(R.string.insecure_rfcomm_desc))
        }
    }
}
