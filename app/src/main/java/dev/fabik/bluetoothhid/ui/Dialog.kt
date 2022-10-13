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
fun CheckBoxDialog(
    dialogState: DialogState,
    title: String,
    selectedValues: Set<Int>,
    values: Array<Int>,
    valueStrings: Array<String>,
    onDismiss: DialogState.() -> Unit = {},
    onConfirm: DialogState.(List<Int>) -> Unit
) {
    var currentSelection = remember {
        selectedValues.toMutableStateList()
    }

    ConfirmDialog(dialogState, title, onConfirm = {
        onConfirm(currentSelection)
    }, onDismiss = {
        currentSelection = selectedValues.toMutableStateList()
        onDismiss()
    }) {
        LazyColumn {
            itemsIndexed(valueStrings) { index, item ->
                val value = values[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (!currentSelection.contains(value)) {
                            currentSelection.add(value)
                        } else {
                            currentSelection.remove(value)
                        }
                    }) {
                    Checkbox(
                        checked = currentSelection.contains(value),
                        onCheckedChange = {
                            if (it && !currentSelection.contains(value)) {
                                currentSelection.add(value)
                            } else {
                                currentSelection.remove(value)
                            }
                        })
                    Spacer(Modifier.width(8.dp))
                    Text(item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
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
fun InfoDialog(
    dialogState: DialogState,
    title: String,
    onDismiss: DialogState.() -> Unit = {},
    content: @Composable () -> Unit
) {
    if (dialogState.openState) {
        AlertDialog(
            onDismissRequest = { onDismiss(dialogState) },
            title = { Text(title) },
            text = { content() },
            confirmButton = { },
            dismissButton = {
                TextButton(
                    onClick = { onDismiss(dialogState) }
                ) {
                    Text("Ok")
                }
            },
        )
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