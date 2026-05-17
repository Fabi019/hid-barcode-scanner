package dev.fabik.bluetoothhid.ui

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.bt.Key
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.bt.Keymap
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun CustomKeysDialog(dialogState: DialogState) {
    val context = LocalContext.current

    val keyMap = remember(dialogState.openState) {
        // Internally checks if it is already loaded once
        KeyTranslator.loadCustomKeyMap(context)
        mutableStateMapOf<Char, Pair<Key, Key?>>().apply {
            putAll(KeyTranslator.CUSTOM_KEYMAP.toMap())
        }
    }

    ConfirmResetDialog(
        dialogState = dialogState,
        title = stringResource(R.string.custom_keys),
        onDismiss = {
            close()
        },
        onReset = {
            keyMap.clear()
        },
        onConfirm = {
            KeyTranslator.CUSTOM_KEYMAP = keyMap
            KeyTranslator.saveCustomKeyMap(context)
            close()
        }
    ) {
        CustomKeys(keyMap, { (char, key) ->
            keyMap[char] = key
        }, { (char, _) ->
            keyMap.remove(char)
        }, { map ->
            keyMap.clear()
            keyMap.putAll(map)
        })
    }
}

@Composable
fun AddCustomKeyDialog(
    dialogState: DialogState,
    initialChar: String = "",
    initialHID: UByte? = null,
    initialModifier: UByte? = null,
    onAddKey: (Pair<Char, Key>) -> Unit
) {
    var valueChar by rememberSaveable(dialogState.openState) { mutableStateOf(initialChar) }
    var valueHID by rememberSaveable(dialogState.openState) { mutableStateOf(initialHID) }
    var valueModifier by rememberSaveable(dialogState.openState) { mutableStateOf(initialModifier) }

    val modifierNames = remember { arrayOf("Ctrl", "Shift", "Alt") }
    val modifierCheckedStates = remember(dialogState.openState, valueModifier) {
        mutableStateListOf(
            (valueModifier ?: 0u) and KeyTranslator.LCTRL == KeyTranslator.LCTRL,
            (valueModifier ?: 0u) and KeyTranslator.LSHIFT == KeyTranslator.LSHIFT,
            (valueModifier ?: 0u) and KeyTranslator.LALT == KeyTranslator.LALT
        )
    }
    val modifierNameStates =
        remember(modifierCheckedStates) { modifierNames.zip(modifierCheckedStates) }

    val currentKey = remember(valueChar, valueHID, valueModifier) {
        val char = valueChar.firstOrNull() ?: return@remember null
        val hidCode = valueHID ?: return@remember null
        val modifier = valueModifier ?: return@remember null
        char to (modifier to hidCode)
    }

    ConfirmDialog(
        dialogState = dialogState,
        title = stringResource(R.string.add_custom_key),
        enabled = currentKey != null,
        onDismiss = {
            close()
        },
        onConfirm = {
            currentKey?.let {
                onAddKey(it)
                close()
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.add_custom_key_desc_long))

            Spacer(Modifier.height(8.dp))

            TextField(
                value = valueChar,
                onValueChange = {
                    if (it.length <= 1) {
                        valueChar = it
                        // preselect shift state
                        valueModifier = if (it.firstOrNull()?.isUpperCase() == true) {
                            (valueModifier ?: 0u) or KeyTranslator.LSHIFT
                        } else {
                            (valueModifier ?: 0u) and KeyTranslator.LSHIFT.inv()
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.character)) }
            )

            Spacer(Modifier.height(4.dp))

            TextField(
                value = valueHID?.toString(16) ?: "",
                onValueChange = { valueHID = it.toUByteOrNull(16) },
                modifier = Modifier.padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.hid_code_hex)) }
            )

            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.modifier), style = MaterialTheme.typography.titleMedium)

            TextField(
                value = valueModifier?.toString() ?: "",
                onValueChange = { valueModifier = it.toUByteOrNull() },
                modifier = Modifier.padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.modifier)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                itemsIndexed(modifierNameStates) { index, (name, checked) ->
                    Row(
                        Modifier
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { isChecked ->
                                    modifierCheckedStates[index] = isChecked

                                    valueModifier = if (modifierCheckedStates[0]) {
                                        (valueModifier ?: 0u) or KeyTranslator.LCTRL
                                    } else {
                                        (valueModifier ?: 0u) and KeyTranslator.LCTRL.inv()
                                    }

                                    valueModifier = if (modifierCheckedStates[1]) {
                                        (valueModifier ?: 0u) or KeyTranslator.LSHIFT
                                    } else {
                                        (valueModifier ?: 0u) and KeyTranslator.LSHIFT.inv()
                                    }

                                    valueModifier = if (modifierCheckedStates[2]) {
                                        (valueModifier ?: 0u) or KeyTranslator.LALT
                                    } else {
                                        (valueModifier ?: 0u) and KeyTranslator.LALT.inv()
                                    }
                                }
                            ), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = null, Modifier.padding(8.dp))
                        Text(name)
                    }
                }
            }

            val context = LocalContext.current

            // Settings activity does not have the bluetooth controller via context
            val controller = rememberBluetoothControllerService(context, false)
            val currentDevice by controller?.getController()?.currentDevice?.collectAsStateWithLifecycle()
                ?: remember { mutableStateOf(null) }

            Button(
                onClick = {
                    runCatching {
                        val hidCode = valueHID ?: return@runCatching
                        val modifier = valueModifier ?: return@runCatching

                        CoroutineScope(Dispatchers.IO).launch {
                            controller?.getController()?.keyboardSender?.sendKey(
                                modifier to hidCode
                            )
                        }
                    }.onFailure {
                        Log.e("CustomKeys", "Error sending to PC:", it)
                    }
                },
                enabled = currentDevice != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.test))
            }

            Text(
                stringResource(R.string.test_key), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun CustomKeys(
    keyMap: Keymap,
    onAddKey: (Pair<Char, Pair<Key, Key?>>) -> Unit,
    onDeleteKey: (Pair<Char, Pair<Key, Key?>>) -> Unit,
    onImportKeys: (Keymap) -> Unit
) {
    val addKeyDialog = rememberDialogState()

    var initialChar by remember { mutableStateOf("") }
    var initialHID by remember { mutableStateOf<UByte?>(null) }
    var initialModifier by remember { mutableStateOf<UByte?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Text(stringResource(R.string.add_custom_key_desc))
        }

        item {
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    initialChar = ""
                    initialHID = null
                    initialModifier = null
                    addKeyDialog.open()
                }, modifier = Modifier.weight(1.0f)) {
                    Icon(Icons.Default.Add, null)
                    Text(stringResource(R.string.add))
                }
                ImportExportButtons(keyMap, onImportKeys)
            }
        }

        items(keyMap.toList(), key = { i -> i.first }) { item ->
            ListItem(
                headlineContent = { Text(item.first.toString()) },
                supportingContent = {
                    Text(
                        item.second.first.toString() +
                                ((item.second.second?.let { ", $it" } ?: "")))
                },
                trailingContent = {
                    IconButton(onClick = { onDeleteKey(item) }) {
                        Icon(Icons.Default.Delete, "Delete $item")
                    }
                },
                modifier = Modifier
                    .animateItem()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        initialChar = item.first.toString()
                        initialHID = item.second.first.second
                        initialModifier = item.second.first.first
                        addKeyDialog.open()
                    }
            )
        }
    }

    AddCustomKeyDialog(addKeyDialog, initialChar, initialHID, initialModifier) {
        onAddKey(it.first to (it.second to null))
    }
}

