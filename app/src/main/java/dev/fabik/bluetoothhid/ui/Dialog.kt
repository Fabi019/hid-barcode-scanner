package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.theme.Typography

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
    values: Array<String>,
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
fun SliderDialog(
    dialogState: DialogState,
    title: String,
    valueFormat: String = "%f",
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onDismiss: DialogState.() -> Unit = {},
    onValueChange: DialogState.(Float) -> Unit
) {
    var sliderPosition by remember {
        mutableStateOf(value)
    }

    ConfirmDialog(dialogState, title, onConfirm = {
        onValueChange(sliderPosition)
    }, onDismiss = {
        sliderPosition = value
        onDismiss()
    }
    ) {
        Column {
            Text(valueFormat.format(sliderPosition))

            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
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
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onDismiss(dialogState) }
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }
}

@Composable
fun LoadingDialog(
    dialogState: DialogState,
    title: String,
    desc: String,
    onDismiss: DialogState.() -> Unit = {}
) {
    if (dialogState.openState) {
        Dialog(onDismissRequest = { onDismiss(dialogState) }) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier
                        .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(title, style = Typography.headlineMedium)
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    Text(desc)
                }
            }
        }
    }
}