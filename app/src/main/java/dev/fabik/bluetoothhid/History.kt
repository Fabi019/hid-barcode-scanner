package dev.fabik.bluetoothhid

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.indicatorLine
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.ui.ConfirmDialog
import dev.fabik.bluetoothhid.ui.FilterModal
import dev.fabik.bluetoothhid.ui.model.HistoryViewModel
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.ui.tooltip
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun History(onBack: () -> Unit, onClick: (HistoryViewModel.HistoryEntry) -> Unit) = with(viewModel<HistoryViewModel>()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HistoryTopBar(scrollBehavior) {
                onBack()
            }
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportSheet()
                ExportSheet()
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            HistoryContent(onClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryViewModel.HistoryContent(onClick: (HistoryViewModel.HistoryEntry) -> Unit) {
    BackHandler(enabled = isSelecting) {
        clearSelection()
    }

    val types = stringArrayResource(R.array.code_types_values)

    LazyColumn(Modifier.fillMaxSize()) {
        items(filteredHistory) { barcode ->
            val isSelected by remember(barcode) { derivedStateOf { isItemSelected(barcode) } }
            ListItem(
                overlineContent = {
                    val timeString = remember {
                        val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
                        val instant = Instant.ofEpochMilli(barcode.timestamp)
                        format.format(instant)
                    }
                    Text(timeString)
                },
                headlineContent = {
                    Text(barcode.value)
                },
                supportingContent = {
                    Text(types.getOrNull(barcode.format) ?: "UNKNOWN")
                },
                tonalElevation = if (isSelected) 8.0.dp else 0.0.dp,
                modifier = Modifier
                    .animateItem()
                    .combinedClickable(onLongClick = {
                        setItemSelected(barcode, true)
                    }, onClick = {
                        onClick(barcode)
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
            when (isSearching) {
                true -> AppBarTextField(
                    value = searchQuery, onValueChange = {
                        searchQuery = it
                    }, hint = stringResource(R.string.search_by_value)
                )

                false -> Text(stringResource(R.string.history))
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
            FilterModal(filteredTypes.toSet(), filterDateStart, filterDateEnd) { sel, a, b ->
                filteredTypes.clear()
                filteredTypes.addAll(sel)
                filterDateStart = a
                filterDateEnd = b
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
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )

    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse {
        MaterialTheme.colorScheme.onSurface
    }
    val mergedTextStyle =
        textStyle.merge(TextStyle(color = textColor, lineHeight = 16.sp, fontSize = 16.sp))

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
            .height(32.dp)
            .indicatorLine(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                colors = colors
            )
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
fun HistoryViewModel.ExportSheet() {
    val context = LocalContext.current

    val exportString = stringResource(R.string.export)
    val types = stringArrayResource(R.array.code_types_values)

    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var exportData by rememberSaveable { mutableStateOf("") }

    ExtendedFloatingActionButton(
        text = {
            Text(
                if (isSelecting) pluralStringResource(
                    R.plurals.export_items, selectionSize, selectionSize
                ) else stringResource(R.string.export),
                modifier = Modifier.animateContentSize(),
            )
        },
        icon = { Icon(Icons.Default.FileDownload, stringResource(R.string.export)) },
        onClick = { showSheet = true }
    )

    val startForResult =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val fileUri = result.data?.data
                fileUri?.let {
                    runCatching {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.bufferedWriter().use { writer ->
                                writer.write(exportData)
                            }
                        }
                    }.onFailure {
                        Log.e("History", "Error saving history to file!", it)
                    }
                }
            }

            exportData = ""
        }

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                ExportSheetContent { typ, dedup, saveToFile ->
                    scope.launch { state.hide() }.invokeOnCompletion { showSheet = state.isVisible }

                    val data = exportHistory(typ, dedup, types)

                    val (mime, name) = when (typ) {
                        HistoryViewModel.ExportType.CSV -> "*/*" to "export.csv"
                        HistoryViewModel.ExportType.JSON -> "*/*" to "export.json"
                        HistoryViewModel.ExportType.XML -> "*/*" to "export.xml"
                        HistoryViewModel.ExportType.LINES -> "*/*" to "export.txt"
                    }

                    runCatching {
                        if (saveToFile) {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = mime
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, name)
                            }
                            exportData = data
                            startForResult.launch(intent)
                        } else {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_TEXT, data)
                            }
                            val shareIntent = Intent.createChooser(intent, exportString)
                            context.startActivity(shareIntent)
                        }
                    }.onFailure {
                        Log.e("History", "Error starting activity!", it)
                    }
                }
            }
        )
    }
}

