package dev.fabik.bluetoothhid.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.SettingsActivity
import dev.fabik.bluetoothhid.bt.BluetoothService
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.exportPreferences
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.importPreferences
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DevicesDropdown() {
    Dropdown {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        DropdownMenuItem(
            text = { Text("Reset favourites") },
            leadingIcon = { Icon(Icons.Outlined.Restore, null) },
            onClick = {
                scope.launch {
                    context.setPreference(PreferenceStore.FAVOURITE_DEVICES, setOf())
                }
            }
        )
    }
}

@Composable
fun Dropdown(transparent: Boolean = false, content: @Composable () -> Unit = {}) {
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
            Icon(
                Icons.Default.MoreVert,
                "More options",
                tint = if (transparent) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = if (transparent) {
                    Modifier.drawBehind {
                        // Draw shadow behind icon for better visibility
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = size.maxDimension
                        )
                    }
                } else Modifier
            )
        }

        DropdownMenu(
            expanded = showMenu,
            modifier = Modifier.widthIn(min = 150.dp),
            onDismissRequest = { showMenu = false }
        ) {
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

            content()
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

            if (connectionMode == ConnectionMode.RFCOMM.ordinal) {
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
            } else {
                QosOptionsModal()
            }

            ImportExportDropdown()
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

@Composable
fun ImportExportDropdown() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.IO }

    var exportData = ""
    val exportPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { result ->
            result?.let { uri ->
                scope.launch {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.bufferedWriter().use {
                                it.write(exportData)
                            }
                        }
                    }.onFailure {
                        Log.e("Settings", "Error saving settings to file!", it)
                    }
                }.invokeOnCompletion {
                    exportData = ""
                }
            }
        }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.export_settings)) },
        onClick = {
            scope.launch {
                runCatching {
                    exportData = context.exportPreferences()
                    exportPickerLauncher.launch("settings.json")
                }.onFailure {
                    Log.e("Settings", "Failed to export settings!", it)
                }
            }
        }
    )

    val importPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            result?.let { uri ->
                scope.launch {
                    var count = 0
                    runCatching {
                        val content = context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        } ?: ""
                        count = context.importPreferences(content)
                    }.onFailure {
                        Log.e("Settings", "Error importing settings!", it)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Imported $count settings!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    val confirmDialog = rememberDialogState()

    ConfirmDialog(
        dialogState = confirmDialog,
        title = stringResource(R.string.import_settings),
        onConfirm = {
            runCatching {
                importPickerLauncher.launch(arrayOf("application/json"))
                close()
            }.onFailure {
                Log.e("Settings", "Error starting file picker!", it)
            }
        }
    ) {
        Text(stringResource(R.string.import_settings_desc))
    }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.import_settings)) },
        onClick = {
            confirmDialog.open()
        }
    )
}