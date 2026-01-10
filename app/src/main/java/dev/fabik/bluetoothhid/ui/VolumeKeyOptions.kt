package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeKeyOptionsModal() {
    var useVolumeKeys by rememberPreferenceNull(PreferenceStore.SEND_WITH_VOLUME)

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    ButtonPreference(
        title = "Use volume keys",
        desc = "Specify action when pressing volume up/down",
        icon = Icons.AutoMirrored.Filled.VolumeMute,
        onClick = { showSheet = true },
        extra = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalDivider(
                    Modifier
                        .height(32.dp)
                        .padding(horizontal = 24.dp)
                )
                useVolumeKeys?.let { c ->
                    Switch(c, onCheckedChange = {
                        useVolumeKeys = it
                    }, modifier = Modifier.semantics(mergeDescendants = true) {
                        stateDescription = "Use volume keys is ${if (c) "On" else "Off"}"
                    })
                }
            }
        }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                VolumeKeysOptionsContent()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun VolumeKeysOptionsContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Volume keys",
            style = MaterialTheme.typography.titleLarge,
        )

        val actions = arrayOf(
            "Nothing",
            "Send value",
            "Clear value",
            "Run OCR",
            "Open manual input",
            "Toggle flash",
            "Toggle zoom",
            "Trigger focus"
        )

        AdvancedEnumSelectionOption(
            "Volume up",
            actions,
            PreferenceStore.VOLUME_ACTION_UP
        )

        AdvancedEnumSelectionOption(
            "Volume down",
            actions,
            PreferenceStore.VOLUME_ACTION_DOWN
        )

        AdvancedSliderOption("Zoom value", 1f to 10f, PreferenceStore.VOLUME_ZOOM_LEVEL)

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun AdvancedSliderOption(
    text: String,
    range: Pair<Float, Float>,
    preference: PreferenceStore.Preference<Float>
) {
    var value by rememberPreference(preference)
    var currentValue by remember { mutableFloatStateOf(value) }

    Column(Modifier.padding(2.dp)) {
        Text("$text: ${"%.1f".format(currentValue)}")
        Slider(
            value = currentValue,
            onValueChange = { currentValue = it },
            onValueChangeFinished = { value = currentValue },
            valueRange = range.first..range.second
        )
    }
}