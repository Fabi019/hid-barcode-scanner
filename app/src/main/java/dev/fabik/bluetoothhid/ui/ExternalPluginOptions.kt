package dev.fabik.bluetoothhid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.bt.ExternalController
import dev.fabik.bluetoothhid.bt.ExternalPlugin
import dev.fabik.bluetoothhid.bt.ExternalProtocol
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import dev.fabik.bluetoothhid.ui.theme.StatusConnected
import dev.fabik.bluetoothhid.ui.theme.StatusDown
import dev.fabik.bluetoothhid.ui.theme.StatusWaiting
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull
import kotlinx.coroutines.delay

// How often to ping plugins for status while the picker sheet is open (snappy, but light).
private const val STATUS_POLL_INTERVAL_MS = 2_000L

/**
 * Dropdown entry + bottom-sheet "External Plugin Picker": a master toggle for parallel external
 * output (HID/RFCOMM) plus the list of installed plugins to enable/disable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPluginsModal() {
    val context = LocalContext.current

    val connectionMode by context.getPreferenceState(PreferenceStore.CONNECTION_MODE)
    val isExternalMode = connectionMode == ConnectionMode.EXTERNAL.ordinal
    var externalOutputEnabled by rememberPreferenceNull(PreferenceStore.ENABLE_EXTERNAL_OUTPUT)

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    ButtonPreference(
        title = stringResource(R.string.external_output),
        desc = stringResource(R.string.enable_external_output_desc),
        icon = Icons.Default.Extension,
        onClick = { showSheet = true },
        extra = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalDivider(
                    Modifier
                        .height(32.dp)
                        .padding(horizontal = 24.dp)
                )
                externalOutputEnabled?.let { c ->
                    Switch(
                        c || isExternalMode,
                        enabled = !isExternalMode,
                        onCheckedChange = {
                            externalOutputEnabled = it
                        }, modifier = Modifier.semantics(mergeDescendants = true) {
                            stateDescription =
                                "External output enabled is ${if (c) "On" else "Off"}"
                        })
                }
            }
        },
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
    var enabledPlugins by rememberPreference(PreferenceStore.ENABLED_EXTERNAL_PLUGINS)
    var plugins by remember { mutableStateOf(ExternalProtocol.discover(context)) }

    // Re-run discover() when a package is installed, removed, or updated while the sheet is open.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                plugins = ExternalProtocol.discover(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Bind (without starting) the already-running BT service to read live plugin liveness reported
    // by the heartbeat. Null binder (service not up) → empty map → rows just show no status dot.
    val controller = rememberBluetoothControllerService(context, startStop = false)?.getController()
    val health by (controller?.externalPluginHealthFlow
        ?.collectAsState(initial = emptyMap())
        ?: remember { mutableStateOf(emptyMap<String, ExternalController.PluginHealth>()) })

    // While the picker is open, poll plugin status for near-live dots: ping immediately (fresh on
    // open) then every few seconds. Auto-cancels when the sheet closes (effect leaves composition),
    // so it never runs in the background or drains the battery.
    LaunchedEffect(controller) {
        while (controller != null) {
            controller.requestExternalStatus()
            delay(STATUS_POLL_INTERVAL_MS)
        }
    }

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

        if (plugins.isEmpty()) {
            Text(
                stringResource(R.string.external_no_plugins),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Text(
                stringResource(R.string.external_long_press_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            plugins.forEach { plugin ->
                PluginRow(
                    plugin = plugin,
                    checked = plugin.packageName in enabledPlugins,
                    health = health[plugin.packageName],
                    onToggle = { on ->
                        enabledPlugins =
                            if (on) enabledPlugins + plugin.packageName
                            else enabledPlugins - plugin.packageName
                    },
                    onOpenSettings = {
                        if (plugin.hasSettings) {
                            runCatching {
                                context.startActivity(
                                    Intent(ExternalProtocol.ACTION_PLUGIN_SETTINGS)
                                        .setPackage(plugin.packageName)
                                )
                            }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.external_plugin_no_settings),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PluginRow(
    plugin: ExternalPlugin,
    checked: Boolean,
    health: ExternalController.PluginHealth?,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle(!checked) },
                onLongClick = onOpenSettings
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Extension, null, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(plugin.label, style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(
                plugin.author,
                plugin.version?.let { "v$it" }
            ).joinToString(" • ").ifEmpty { plugin.packageName }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Live transport status from the plugin's heartbeat reply — only meaningful while enabled.
            if (checked && health != null) PluginStatusLine(health)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** A colored dot + the plugin's reported transport status (connected / waiting / down). */
@Composable
private fun PluginStatusLine(health: ExternalController.PluginHealth) {
    val connected = health.detail?.contains("connected", ignoreCase = true) == true
    val (color, fallback) = when {
        !health.running -> StatusDown to "down"        // red — service not running
        connected -> StatusConnected to "connected"     // green — peer connected
        else -> StatusWaiting to "waiting"              // amber — up but no peer yet
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            health.detail ?: fallback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
