package dev.fabik.bluetoothhid.bt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Bridges scans to external transport extensions via broadcast Intents (see [ExternalProtocol]).
 *
 * The core owns scanning + processing; an extension owns the actual protocol (TCP/HTTP/MQTT/…),
 * its settings and its connection lifecycle. This is how non-Bluetooth transports stay OUT of
 * the core app while still being usable: install the matching extension, pick "External" mode,
 * and enable it in the plugin list.
 *
 * Two-way contract:
 *  - core → extension: targeted [ExternalProtocol.ACTION_BARCODE_SCANNED] per enabled package,
 *  - extension → core: optional [ExternalProtocol.ACTION_SEND_RESULT] surfaced via [lastResultFlow].
 */
class ExternalController(private val context: Context) {
    companion object {
        private const val TAG = "ExternalController"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Package names the user enabled in the plugin list — kept in sync with the preference.
    @Volatile
    private var enabledPackages: Set<String> = emptySet()
    private var enabledObserverJob: Job? = null

    // Last delivery status reported back by an extension (null = nothing reported yet).
    // Fire-and-forget broadcasts have no return value, so this is only populated if an
    // extension opts into the ACTION_SEND_RESULT channel.
    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResultFlow = _lastResult.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActiveFlow = _isActive.asStateFlow()

    private var resultReceiver: BroadcastReceiver? = null

    fun start() {
        if (resultReceiver != null) return

        // Keep the enabled-packages cache in sync so publishScan() stays non-suspending.
        enabledObserverJob = scope.launch {
            context.getPreference(PreferenceStore.ENABLED_EXTERNAL_PLUGINS).collect {
                enabledPackages = it
                Log.i(TAG, "Enabled external plugins: $it")
            }
        }

        // Listen for delivery status reported back by extensions.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ExternalProtocol.ACTION_SEND_RESULT) return
                val scanId = intent.getStringExtra(ExternalProtocol.EXTRA_SCAN_ID)
                val ok = intent.getBooleanExtra(ExternalProtocol.EXTRA_RESULT_OK, false)
                val detail = intent.getStringExtra(ExternalProtocol.EXTRA_RESULT_DETAIL)
                // scan_id is for correlation/logs only — NOT user-facing. Show the plugin's
                // human-readable detail (or a plain sent/failed) to the user.
                val status = detail ?: if (ok) "sent" else "failed"
                _lastResult.update { status }
                Log.i(TAG, "External send result: scan=$scanId ok=$ok detail=$detail")
            }
        }
        // Exported: the result comes from another app. Could be hardened with a dedicated
        // permission on the sender side later.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ExternalProtocol.ACTION_SEND_RESULT),
            ContextCompat.RECEIVER_EXPORTED
        )
        resultReceiver = receiver
        _isActive.update { true }
        Log.i(TAG, "External output active — scans will be broadcast to enabled extensions")
    }

    fun stop() {
        if (resultReceiver == null) return // already stopped — keep idempotent (no log spam)
        enabledObserverJob?.cancel()
        enabledObserverJob = null
        resultReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        resultReceiver = null
        _isActive.update { false }
        Log.i(TAG, "External output stopped")
    }

    /**
     * Publishes a scan to every enabled extension via a targeted (explicit) broadcast. Targeting
     * by package both restricts delivery to chosen plugins and bypasses the Android 8+ restriction
     * on implicit broadcasts reaching manifest-declared receivers. Both the raw and the processed
     * value are sent so the extension can pick what it needs.
     */
    fun publishScan(
        rawValue: String,
        processedValue: String,
        format: String?,
        timestamp: Long,
        source: String,
        scannerId: String?,
        regexGroups: List<String>,
        imageName: String?,
    ) {
        val targets = enabledPackages
        if (targets.isEmpty()) {
            Log.i(TAG, "No external plugins enabled — scan not forwarded")
            return
        }
        // One id per scan, shared across all target plugins; echoed back in ACTION_SEND_RESULT.
        val scanId = UUID.randomUUID().toString()
        val base = Intent(ExternalProtocol.ACTION_BARCODE_SCANNED).apply {
            putExtra(ExternalProtocol.EXTRA_SCAN_ID, scanId)
            putExtra(ExternalProtocol.EXTRA_RAW_VALUE, rawValue)
            putExtra(ExternalProtocol.EXTRA_PROCESSED_VALUE, processedValue)
            putExtra(ExternalProtocol.EXTRA_FORMAT, format)
            putExtra(ExternalProtocol.EXTRA_TIMESTAMP, timestamp)
            putExtra(ExternalProtocol.EXTRA_SOURCE, source)
            putExtra(ExternalProtocol.EXTRA_SCANNER_ID, scannerId)
            if (regexGroups.isNotEmpty())
                putExtra(ExternalProtocol.EXTRA_REGEX_GROUPS, regexGroups.toTypedArray())
            imageName?.let { putExtra(ExternalProtocol.EXTRA_IMAGE_NAME, it) }
        }
        targets.forEach { pkg ->
            // Per-package copy so each delivery is an explicit broadcast. Permission-protected
            // so only extensions holding PERMISSION_RECEIVE_SCANS get the (sensitive) data.
            val intent = Intent(base).setPackage(pkg)
            context.sendBroadcast(intent, ExternalProtocol.PERMISSION_RECEIVE_SCANS)
        }
        Log.i(TAG, "Published scan $scanId to ${targets.size} plugin(s): $processedValue")
    }
}
