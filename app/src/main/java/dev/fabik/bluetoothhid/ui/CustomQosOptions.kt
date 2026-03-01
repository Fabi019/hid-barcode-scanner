package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QosOptionsModal() {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text("L2CAP QoS settings") },
        onClick = {
            showSheet = true
        }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                QosOptionsContent()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun QosOptionsContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "L2CAP QoS settings",
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            "Note: These parameters normally don't need to be changed. " +
                    "Changes get applied after restarting the app.",
            style = MaterialTheme.typography.bodySmall
        )

        AdvancedEnumSelectionOption(
            "Service type",
            arrayOf("BEST_EFFORT", "GUARANTEED"),
            PreferenceStore.QOS_SERVICE_TYPE
        )

        val keyboard = remember { KeyboardOptions(keyboardType = KeyboardType.Number) }

        AdvancedTextField("Token rate (bytes/sec)", PreferenceStore.QOS_TOKEN_RATE, keyboard) {
            if (it.isBlank()) 0 else it.toString().toIntOrNull()
        }

        AdvancedTextField(
            "Token bucket size (bytes)",
            PreferenceStore.QOS_TOKEN_BUCKET_SIZE,
            keyboard
        ) {
            if (it.isBlank()) 0 else it.toString().toIntOrNull()
        }

        AdvancedTextField(
            "Peak bandwidth (bytes/sec)",
            PreferenceStore.QOS_PEAK_BANDWIDTH,
            keyboard
        ) {
            if (it.isBlank()) 0 else it.toString().toIntOrNull()
        }

        AdvancedTextField("Latency (us)", PreferenceStore.QOS_LATENCY, keyboard) {
            if (it.isBlank()) 0 else it.toString().toIntOrNull()
        }

        AdvancedTextField("Delay variation (us)", PreferenceStore.QOS_DELAY_VARIATION, keyboard) {
            if (it.isBlank()) 0 else it.toString().toIntOrNull()
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun <T> AdvancedTextField(
    desc: String,
    pref: PreferenceStore.Preference<T>,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    transform: (CharSequence) -> T?
) {
    var prefValue by rememberPreference(pref)
    val value = rememberTextFieldState(prefValue.toString())

    DisposableEffect(prefValue) {
        value.setTextAndPlaceCursorAtEnd(prefValue.toString())
        onDispose {
            transform(value.text)?.let { prefValue = it }
        }
    }

    OutlinedTextField(
        state = value,
        label = { Text(desc) },
        inputTransformation = InputTransformation.byValue { current, proposed ->
            transform(proposed)?.toString() ?: current
        },
        keyboardOptions = keyboard,
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp),
    )
}