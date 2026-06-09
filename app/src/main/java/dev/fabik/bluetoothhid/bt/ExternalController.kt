package dev.fabik.bluetoothhid.bt

import android.content.BroadcastReceiver
import android.content.ComponentName
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
 * Channels (all targeted by package + permission-protected for core→extension):
 *  - core → extension: [ExternalProtocol.ACTION_BARCODE_SCANNED] per enabled package,
 *  - core → extension: [ExternalProtocol.ACTION_PLUGIN_SET_ENABLED] lifecycle (warm up / release),
 *  - core → extension: [ExternalProtocol.ACTION_PLUGIN_PING] liveness probe,
 *  - extension → core: optional [ExternalProtocol.ACTION_SEND_RESULT] surfaced via [lastResultFlow],
 *  - extension → core: optional [ExternalProtocol.ACTION_PLUGIN_STATUS] surfaced via [pluginHealthFlow].
 *
 * Self-healing: a plugin's transport lives in its own (foreground) service, which the OS can kill.
 * The core can't observe that directly, so it pings each enabled plugin on an interval; a missing
 * reply (or running=false) triggers a fresh SET_ENABLED(true) to revive it. Sending any of these
 * targeted broadcasts also cold-starts a dead plugin process and grants it the short FGS-start
 * window it needs to (re)spawn its service.
 */
