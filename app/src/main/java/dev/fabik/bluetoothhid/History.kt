package dev.fabik.bluetoothhid

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.ui.ConfirmDialog
import dev.fabik.bluetoothhid.ui.model.HistoryViewModel
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.ui.tooltip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun History(onBack: () -> Unit, onClick: (String) -> Unit) = with(viewModel<HistoryViewModel>()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val exportString = stringResource(R.string.export)
    val exportSheetState = rememberModalBottomSheetState(true)
    val scope: CoroutineScope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        HistoryTopBar(scrollBehavior) {
            onBack()
        }
    }, floatingActionButton = {
        ExtendedFloatingActionButton(text = {
            Text(
                if (isSelecting) pluralStringResource(
                    R.plurals.export_items, selectionSize, selectionSize
                ) else stringResource(R.string.export),
                modifier = Modifier.animateContentSize(),
            )
        },
            icon = { Icon(Icons.Default.FileDownload, stringResource(R.string.export)) },
            onClick = {
                scope.launch { exportSheetState.show() }
            })
    }) { padding ->
        Box(Modifier.padding(padding)) {
            HistoryContent(onClick)
        }
        ExportSheet(sheetState = exportSheetState) {
            val data = exportHistory(it)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when (it) {
                    HistoryViewModel.ExportType.CSV -> "text/csv"
                    HistoryViewModel.ExportType.JSON -> "application/json"
                    HistoryViewModel.ExportType.LINES -> "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, data)
            }
            val shareIntent = Intent.createChooser(intent, exportString)
            startActivity(context, shareIntent, null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryViewModel.HistoryContent(onClick: (String) -> Unit) {
    val filteredHistory = remember(HistoryViewModel.historyEntries, searchQuery) {
        HistoryViewModel.historyEntries.filter { (barcode, _) ->
            barcode.rawValue?.contains(searchQuery, ignoreCase = true) ?: false
        }
    }

    BackHandler(enabled = isSelecting) {
        clearSelection()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(filteredHistory) { (barcode, time) ->
            val isSelected by remember(barcode) { derivedStateOf { isItemSelected(barcode) } }
            ListItem(
                overlineContent = {
                    val timeString = remember {
                        val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
                        val instant = Instant.ofEpochMilli(time)
                        format.format(instant)
                    }
                    Text(timeString)
                },
                headlineContent = {
                    Text(barcode.rawValue ?: barcode.rawBytes?.contentToString() ?: "")
                },
                supportingContent = {
                    Text(HistoryViewModel.parseBarcodeType(barcode.format))
                },
                tonalElevation = if (isSelected) {
                    8.0.dp
                } else {
                    0.0.dp
                },
                modifier = Modifier
                    .combinedClickable(onLongClick = {
                        setItemSelected(barcode, true)
                    }, onClick = {
                        barcode.rawValue?.let {
                            onClick(it)
                        }
                    })
                    .then(
                        if (isSelecting) {
                            Modifier.toggleable(value = isSelected, onValueChange = {
                                setItemSelected(barcode, it)
                            })
                        } else Modifier
                    )
            )
            HorizontalDivider()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HistoryViewModel.HistoryTopBar(
    scrollBehavior: TopAppBarScrollBehavior, onExit: () -> Unit
) {
    val clearHistoryDialog = rememberDialogState()

    // Close search on back button
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    TopAppBar(
        title = {
            if (isSearching) {
                AppBarTextField(
                    value = searchQuery, onValueChange = {
                        searchQuery = it
                    }, hint = stringResource(R.string.search_by_value)
                )
            } else {
                Text(stringResource(R.string.history))
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        onExit()
                    }
                }, Modifier.tooltip(stringResource(R.string.back))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(
                onClick = {
                    if (isSearching) searchQuery = ""
                    else isSearching = true
                }, Modifier.tooltip(stringResource(R.string.search))
            ) {
                Icon(
                    if (isSearching) Icons.Outlined.Close
                    else Icons.Default.Search, "Search"
                )
            }
            IconButton(
                onClick = {
                    clearHistoryDialog.open()
                }, Modifier.tooltip(stringResource(R.string.clear_history))
            ) {
                Icon(Icons.Default.Delete, "Clear history")
            }
        },
        scrollBehavior = scrollBehavior
    )

    ConfirmDialog(
        dialogState = clearHistoryDialog,
        title = if (isSelecting) {
            stringResource(R.string.delete_selection)
        } else {
            stringResource(R.string.clear_history)
        },
        onConfirm = {
            if (isSelecting) {
                deleteSelectedItems()
            } else {
                HistoryViewModel.clearHistory()
            }
            close()
        }
    ) {
        Text(
            if (isSelecting) {
                stringResource(R.string.delete_selection_desc)
            } else {
                stringResource(R.string.clear_history_desc)
            }
        )
    }
}

// adapted from: https://stackoverflow.com/a/73665177/21418508
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = LocalTextStyle.current
    // make sure there is no background color in the decoration box
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Unspecified,
        unfocusedContainerColor = Color.Unspecified,
        disabledContainerColor = Color.Unspecified,
        // Hides the indicator line below the text field
        focusedIndicatorColor = Color.Unspecified,
        unfocusedIndicatorColor = Color.Unspecified,
    )

    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse {
        MaterialTheme.colorScheme.onSurface
    }
    val mergedTextStyle =
        textStyle.merge(TextStyle(color = textColor, lineHeight = 50.sp, fontSize = 16.sp))

    // request focus when this composable is first initialized
    val focusRequester = FocusRequester()
    SideEffect {
        focusRequester.requestFocus()
    }

    // set the correct cursor position when this composable is first initialized
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    textFieldValue = textFieldValue.copy(text = value) // make sure to keep the value updated

    BasicTextField(value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            // remove newlines to avoid strange layout issues, and also because singleLine=true
            onValueChange(it.text.replace("\n", ""))
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(32.dp)
            .focusRequester(focusRequester),
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = true,
        decorationBox = { innerTextField ->
            // places text field with placeholder and appropriate bottom padding
            TextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = VisualTransformation.None,
                innerTextField = innerTextField,
                placeholder = { Text(text = hint) },
                singleLine = true,
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = PaddingValues(bottom = 4.dp)
            )
        })
}

@Composable
@ExperimentalMaterial3Api
fun ExportSheet(
    sheetState: SheetState,
    scope: CoroutineScope = rememberCoroutineScope(),
    onExport: (HistoryViewModel.ExportType) -> Unit,
) = with(sheetState) {
    if (isVisible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { scope.launch { hide() } },
            content = {
                ExportSheetContent {
                    scope.launch { hide() }
                    onExport(it)
                }
            },
        )
    }
}

@Composable
@Preview
private fun ExportSheetContent(
    onSelect: (HistoryViewModel.ExportType) -> Unit = {},
) {
    Column {
        Text(
            stringResource(R.string.export_as),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        HistoryViewModel.ExportType.values().forEach { type ->
            ListItem(headlineContent = { Text(stringResource(id = type.label)) },
                supportingContent = { Text(stringResource(id = type.description)) },
                leadingContent = { Icon(type.icon, null) },
                modifier = Modifier.clickable {
                    onSelect(type)
                })
        }
    }
}