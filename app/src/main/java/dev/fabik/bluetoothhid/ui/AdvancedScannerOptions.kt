package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedOptionsModal() {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    ButtonPreference(
        title = stringResource(R.string.advanced_options),
        desc = stringResource(R.string.advanced_opts_desc),
        icon = Icons.Default.Science,
        onClick = { showSheet = true }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                AdvancedOptionsModalContent()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedOptionsModalContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.advanced_options),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            stringResource(R.string.detection),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )

        AdvancedToggleOption(stringResource(R.string.try_harder), PreferenceStore.ADV_TRY_HARDER)
        AdvancedToggleOption(
            stringResource(R.string.try_rotate_image),
            PreferenceStore.ADV_TRY_ROTATE
        )
        AdvancedToggleOption(stringResource(R.string.try_inverted), PreferenceStore.ADV_TRY_INVERT)
        AdvancedToggleOption(
            stringResource(R.string.try_downscale),
            PreferenceStore.ADV_TRY_DOWNSCALE
        )
        AdvancedSliderOption(
            stringResource(R.string.minimum_scan_lines),
            1 to 50,
            PreferenceStore.ADV_MIN_LINE_COUNT
        )

        Text(
            stringResource(R.string.processing),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )

        AdvancedSelectionOption(
            stringResource(R.string.binarizer),
            arrayOf("LOCAL_AVERAGE", "GLOBAL_HISTOGRAM", "FIXED_THRESHOLD", "BOOL_CAST"),
            PreferenceStore.ADV_BINARIZER
        )
        AdvancedSliderOption(
            stringResource(R.string.downscale_factor),
            1 to 10,
            PreferenceStore.ADV_DOWNSCALE_FACTOR
        )
        AdvancedSliderOption(
            stringResource(R.string.downscale_threshold),
            0 to 1000,
            PreferenceStore.ADV_DOWNSCALE_THRESHOLD
        )

        Text(
            stringResource(R.string.parser),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )

        AdvancedSelectionOption(
            stringResource(R.string.text_mode),
            arrayOf("PLAIN", "ECI", "HRI", "HEX", "ESCAPED"),
            PreferenceStore.ADV_TEXT_MODE
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AdvancedToggleOption(text: String, preference: PreferenceStore.Preference<Boolean>) {
    var checked by rememberPreference(preference)

    Row(
        Modifier
            .toggleable(checked, onValueChange = { checked = it })
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1.0f))
        Switch(
            checked,
            onCheckedChange = null,
            modifier = Modifier.semantics(mergeDescendants = true) {
                stateDescription = "$text is ${if (checked) "On" else "Off"}"
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSelectionOption(
    text: String,
    values: Array<String>,
    preference: PreferenceStore.Preference<Int>
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndex by rememberPreference(preference)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        }
    ) {
        OutlinedTextField(
            readOnly = true,
            singleLine = true,
            value = values.getOrNull(selectedIndex) ?: "",
            onValueChange = { },
            label = { Text(text) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(2.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            values.forEachIndexed { i, selectionOption ->
                DropdownMenuItem(
                    text = { Text(text = selectionOption) },
                    onClick = {
                        selectedIndex = i
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AdvancedSliderOption(
    text: String,
    range: Pair<Int, Int>,
    preference: PreferenceStore.Preference<Int>
) {
    var value by rememberPreference(preference)
    var currentValue by remember { mutableIntStateOf(value) }

    Column(Modifier.padding(2.dp)) {
        Text("$text: $currentValue")
        Slider(
            value = currentValue.toFloat(),
            onValueChange = { currentValue = it.roundToInt() },
            onValueChangeFinished = { value = currentValue },
            valueRange = range.first.toFloat()..range.second.toFloat()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AdvancedOptionsPreview() {
    AdvancedOptionsModalContent()
}