@Composable
@Preview
private fun ExportSheetContent(
    onSelect: (HistoryViewModel.ExportType, Boolean, Boolean) -> Unit = { _, _, _ -> },
) {
    var (deduplicateChecked, setDeduplicate) = rememberSaveable { mutableStateOf(true) }
    var (saveIntoFileChecked, setSaveIntoFile) = rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.export_as),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .toggleable(
                    value = deduplicateChecked,
                    role = Role.Checkbox,
                    onValueChange = setDeduplicate
                )
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = deduplicateChecked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.exclude_duplicates))
        }

        Row(
            Modifier
                .toggleable(
                    value = saveIntoFileChecked,
                    role = Role.Checkbox,
                    onValueChange = setSaveIntoFile
                )
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = saveIntoFileChecked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.save_to_file))
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(HistoryViewModel.ExportType.entries) { type ->
                ListItem(
                    headlineContent = { Text(stringResource(id = type.label)) },
                    supportingContent = { Text(stringResource(id = type.description)) },
                    leadingContent = { Icon(type.icon, null) },
                    modifier = Modifier
                        .clickable { onSelect(type, deduplicateChecked, saveIntoFileChecked) }
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
@ExperimentalMaterial3Api
fun HistoryViewModel.ImportSheet() {
    val context = LocalContext.current
    val types = stringArrayResource(R.array.code_types_values)

    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf<HistoryViewModel.ImportFormat?>(null) }

    ExtendedFloatingActionButton(
        text = { Text(stringResource(R.string.import_text)) },
        icon = { Icon(Icons.Default.FileUpload, stringResource(R.string.import_text)) },
        onClick = { showSheet = true }
    )

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val fileUri = result.data?.data
                val format = selectedFormat

                if (fileUri != null && format != null) {
                    runCatching {
                        val content = context.contentResolver.openInputStream(fileUri)?.use {
                            it.bufferedReader().readText()
                        } ?: ""

                        val count = HistoryViewModel.importHistory(content, format, types)
                        Toast.makeText(
                            context,
                            "Imported $count entries",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        Log.e("History", "Error importing file!", it)
                        Toast.makeText(
                            context,
                            "Error: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            selectedFormat = null
        }

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                ImportSheetContent { format ->
                    scope.launch { state.hide() }.invokeOnCompletion { showSheet = state.isVisible }

                    selectedFormat = format

                    // Launch file picker with appropriate MIME type
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = format.mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }

                    runCatching {
                        filePickerLauncher.launch(intent)
                    }.onFailure {
                        Log.e("History", "Error starting file picker!", it)
                    }
                }
            }
        )
    }
}

@Composable
@Preview
private fun ImportSheetContent(
    onSelect: (HistoryViewModel.ImportFormat) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.import_from),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(HistoryViewModel.ImportFormat.entries) { format ->
                ListItem(
                    headlineContent = { Text(stringResource(id = format.label)) },
                    supportingContent = { Text(stringResource(id = format.description)) },
                    leadingContent = { Icon(format.icon, null) },
                    modifier = Modifier
                        .clickable { onSelect(format) }
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PersistHistory() {
    val context = LocalContext.current
    val persistHistory by context.getPreferenceState(PreferenceStore.PERSIST_HISTORY)

    persistHistory?.let {
        ComposableLifecycle { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (it) CoroutineScope(Dispatchers.IO).launch {
                        HistoryViewModel.restoreHistory(
                            context
                        )
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    if (it) CoroutineScope(Dispatchers.IO).launch {
                        HistoryViewModel.saveHistory(
                            context
                        )
                    }
                }

                else -> {}
            }
        }
    }
}