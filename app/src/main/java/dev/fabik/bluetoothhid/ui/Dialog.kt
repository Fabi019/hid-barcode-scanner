package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class DialogState(initialOpen: Boolean = false) {
    var openState by mutableStateOf(initialOpen)

    fun toggle() {
        openState = !openState
    }

    fun open() {
        openState = true
    }

    fun close() {
        openState = false
    }
}

@Composable
fun rememberDialogState(initialOpen: Boolean = false) = remember {
    DialogState(initialOpen)
}

@Composable
fun ComboBoxDialog(
    dialogState: DialogState,
    title: String,
    selectedItem: Int,
    values: List<String>,
    onDismiss: DialogState.() -> Unit = {},
    onConfirm: DialogState.(Int) -> Unit
) {
    var currentSelection by remember {
        mutableStateOf(selectedItem)
    }

    ConfirmDialog(dialogState, title, onConfirm = {
        onConfirm(currentSelection)
    }, onDismiss = {
        currentSelection = selectedItem
        onDismiss()
    }) {
        LazyColumn {
            itemsIndexed(values) { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        currentSelection = index
                    }) {
                    RadioButton(
                        selected = (index == currentSelection),
                        onClick = { currentSelection = index })
                    Spacer(Modifier.width(8.dp))
                    Text(item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TextDialog(
    dialogState: DialogState,
    title: String,
    desc: String,
    onDismiss: DialogState.() -> Unit = {},
    onConfirm: DialogState.() -> Unit
) {
    ConfirmDialog(dialogState, title, onConfirm, onDismiss) {
        Text(desc)
    }
}

@Composable
fun ConfirmDialog(
    dialogState: DialogState,
    title: String,
    onDismiss: DialogState.() -> Unit = {},
    onConfirm: DialogState.() -> Unit,
    content: @Composable () -> Unit,
) {
    if (dialogState.openState) {
        AlertDialog(
            onDismissRequest = { onDismiss(dialogState) },
            title = { Text(title) },
            text = { content() },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(dialogState) }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onDismiss(dialogState) }
                ) {
                    Text("Dismiss")
                }
            },
        )
    }
}