@Composable
private fun ImportExportButtons(keyMap: Keymap, onImportKeys: (Keymap) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val confirmDialog = rememberDialogState()

    var importing by remember { mutableStateOf(false) }

    val exportPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { result ->
            result?.let { uri ->
                scope.launch {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.bufferedWriter().use {
                                KeyTranslator.keymapToString(keyMap).forEach { l ->
                                    it.write(l)
                                    it.newLine()
                                }
                            }
                        }
                    }.onFailure {
                        Log.e("CustomKeys", "Error saving custom keys to file!", it)
                    }
                }
            }
        }

    val importPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            result?.let { uri ->
                scope.launch {
                    importing = true

                    var count = 0
                    runCatching {
                        val content = context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        } ?: ""
                        val map = KeyTranslator.loadKeymap(content.lines())
                        count = map.size
                        if (count > 0) {
                            onImportKeys(map)
                        }
                    }.onFailure {
                        Log.e("Settings", "Error importing custom keys!", it)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Imported $count keys!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.invokeOnCompletion {
                    importing = false
                }
            }
        }

    IconButton(
        onClick = {
            confirmDialog.open()
        },
        enabled = !importing,
        modifier = Modifier.tooltip(stringResource(R.string.import_keys))
    ) {
        Icon(Icons.Default.Upload, stringResource(R.string.import_keys))
    }

    IconButton(
        onClick = {
            runCatching {
                exportPickerLauncher.launch("custom.layout")
            }.onFailure {
                Log.e("CustomKeys", "Error starting file picker!", it)
            }
        },
        enabled = keyMap.isNotEmpty(),
        modifier = Modifier.tooltip(stringResource(R.string.export_keys))
    ) {
        Icon(Icons.Default.Download, stringResource(R.string.export_keys))
    }

    ConfirmDialog(
        dialogState = confirmDialog,
        title = stringResource(R.string.import_keys),
        onConfirm = {
            runCatching {
                importPickerLauncher.launch(arrayOf("*/*"))
                close()
            }.onFailure {
                Log.e("CustomKeys", "Error starting file picker!", it)
            }
        }
    ) {
        Text(stringResource(R.string.import_keys_desc))
    }
}

@Preview
@Composable
fun PreviewCustomKeys() {
    val keyMap = mapOf(
        'a' to ((0x12.toUByte() to 0x1.toUByte()) to (0x44.toUByte() to 0x55.toUByte())),
        'b' to ((0x22.toUByte() to 0x2.toUByte()) to null),
    )

    Surface {
        CustomKeys(keyMap, {}, {}, {})
    }
}