package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.bt.ExternalProtocol
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.rememberPreference

/**
 * Dropdown entry + bottom-sheet "External Plugin Picker": a master toggle for parallel external
 * output (HID/RFCOMM) plus the list of installed plugins to enable/disable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPluginsModal() {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.external_output)) },
        leadingIcon = { Icon(Icons.Default.Extension, null) },
        onClick = { showSheet = true }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = { ExternalPluginsContent() }
        )
    }
}

@Composable
fun ExternalPluginsContent() {
    val context = LocalContext.current
    val connectionMode by context.getPreferenceState(PreferenceStore.CONNECTION_MODE)
    val isExternalMode = connectionMode == ConnectionMode.EXTERNAL.ordinal

    var externalOutputEnabled by rememberPreference(PreferenceStore.ENABLE_EXTERNAL_OUTPUT)
    var enabledPlugins by rememberPreference(PreferenceStore.ENABLED_EXTERNAL_PLUGINS)
    val plugins = remember { ExternalProtocol.discover(context) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.external_output),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Master toggle. In EXTERNAL mode external output is implied (always on, can't disable).
        SwitchPreference(
            title = stringResource(R.string.enable_external_output),
            desc = stringResource(R.string.enable_external_output_desc),
            icon = Icons.Default.Lan,
            checked = isExternalMode || externalOutputEnabled,
            enabled = !isExternalMode,
            onToggle = { externalOutputEnabled = it }
        )

        if (plugins.isEmpty()) {
            Text(
                stringResource(R.string.external_no_plugins),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            plugins.forEach { plugin ->
                SwitchPreference(
                    title = plugin.label,
                    desc = plugin.packageName,
                    icon = Icons.Default.Extension,
                    checked = plugin.packageName in enabledPlugins,
                    onToggle = { on ->
                        enabledPlugins =
                            if (on) enabledPlugins + plugin.packageName
                            else enabledPlugins - plugin.packageName
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
