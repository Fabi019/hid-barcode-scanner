package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterModal() {
    var showModal by remember { mutableStateOf(false) }

    IconButton(onClick = { showModal = true }) {
        Icon(Icons.Default.FilterAlt, "Open Filter")
    }

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            content = {
                FilterModalContent(onApply = {}, onCancel = { showModal = false })
            }
        )
    }
}

@Composable
fun FilterModalContent(onApply: () -> Unit, onCancel: () -> Unit) {
    var selectedTypes by remember { mutableStateOf(setOf("QR Code", "Barcode")) }

    var selectedDateStart by remember { mutableStateOf<Long?>(null) }
    var selectedDateEnd by remember { mutableStateOf<Long?>(null) }
    var showDateModal by remember { mutableStateOf(false) }

    if (showDateModal) {
        DateRangePickerModal(
            onDateRangeSelected = { (start, end) ->
                selectedDateStart = start; selectedDateEnd = end
            },
            onDismiss = { showDateModal = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Filter Options", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Date Range Picker
        Text("Select Date Range")

        OutlinedTextField(
            value = convertMillisToDate(selectedDateStart) + " - " + convertMillisToDate(
                selectedDateEnd
            ),
            onValueChange = { },
            readOnly = true,
            label = { Text("Date range") },
            placeholder = { Text("Click to set a date range") },
            trailingIcon = {
                IconButton(onClick = { showDateModal = !showDateModal }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select date"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Type Filter with Chips
        Text("Select Types")
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 120.dp)) {
            items(listOf("QR Code", "Barcode", "Matrix", "Text")) { type ->
                FilterChip(
                    selected = selectedTypes.contains(type),
                    onClick = {
                        if (selectedTypes.contains(type)) {
                            selectedTypes = selectedTypes - type
                        } else {
                            selectedTypes = selectedTypes + type
                        }
                    },
                    label = { Text(type) },
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { onCancel() }) {
                Text("Cancel")
            }

            Button(onClick = { onApply() }) {
                Text("Apply Filters")
            }
        }
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
                        Pair(
                            dateRangePickerState.selectedStartDateMillis,
                            dateRangePickerState.selectedEndDateMillis
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select date range"
                )
            },
            showModeToggle = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp)
        )
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
    FilterModalContent({}) { }
}