class ExternalController(private val context: Context) {
    companion object {
        private const val TAG = "ExternalController"

        // How often to ping enabled plugins for liveness.
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
        // A plugin not heard from within this window is considered dead and revived. ~2.5 intervals
        // tolerates one dropped/late reply before we act.
        private const val HEALTH_DEADLINE_MS = 50_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Package names the user enabled in the plugin list — kept in sync with the preference.
    @Volatile
    private var enabledPackages: Set<String> = emptySet()
    private var enabledObserverJob: Job? = null
    private var heartbeatJob: Job? = null

    // Delivery statuses reported back by extensions. An *event* stream (not a StateFlow): two
    // consecutive identical statuses ("sent", "sent") must both reach the UI, which a StateFlow
    // would swallow as a no-op. Only populated if an extension opts into the ACTION_SEND_RESULT
    // channel (fire-and-forget broadcasts have no return value).
    private val _lastResult = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val lastResultFlow = _lastResult.asSharedFlow()

    /** Per-package liveness snapshot, keyed by plugin package name. Updated from ACTION_PLUGIN_STATUS. */
    data class PluginHealth(
        val running: Boolean,
        val detail: String?,
        val lastSeen: Long,
    )

    private val _pluginHealth = MutableStateFlow<Map<String, PluginHealth>>(emptyMap())
    val pluginHealthFlow = _pluginHealth.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActiveFlow = _isActive.asStateFlow()

    private var resultReceiver: BroadcastReceiver? = null

    fun start() {
        if (resultReceiver != null) return

        // Keep the enabled-packages cache in sync so publishScan() stays non-suspending, AND drive
        // the lifecycle channel: newly-enabled plugins get SET_ENABLED(true) to warm up (plus a PING
        // so their status comes back right away instead of at the next poll), removed ones get
        // SET_ENABLED(false). The first emission treats every enabled plugin as "added", so simply
        // starting external output warms up whatever is already enabled.
        enabledObserverJob = scope.launch {
            context.getPreference(PreferenceStore.ENABLED_EXTERNAL_PLUGINS).collect { next ->
                val previous = enabledPackages
                enabledPackages = next
                Log.i(TAG, "Enabled external plugins: $next")

                (next - previous).forEach { pkg ->
                    sendSetEnabled(pkg, true)
                    sendControl(pkg, ExternalProtocol.ACTION_PLUGIN_PING)
                }
                (previous - next).forEach { pkg ->
                    sendSetEnabled(pkg, false)
                    _pluginHealth.update { it - pkg } // stop tracking a disabled plugin
                }
            }
        }

        // Listen for delivery status (ACTION_SEND_RESULT) and liveness replies (ACTION_PLUGIN_STATUS).
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ExternalProtocol.ACTION_SEND_RESULT -> onSendResult(intent)
                    ExternalProtocol.ACTION_PLUGIN_STATUS -> onPluginStatus(intent)
                }
            }
        }
        // Exported: the result comes from another app. Could be hardened with a dedicated
        // permission on the sender side later.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(ExternalProtocol.ACTION_SEND_RESULT)
                addAction(ExternalProtocol.ACTION_PLUGIN_STATUS)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        resultReceiver = receiver

        heartbeatJob = scope.launch { heartbeatLoop() }

        _isActive.update { true }
        Log.i(TAG, "External output active — scans will be broadcast to enabled extensions")
    }

    fun stop() {
        if (resultReceiver == null) return // already stopped — keep idempotent (no log spam)
        // Tell currently-enabled plugins to release their transport (best-effort).
        enabledPackages.forEach { sendSetEnabled(it, false) }

        enabledObserverJob?.cancel()
        enabledObserverJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        resultReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        resultReceiver = null
        _pluginHealth.update { emptyMap() }
        _isActive.update { false }
        Log.i(TAG, "External output stopped")
    }

    private fun onSendResult(intent: Intent) {
        val scanId = intent.getStringExtra(ExternalProtocol.EXTRA_SCAN_ID)
        val ok = intent.getBooleanExtra(ExternalProtocol.EXTRA_RESULT_OK, false)
        val detail = intent.getStringExtra(ExternalProtocol.EXTRA_RESULT_DETAIL)
        // scan_id is for correlation/logs only — NOT user-facing. Show the plugin's
        // human-readable detail (or a plain sent/failed) to the user.
        val status = detail ?: if (ok) "sent" else "failed"
        _lastResult.tryEmit(status)
        Log.i(TAG, "External send result: scan=$scanId ok=$ok detail=$detail")
    }

    private fun onPluginStatus(intent: Intent) {
        val pkg = intent.getStringExtra(ExternalProtocol.EXTRA_PACKAGE) ?: return
        val running = intent.getBooleanExtra(ExternalProtocol.EXTRA_RUNNING, false)
        val detail = intent.getStringExtra(ExternalProtocol.EXTRA_STATUS_DETAIL)
        _pluginHealth.update { it + (pkg to PluginHealth(running, detail, System.currentTimeMillis())) }
        Log.i(TAG, "Plugin status: $pkg running=$running detail=$detail")
    }

    /**
     * Pings enabled plugins on demand so their STATUS reply refreshes the UI right away, without
     * waiting for the next heartbeat. Used by the plugin picker for snappy/live status (ping-only:
     * no revive, that stays the heartbeat's job).
     */
    fun requestStatus() {
        enabledPackages.forEach { sendControl(it, ExternalProtocol.ACTION_PLUGIN_PING) }
    }

    /**
     * Kicks every enabled plugin NOW: SET_ENABLED(true) cold-starts/(re)starts a down transport,
     * PING asks it to report status right away. Used on entering the scanner and on the user's
     * "plugin not responding → retry" tap, so we don't wait up to a full heartbeat for revival.
     * Idempotent (the plugin's start is), so it's safe to call repeatedly.
     */
    fun warmUpEnabled() {
        enabledPackages.forEach { pkg ->
            sendSetEnabled(pkg, true)
            sendControl(pkg, ExternalProtocol.ACTION_PLUGIN_PING)
        }
    }

    /**
     * Pings every enabled plugin on an interval and revives any that are silent or report
     * running=false. Reviving = SET_ENABLED(true), which the plugin's lifecycle receiver turns
     * back into a (re)start of its transport service. The first pass runs immediately (delay is at
     * the end) so a freshly-activated output checks/revives at t=0 instead of after a full interval.
     */
    private suspend fun heartbeatLoop() {
        while (scope.isActive) {
            val targets = enabledPackages
            if (targets.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val health = _pluginHealth.value
                targets.forEach { pkg ->
                    sendControl(pkg, ExternalProtocol.ACTION_PLUGIN_PING)

                    val last = health[pkg]
                    val stale = last == null || (now - last.lastSeen) > HEALTH_DEADLINE_MS
                    val down = last != null && !last.running
                    if (stale || down) {
                        Log.w(TAG, "Plugin $pkg looks dead (stale=$stale down=$down) — reviving")
                        sendSetEnabled(pkg, true)
                    }
                }
            }
            delay(HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun sendSetEnabled(pkg: String, enabled: Boolean) {
        sendControl(pkg, ExternalProtocol.ACTION_PLUGIN_SET_ENABLED) {
            putExtra(ExternalProtocol.EXTRA_ENABLED, enabled)
        }
        Log.i(TAG, "Sent SET_ENABLED($enabled) to $pkg")
    }

    /** Targeted, permission-protected control broadcast to a single plugin. */
    private inline fun sendControl(pkg: String, action: String, extras: Intent.() -> Unit = {}) {
        dispatch(pkg, Intent(action).apply(extras))
    }

    // Resolved receiver components per "pkg|action". A plugin's receiver class rarely changes, so we
    // cache it; getOrPut still resolves newly-installed plugins lazily.
    private val receiverCache = java.util.concurrent.ConcurrentHashMap<String, List<ComponentName>>()

    private fun resolveReceivers(pkg: String, action: String): List<ComponentName> =
        receiverCache.getOrPut("$pkg|$action") {
            runCatching {
                context.packageManager
                    .queryBroadcastReceivers(Intent(action).setPackage(pkg), 0)
                    .mapNotNull { it.activityInfo?.let { ai -> ComponentName(ai.packageName, ai.name) } }
            }.getOrDefault(emptyList())
        }

    /**
     * Delivers [intent] to [pkg] by EXPLICIT receiver component(s). Targeting the component — not
     * just the package — is REQUIRED to deliver to (and cold-start) a plugin in Android's "stopped"
     * state (fresh install, force-stopped, or its process died): a package-only broadcast is
     * silently dropped for stopped apps. FLAG_INCLUDE_STOPPED_PACKAGES is the documented companion.
     * Falls back to package targeting if the receiver can't be resolved.
     */
    private fun dispatch(pkg: String, intent: Intent) {
        intent.setPackage(pkg).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val components = resolveReceivers(pkg, intent.action ?: return)
        if (components.isEmpty()) {
            context.sendBroadcast(intent, ExternalProtocol.PERMISSION_RECEIVE_SCANS)
        } else {
            components.forEach { comp ->
                context.sendBroadcast(
                    Intent(intent).setComponent(comp),
                    ExternalProtocol.PERMISSION_RECEIVE_SCANS
                )
            }
        }
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
            // Per-package copy delivered by explicit component (see dispatch) so even a stopped
            // plugin is woken; permission-protected so only PERMISSION_RECEIVE_SCANS holders get it.
            dispatch(pkg, Intent(base))
        }
        Log.i(TAG, "Published scan $scanId to ${targets.size} plugin(s): $processedValue")
    }
}
