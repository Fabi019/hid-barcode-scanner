package dev.fabik.bluetoothhid.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.bt.Key
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.bt.Keymap
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or


@Composable
fun CustomKeysDialog(dialogState: DialogState) {
    val context = LocalContext.current

    ConfirmDialog(
        dialogState = dialogState,
        title = stringResource(R.string.custom_keys),
        onDismiss = {
            close()
        },
        onConfirm = {
            KeyTranslator.saveCustomKeyMap(context)
            close()
        }
    ) {
        var keyMap by remember {
            // Internally checks if it is already loaded once
            KeyTranslator.loadCustomKeyMap(context)
            mutableStateOf(KeyTranslator.CUSTOM_KEYMAP.toMap())
        }

        CustomKeys(keyMap, { (char, key) ->
            // Sync changes between the local copy and real
            KeyTranslator.CUSTOM_KEYMAP[char] = key
            keyMap = KeyTranslator.CUSTOM_KEYMAP.toMap()
        }, { (char, _) ->
            KeyTranslator.CUSTOM_KEYMAP.remove(char)
            keyMap = KeyTranslator.CUSTOM_KEYMAP.toMap()
        })
    }
}

@Composable
fun AddCustomKeyDialog(
    dialogState: DialogState,
    initialChar: String = "",
    initialHID: Byte? = null,
    initialModifier: Byte? = null,
    onAddKey: (Pair<Char, Key>) -> Unit
) {
    var valueChar by remember(dialogState.openState) { mutableStateOf(initialChar) }
    var valueHID by remember(dialogState.openState) { mutableStateOf(initialHID) }
    var valueModifier by remember(dialogState.openState) { mutableStateOf(initialModifier) }

    val modifierNames = remember { arrayOf("Ctrl", "Shift", "Alt") }
    val modifierCheckedStates = remember(dialogState.openState, valueModifier) {
        mutableStateListOf(
            (valueModifier ?: 0) and KeyTranslator.LCTRL == KeyTranslator.LCTRL,
            (valueModifier ?: 0) and KeyTranslator.LSHIFT == KeyTranslator.LSHIFT,
            (valueModifier ?: 0) and KeyTranslator.LALT == KeyTranslator.LALT
        )
    }

    val currentKey = remember(valueChar, valueHID, valueModifier) {
        val char = valueChar.firstOrNull() ?: return@remember null
        val hidCode = valueHID ?: return@remember null
        val modifier = valueModifier ?: return@remember null
        char to (modifier to hidCode)
    }

    ConfirmDialog(
        dialogState = dialogState,
        title = stringResource(R.string.add_custom_key),
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
                            (valueModifier ?: 0) or KeyTranslator.LSHIFT
                        } else {
                            (valueModifier ?: 0) and KeyTranslator.LSHIFT.inv()
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.character)) }
            )

            Spacer(Modifier.height(4.dp))

            TextField(
                value = valueHID?.toString(16) ?: "",
                onValueChange = { valueHID = it.toByteOrNull(16) },
                modifier = Modifier.padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.hid_code_hex)) }
            )

            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.modifier), style = MaterialTheme.typography.titleMedium)

            TextField(
                value = valueModifier?.toString() ?: "",
                onValueChange = { valueModifier = it.toByteOrNull() },
                modifier = Modifier.padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.modifier)) }
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                modifierNames.zip(modifierCheckedStates).forEachIndexed { index, (name, checked) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name)
                        Checkbox(checked = checked, onCheckedChange = { isChecked ->
                            modifierCheckedStates[index] = isChecked

                            valueModifier = if (modifierCheckedStates[0]) {
                                (valueModifier ?: 0) or KeyTranslator.LCTRL
                            } else {
                                (valueModifier ?: 0) and KeyTranslator.LCTRL.inv()
                            }

                            valueModifier = if (modifierCheckedStates[1]) {
                                (valueModifier ?: 0) or KeyTranslator.LSHIFT
                            } else {
                                (valueModifier ?: 0) and KeyTranslator.LSHIFT.inv()
                            }

                            valueModifier = if (modifierCheckedStates[2]) {
                                (valueModifier ?: 0) or KeyTranslator.LALT
                            } else {
                                (valueModifier ?: 0) and KeyTranslator.LALT.inv()
                            }
                        })
                    }
                }
            }

            val context = LocalContext.current

            // Settings activity does not have the bluetooth controller via context
            val controller = rememberBluetoothControllerService(context, false)

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
                enabled = controller?.getController()?.currentDevice != null,
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
    keyMap: Keymap, onAddKey: (Pair<Char, Key>) -> Unit, onDeleteKey: (Pair<Char, Key>) -> Unit
) {
    val addKeyDialog = rememberDialogState()

    var initialChar by remember { mutableStateOf("") }
    var initialHID by remember { mutableStateOf<Byte?>(null) }
    var initialModifier by remember { mutableStateOf<Byte?>(null) }

    LazyColumn {
        item {
            Text(stringResource(R.string.add_custom_key_desc))
        }

        item {
            OutlinedButton(onClick = {
                initialChar = ""
                initialHID = null
                initialModifier = null
                addKeyDialog.open()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Add")
            }
        }

        items(keyMap.toList()) { item ->
            ListItem(
                headlineContent = { Text(item.first.toString()) },
                supportingContent = { Text(item.second.toString()) },
                trailingContent = {
                    IconButton(onClick = { onDeleteKey(item) }) {
                        Icon(Icons.Default.Delete, "Delete $item")
                    }
                },
                modifier = Modifier.clickable {
                    initialChar = item.first.toString()
                    initialHID = item.second.second
                    initialModifier = item.second.first
                    addKeyDialog.open()
                }
            )
        }
    }

    AddCustomKeyDialog(addKeyDialog, initialChar, initialHID, initialModifier) {
        onAddKey(it)
    }
}

@Preview
@Composable
fun PreviewCustomKeys() {
    val keyMap = mapOf(
        'a' to (0x123.toByte() to 0x1.toByte()),
        'b' to (0x222.toByte() to 0x2.toByte()),
    )

    Surface {
        CustomKeys(keyMap, {}, {})
    }
}