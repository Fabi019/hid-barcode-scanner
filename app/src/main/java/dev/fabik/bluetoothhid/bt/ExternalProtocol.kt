package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * A discovered external transport extension (an app with a receiver for [ExternalProtocol.ACTION_BARCODE_SCANNED]).
 */
data class ExternalPlugin(
    val packageName: String,
    val label: String,
    val author: String? = null,
    val version: String? = null,
    val hasSettings: Boolean = false,
)

/**
 * Broadcast contract between the core scanner app and external transport extensions.
 *
 * Rationale: the core app stays focused on scanning + Bluetooth HID. Any non-Bluetooth
 * transport (TCP, HTTP, MQTT, WebSocket, …) lives in a separately installable companion app
 * that receives [ACTION_BARCODE_SCANNED] and handles delivery over its own protocol — with its
 * own settings, lifecycle and status UI. Optionally an extension reports delivery status back
 * via [ACTION_SEND_RESULT], which the core can surface to the user.
 *
 * Keep this file in sync with the companion/extension apps (it IS the public API).
 */
object ExternalProtocol {
    // ── Core → extension ────────────────────────────────────────────────────────────────────
    /** A barcode was scanned. Broadcast to any extension holding [PERMISSION_RECEIVE_SCANS]. */
    const val ACTION_BARCODE_SCANNED = "dev.fabik.bluetoothhid.action.BARCODE_SCANNED"

    /**
     * Lifecycle signal: the core toggled this plugin on/off (enabled in the picker, or external
     * output as a whole started/stopped). Lets a plugin warm up its transport ahead of the first
     * scan (or release it when disabled) instead of cold-starting on the first ACTION_BARCODE_SCANNED.
     * Carries [EXTRA_ENABLED]. Sent targeted + permission-protected, like scans.
     */
    const val ACTION_PLUGIN_SET_ENABLED = "dev.fabik.bluetoothhid.plugin.action.SET_ENABLED"

    /**
     * Health probe. The core periodically pings each enabled plugin; the plugin answers with
     * [ACTION_PLUGIN_STATUS]. No reply (or a reply with running=false) within the deadline makes the
     * core re-send [ACTION_PLUGIN_SET_ENABLED] to revive the plugin's service (self-healing).
     */
    const val ACTION_PLUGIN_PING = "dev.fabik.bluetoothhid.plugin.action.PING"

    // ── Extension → core (optional status channel) ──────────────────────────────────────────
    /** Result of forwarding a scan, so the core can show "sent / failed" instead of just "published". */
    const val ACTION_SEND_RESULT = "dev.fabik.bluetoothhid.action.SEND_RESULT"

    /**
     * Health/liveness reply (answer to [ACTION_PLUGIN_PING], or sent proactively when the plugin's
     * transport state changes). Carries [EXTRA_PACKAGE], [EXTRA_RUNNING] and [EXTRA_STATUS_DETAIL].
     */
    const val ACTION_PLUGIN_STATUS = "dev.fabik.bluetoothhid.plugin.action.STATUS"

    /**
     * Optional settings entry-point. An extension that has a settings screen declares an
     * <activity> with an <intent-filter> for this action; the core opens it (targeted by package)
     * when the user long-presses the plugin. Plugins without it simply have no settings.
     */
    const val ACTION_PLUGIN_SETTINGS = "dev.fabik.bluetoothhid.plugin.action.SETTINGS"

    /**
     * Permission an extension must hold to receive scan broadcasts. Declared by the core
     * (see AndroidManifest). protectionLevel is "normal" for an open plugin ecosystem; switch
     * to "signature" if only first-party extensions should be allowed.
     */
    const val PERMISSION_RECEIVE_SCANS = "dev.fabik.bluetoothhid.permission.RECEIVE_SCANS"

    /** Optional <meta-data> on the extension's receiver giving a human-friendly display name. */
    const val META_PLUGIN_LABEL = "dev.fabik.bluetoothhid.plugin.label"

    /** Optional <meta-data> on the extension's receiver giving the plugin's author. */
    const val META_PLUGIN_AUTHOR = "dev.fabik.bluetoothhid.plugin.author"

