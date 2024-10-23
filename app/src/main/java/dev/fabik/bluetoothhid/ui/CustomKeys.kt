package dev.fabik.bluetoothhid.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.bt.Key
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.bt.Keymap
import java.io.ObjectOutputStream
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
            val keys = KeyTranslator.CUSTOM_KEYMAP

            runCatching {
                val fos = context.openFileOutput("custom.layout", Context.MODE_PRIVATE)
                ObjectOutputStream(fos).apply {
                    writeObject(keys)
                    close()
                }
            }.onFailure {
                Log.e("CustomKeys", "Error saving custom keymap", it)
            }

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
fun CustomKeys(
    keyMap: Keymap,
    onAddKey: (Pair<Char, Key>) -> Unit,
    onDeleteKey: (Pair<Char, Key>) -> Unit
) {
    var valueChar by remember { mutableStateOf(TextFieldValue("")) }
    var valueHID by remember { mutableStateOf(TextFieldValue("")) }

    val modifierNames = remember { arrayOf("Shift", "Ctrl", "Alt") }
    val modifierCheckedStates = remember { mutableStateListOf(false, false, false) }

    LazyColumn {
        item {
            Text("Allows you to define custom character to HID-code mappings, that will override any definitions from the selected keyboard layout.")

            Text(
                "Keys",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
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
            Text("Add", style = MaterialTheme.typography.titleMedium)

            // Input fields
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = valueChar,
                    onValueChange = {
                        if (it.text.length <= 1) {
                            valueChar = it
                            // preselect shift state
                            modifierCheckedStates[0] = it.text.firstOrNull()?.isUpperCase() ?: false
                        }
                    },
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(end = 8.dp),
                    placeholder = { Text("Character") }
                )

                TextField(
                    value = valueHID,
                    onValueChange = { valueHID = it },
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(end = 8.dp),
                    placeholder = { Text("HID-Code (HEX)") }
                )
            }
        }

        item {
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
        }

        item {
            ElevatedButton(onClick = {
                val char = valueChar.text.firstOrNull() ?: return@ElevatedButton
                val hidCode = valueHID.text.toByteOrNull(16) ?: return@ElevatedButton
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

                onAddKey(char to (modifier to hidCode))
            }) {
                Text("Add")
            }
        }
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