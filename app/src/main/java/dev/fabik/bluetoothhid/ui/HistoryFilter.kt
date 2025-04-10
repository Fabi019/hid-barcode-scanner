package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterModal(
    selectedTypes: Set<Int>,
    startDate: Long?,
    endDate: Long?,
    onApply: (Set<Int>, Long?, Long?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }, Modifier.tooltip(stringResource(R.string.filter))) {
        BadgedBox(badge = {
            if (selectedTypes.isNotEmpty() || startDate != null || endDate != null)
                Badge()
        }) {
            Icon(Icons.Default.FilterAlt, "Open Filter")
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                FilterModalContent(
                    selectedTypes, startDate, endDate,
                    onApply = { sel, a, b ->
                        scope.launch { state.hide() }
                            .invokeOnCompletion { showSheet = state.isVisible }
                        onApply(sel, a, b)
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterModalContent(
    selectedTypes: Set<Int>,
    startDate: Long?,
    endDate: Long?,
    onApply: (Set<Int>, Long?, Long?) -> Unit
) {
    var selectedTypes =
        rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) {
            selectedTypes.toMutableStateList()
        }

    var selectedDateStart by rememberSaveable { mutableStateOf<Long?>(startDate) }
    var selectedDateEnd by rememberSaveable { mutableStateOf<Long?>(endDate) }
    var showDateModal by rememberSaveable { mutableStateOf(false) }

    if (showDateModal) {
        DateRangePickerModal(
            onDateRangeSelected = { (start, end) ->
                selectedDateStart = start
                selectedDateEnd = end
            },
            onDismiss = { showDateModal = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.filter),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = convertMillisToDate(selectedDateStart) + " - " + convertMillisToDate(
                selectedDateEnd
            ),
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.date_range)) },
            trailingIcon = {
                IconButton(onClick = { showDateModal = !showDateModal }) {
                    Icon(Icons.Default.DateRange, "Select date")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Type Filter with Chips
        Text(stringResource(R.string.select_types))

        FlowRow(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            stringArrayResource(R.array.code_types_values).forEachIndexed { i, type ->
                FilterChip(
                    selected = selectedTypes.contains(i),
                    onClick = {
                        if (selectedTypes.contains(i)) {
                            selectedTypes.remove(i)
                        } else {
                            selectedTypes.add(i)
                        }
                    },
                    label = { Text(type) },
                    Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {
                selectedDateStart = null
                selectedDateEnd = null
                selectedTypes.clear()
            }) {
                Text(stringResource(R.string.clear))
            }

            Button(onClick = {
                onApply(
                    selectedTypes.toSet(),
                    selectedDateStart,
                    selectedDateEnd
                )
            }) {
                Text(stringResource(R.string.apply))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDateRangeSelected: (Pair<Long?, Long?>) -> Unit,
    onDismiss: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(
                        dateRangePickerState.selectedStartDateMillis to dateRangePickerState.selectedEndDateMillis
                    )
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        DateRangePicker(dateRangePickerState)
    }
}

fun convertMillisToDate(millis: Long?): String {
    if (millis == null) {
        return ""
    }

    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
    val instant = Instant.ofEpochMilli(millis)
    return formatter.format(instant)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FilterModalContent(setOf(), null, null) { sel, a, b -> }
}