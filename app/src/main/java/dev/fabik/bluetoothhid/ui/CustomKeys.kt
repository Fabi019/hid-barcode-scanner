package dev.fabik.bluetoothhid.ui

import android.util.Log
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.bt.Key
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.bt.Keymap
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.experimental.or


@Composable
fun CustomKeysDialog(dialogState: DialogState) {
    val context = LocalContext.current

    ConfirmDialog(
        dialogState = dialogState,
        title = "Custom keys",
        onDismiss = {
            close()
        }, onConfirm = {
            KeyTranslator.saveCustomKeyMap(context)
            close()
        }
    ) {
        var keyMap by remember { mutableStateOf(KeyTranslator.CUSTOM_KEYMAP.toMap()) }

        CustomKeys(
            keyMap,
            { (char, key) ->
                KeyTranslator.CUSTOM_KEYMAP[char] = key
                keyMap = KeyTranslator.CUSTOM_KEYMAP.toMap()
            },
            { (char, _) ->
                KeyTranslator.CUSTOM_KEYMAP.remove(char)
                keyMap = KeyTranslator.CUSTOM_KEYMAP.toMap()
            }
        )
    }
}

@Composable
fun AddCustomKeyDialog(dialogState: DialogState, onAddKey: (Pair<Char, Key>) -> Unit) {
    var valueChar by remember(dialogState.openState) { mutableStateOf("") }
    var valueHID by remember(dialogState.openState) { mutableStateOf("") }

    val modifierNames = remember { arrayOf("Shift", "Ctrl", "Alt") }
    val modifierCheckedStates =
        remember(dialogState.openState) { mutableStateListOf(false, false, false) }

    val currentKey = remember(valueChar, valueHID, modifierCheckedStates) {
        val char = valueChar.firstOrNull() ?: return@remember null
        val hidCode = valueHID.toByteOrNull(16) ?: return@remember null
        var modifier = 0.toByte()

        if (modifierCheckedStates[0]) {
            modifier = modifier or KeyTranslator.LSHIFT
        }
        if (modifierCheckedStates[1]) {
            modifier = modifier or KeyTranslator.LCTRL
        }
        if (modifierCheckedStates[2]) {
            modifier = modifier or KeyTranslator.LALT
        }

        char to (modifier to hidCode)
    }

    ConfirmDialog(
        dialogState = dialogState,
        title = "Add custom key",
        onDismiss = {
            close()
        }, onConfirm = {
            currentKey?.let {
                onAddKey(it)
                close()
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Enter the character and the corresponding HID-code (not scancode) including any modifiers. The HID-code of the ENTER key is for example 28 (hex).")

            Spacer(Modifier.height(8.dp))

            // Input fields
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = valueChar,
                    onValueChange = {
                        if (it.length <= 1) {
                            valueChar = it
                            // preselect shift state
                            modifierCheckedStates[0] = it.firstOrNull()?.isUpperCase() ?: false
                        }
                    },
                    modifier = Modifier
                        .padding(end = 8.dp),
                    placeholder = { Text("Character") }
                )

                TextField(
                    value = valueHID,
                    onValueChange = { valueHID = it },
                    modifier = Modifier
                        .padding(end = 8.dp),
                    placeholder = { Text("HID-Code (HEX)") }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                modifierNames.zip(modifierCheckedStates).forEachIndexed { index, (name, checked) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name)
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                modifierCheckedStates[index] = isChecked
                            }
                        )
                    }
                }
            }

            val context = LocalContext.current

            // Settings activity does not have the bluetooth controller via context
            val controller = rememberBluetoothControllerService(context, false)

            Button(
                onClick = {
                    runCatching {
                        currentKey?.let {
                            CoroutineScope(Dispatchers.IO).launch {
                                controller?.getController()?.keyboardSender?.sendKey(
                                    it.second
                                )
                            }
                        }
                    }.onFailure {
                        Log.e("CustomKeys", "Error sending to PC:", it)
                    }
                },
                enabled = controller?.getController()?.currentDevice != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Test")
            }

            Text(
                "* To test the key out you need to be connected to a device.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun CustomKeys(
    keyMap: Keymap,
    onAddKey: (Pair<Char, Key>) -> Unit,
    onDeleteKey: (Pair<Char, Key>) -> Unit
) {
    val addKeyDialog = rememberDialogState()

    LazyColumn {
        item {
            Text("Allows you to define custom character to HID-code mappings, that will override any definitions from the selected keyboard layout.")
        }

        items(keyMap.toList()) { item ->
            ListItem(
                headlineContent = { Text(item.first.toString()) },
                supportingContent = { Text(item.second.toString()) },
                trailingContent = {
                    IconButton(onClick = { onDeleteKey(item) }) {
                        Icon(Icons.Default.Delete, "Delete $item")
                    }
                }
            )
        }

        item {
            OutlinedButton(onClick = {
                addKeyDialog.open()
            }) {
                Text("Add")
            }
        }
    }

    AddCustomKeyDialog(addKeyDialog, onAddKey)
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