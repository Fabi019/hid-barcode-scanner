package dev.fabik.bluetoothhid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getMultiPreferenceState
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.runBlocking

const val EXTERNAL_INPUT_INTENT_ACTION = "dev.fabik.bluetoothhid.action.EXTERNAL_INPUT"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalInputOptionsModal() {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.external_input)) },
        onClick = {
            showSheet = true
        }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                ExternalInputOptionsContent()
            }
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun ExternalInputOptionsContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        ExternalInputHeader()

        Text(
            stringResource(R.string.ext_input_desc),
            style = MaterialTheme.typography.bodyMedium
        )

        SelectionContainer {
            Text(
                text = EXTERNAL_INPUT_INTENT_ACTION,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        AdvancedToggleOption(
            stringResource(R.string.enabled),
            PreferenceStore.ENABLE_EXTERNAL_INPUT
        )

        Text(
            stringResource(R.string.ext_input_extra_desc),
            style = MaterialTheme.typography.bodyMedium
        )

        AdvancedTextField(
            stringResource(R.string.value_extra),
            PreferenceStore.EXT_INPUT_KEY_VALUE
        ) { it.toString() }
        AdvancedTextField(
            stringResource(R.string.format_extra),
            PreferenceStore.EXT_INPUT_KEY_TYPE
        ) { it.toString() }
        AdvancedTextField(
            stringResource(R.string.source_extra),
            PreferenceStore.EXT_INPUT_KEY_SOURCE
        ) { it.toString() }
    }
}

@Composable
private fun ExternalInputHeader() {
    val context = LocalContext.current

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(
            stringResource(R.string.external_input),
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(
            onClick = {
                runBlocking {
                    arrayOf(
                        PreferenceStore.EXT_INPUT_KEY_VALUE,
                        PreferenceStore.EXT_INPUT_KEY_TYPE,
                        PreferenceStore.EXT_INPUT_KEY_SOURCE,
                    ).forEach {
                        context.setPreference(it, it.defaultValue)
                    }
                    context.setPreference(
                        PreferenceStore.ENABLE_EXTERNAL_INPUT,
                        PreferenceStore.ENABLE_EXTERNAL_INPUT.defaultValue
                    )
                }
            },
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterEnd)
                .tooltip(stringResource(R.string.reset))
        ) {
            Icon(Icons.Filled.Restore, "Reset to default", Modifier.size(28.dp))
        }
    }
}

@Composable
fun ExternalInputReceiver(onReceive: (value: String, format: String?, source: String?) -> Unit) {
    val context = LocalContext.current
    val currentOnReceive by rememberUpdatedState(onReceive)

    val enabled by context.getPreferenceState(PreferenceStore.ENABLE_EXTERNAL_INPUT)
    val prefs by context.getMultiPreferenceState(
        PreferenceStore.EXT_INPUT_KEY_VALUE,
        PreferenceStore.EXT_INPUT_KEY_TYPE,
        PreferenceStore.EXT_INPUT_KEY_SOURCE
    )
    val currentPrefs by rememberUpdatedState(prefs)

    if (enabled == true) {
        DisposableEffect(context, currentOnReceive) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val valueKey =
                        PreferenceStore.EXT_INPUT_KEY_VALUE.extract(currentPrefs ?: return)
                            .ifBlank { return@onReceive }
                    val typeKey = PreferenceStore.EXT_INPUT_KEY_TYPE.extract(currentPrefs ?: return)
                    val sourceKey =
                        PreferenceStore.EXT_INPUT_KEY_SOURCE.extract(currentPrefs ?: return)

                    val value = intent.getStringExtra(valueKey) ?: return
                    val format = intent.getStringExtra(typeKey)
                    val source = intent.getStringExtra(sourceKey)

                    Log.d("ExternalInput", "External input received: $value, $format, $source")
                    currentOnReceive(value, format, source)
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(EXTERNAL_INPUT_INTENT_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )

            Log.d("ExternalInput", "Receiver registered")

            onDispose {
                Log.d("ExternalInput", "Receiver unregistered")
                context.unregisterReceiver(receiver)
            }
        }
    }
}