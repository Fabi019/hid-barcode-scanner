package dev.fabik.bluetoothhid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.indicatorLine
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.ui.ConfirmDialog
import dev.fabik.bluetoothhid.ui.model.HistoryViewModel
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.ui.tooltip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun History(onBack: () -> Unit, onClick: (String) -> Unit) = with(viewModel<HistoryViewModel>()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HistoryTopBar(scrollBehavior) {
                onBack()
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            HistoryContent(onClick)
        }
    }
}

@Composable
fun HistoryViewModel.HistoryContent(onClick: (String) -> Unit) {
    val filteredHistory = remember(HistoryViewModel.historyEntries, searchQuery) {
        HistoryViewModel.historyEntries.filter { (barcode, _) ->
            barcode.displayValue?.contains(searchQuery, ignoreCase = true) ?: false
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(filteredHistory) { index, item ->
            val (barcode, time) = item
            ListItem(
                overlineContent = {
                    val timeString = remember {
                        val format = DateTimeFormatter
                            .ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.getDefault())
                            .withZone(ZoneId.systemDefault())
                        val instant = Instant.ofEpochMilli(time)
                        format.format(instant)
                    }
                    Text(timeString)
                },
                headlineContent = {
                    Text(barcode.rawValue ?: "")
                },
                supportingContent = {
                    Text(parseBarcodeType(barcode.format))
                },
                modifier = Modifier.clickable {
                    barcode.rawValue?.let(onClick)
                }
            )
            Divider()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HistoryViewModel.HistoryTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onExit: () -> Unit
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
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                    },
                    hint = "Search by value"
                )
            } else {
                Text("History")
            }
        }, navigationIcon = {
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
                Icon(Icons.Default.ArrowBack, "Back")
            }
        }, actions = {
            IconButton(
                onClick = {
                    if (isSearching) searchQuery = ""
                    else isSearching = true
                },
                Modifier.tooltip("Search")
            ) {
                Icon(
                    if (isSearching) Icons.Default.Backspace
                    else Icons.Default.Search,
                    "Search"
                )
            }
            IconButton(
                onClick = {
                    clearHistoryDialog.open()
                }, Modifier.tooltip("Clear history")
            ) {
                Icon(Icons.Default.Delete, "Clear history")
            }
        },
        scrollBehavior = scrollBehavior
    )

    ConfirmDialog(
        dialogState = clearHistoryDialog,
        title = "Clear history",
        onConfirm = {
            HistoryViewModel.clearHistory()
            close()
        }
    ) {
        Text("Are you sure you want to clear the history?")
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
        disabledContainerColor = Color.Unspecified
    )

    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse {
        MaterialTheme.colorScheme.onSurface
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor, lineHeight = 50.sp))

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

    CompositionLocalProvider(
        LocalTextSelectionColors provides LocalTextSelectionColors.current
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                // remove newlines to avoid strange layout issues, and also because singleLine=true
                onValueChange(it.text.replace("\n", ""))
            },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(32.dp)
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
            }
        )
    }
}