    // ── Extras for ACTION_BARCODE_SCANNED ───────────────────────────────────────────────────
    /**
     * String — unique id for this scan. The core generates it and an extension MUST echo it
     * back in [ACTION_SEND_RESULT] so the core can correlate a result with the originating scan
     * (important with multiple plugins and rapid scans).
     */
    const val EXTRA_SCAN_ID = "scan_id"
    /** String — the value exactly as decoded by the scanner. */
    const val EXTRA_RAW_VALUE = "raw_value"
    /** String — the value after the core's template/custom/regex processing. */
    const val EXTRA_PROCESSED_VALUE = "processed_value"
    /** String — barcode symbology, e.g. "Code128", "QrCode". */
    const val EXTRA_FORMAT = "format"
    /** Long — epoch millis of the scan. */
    const val EXTRA_TIMESTAMP = "timestamp"
    /** String — origin of the value, e.g. "SCAN". */
    const val EXTRA_SOURCE = "source"
    /** String? — scanner/device identifier. */
    const val EXTRA_SCANNER_ID = "scanner_id"
    /** String[] — optional regex capture groups. */
    const val EXTRA_REGEX_GROUPS = "regex_groups"
    /** String? — optional saved-image filename associated with the scan. */
    const val EXTRA_IMAGE_NAME = "image_name"

    // ── Extras for ACTION_SEND_RESULT ───────────────────────────────────────────────────────
    /** Boolean — whether the extension actually delivered the value. */
    const val EXTRA_RESULT_OK = "result_ok"
    /** String? — human-readable status detail (e.g. "TCP 192.168.0.5:51000"). */
    const val EXTRA_RESULT_DETAIL = "result_detail"

    // ── Extras for ACTION_PLUGIN_SET_ENABLED ────────────────────────────────────────────────
    /** Boolean — true = plugin enabled (warm up transport), false = disabled (release transport). */
    const val EXTRA_ENABLED = "enabled"

    // ── Extras for ACTION_PLUGIN_STATUS ─────────────────────────────────────────────────────
    /** String — the plugin's own package name, so the core can correlate the reply to a plugin. */
    const val EXTRA_PACKAGE = "package"
    /** Boolean — whether the plugin's transport/service is currently up. */
    const val EXTRA_RUNNING = "running"
    /** String? — human-readable transport status (e.g. "TCP server :51000 (connected)"). */
    const val EXTRA_STATUS_DETAIL = "status_detail"

    /**
     * Finds installed extensions that declare a receiver for [ACTION_BARCODE_SCANNED] AND request
     * [PERMISSION_RECEIVE_SCANS]. The permission check matters: scan broadcasts are sent
     * permission-protected, so an extension that declares a receiver but doesn't hold the
     * permission would never actually receive anything — listing it would be misleading.
     * Requires a matching <queries> entry in the core manifest for Android 11+ visibility.
     */
    @Suppress("DEPRECATION", "QueryPermissionsNeeded")
    fun discover(context: Context): List<ExternalPlugin> {
        val pm = context.packageManager
        val intent = Intent(ACTION_BARCODE_SCANNED)
        return pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
            .mapNotNull { ri ->
                val info = ri.activityInfo ?: return@mapNotNull null
                if (!requestsReceivePermission(pm, info.packageName)) return@mapNotNull null
                val label = info.metaData?.getString(META_PLUGIN_LABEL)
                    ?: info.loadLabel(pm).toString()
                val author = info.metaData?.getString(META_PLUGIN_AUTHOR)
                val version = runCatching {
                    pm.getPackageInfo(info.packageName, 0).versionName
                }.getOrNull()
                val hasSettings = runCatching {
                    pm.resolveActivity(
                        Intent(ACTION_PLUGIN_SETTINGS).setPackage(info.packageName), 0
                    ) != null
                }.getOrDefault(false)
                // A plugin that doesn't declare a receiver for PING will never reply to liveness
                // probes — the health monitor would perpetually see it as dead and keep reviving it.
                val hasLifecycle = runCatching {
                    pm.queryBroadcastReceivers(
                        Intent(ACTION_PLUGIN_PING).setPackage(info.packageName), 0
                    ).isNotEmpty()
                }.getOrDefault(false)
                if (!hasLifecycle) return@mapNotNull null
                ExternalPlugin(info.packageName, label, author, version, hasSettings)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** True if [pkg] declares <uses-permission> for [PERMISSION_RECEIVE_SCANS]. */
    @Suppress("DEPRECATION")
    private fun requestsReceivePermission(pm: PackageManager, pkg: String): Boolean = runCatching {
        pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(PERMISSION_RECEIVE_SCANS) == true
    }.getOrDefault(false)
}
