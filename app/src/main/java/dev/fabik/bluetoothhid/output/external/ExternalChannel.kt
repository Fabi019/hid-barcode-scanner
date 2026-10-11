package dev.fabik.bluetoothhid.output.external

import android.content.Context
import android.util.Log
import dev.fabik.bluetoothhid.output.ChannelEvent
import dev.fabik.bluetoothhid.output.ChannelState
import dev.fabik.bluetoothhid.output.OutputChannel
import dev.fabik.bluetoothhid.output.ScanEvent
import dev.fabik.bluetoothhid.output.SendResult
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.ExtraKeys
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import dev.fabik.bluetoothhid.utils.getPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Output channel for external plugin transports. Wraps [ExternalController], which owns the
 * broadcast bridge (see [ExternalProtocol][dev.fabik.bluetoothhid.bt.ExternalProtocol]).
 *
 * [parallelSafe]: broadcasting to plugins works alongside any Bluetooth transport, so this
 * channel can run as a mirror of HID/RFCOMM (the ENABLE_EXTERNAL_OUTPUT toggle).
 */
class ExternalChannel(
    private val context: Context,
    private val controller: ExternalController,
    private val scannerId: () -> String? = { null },
) : OutputChannel {

    companion object {
        private const val TAG = "ExternalChannel"
    }

    override val mode: ConnectionMode = ConnectionMode.EXTERNAL
    override val parallelSafe: Boolean = true

    private val _state = MutableStateFlow<ChannelState>(ChannelState.Stopped)
    override val state: StateFlow<ChannelState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChannelEvent>(0, 8, BufferOverflow.DROP_OLDEST)
    override val events: Flow<ChannelEvent> = _events.asSharedFlow()

    // Delegated surfaces for the UI (delivery results, per-plugin liveness, runtime state).
    val lastResultFlow = controller.lastResultFlow
    val pluginHealthFlow = controller.pluginHealthFlow
    val isActiveFlow = controller.isActiveFlow

    /** Ping enabled external plugins now to refresh their reported status (used by the picker UI). */
    fun requestStatus() = controller.requestStatus()

    /** Kick enabled external plugins now (SET_ENABLED + ping) to start/revive their transport. */
    fun warmUp() = controller.warmUpEnabled()

    override suspend fun start() {
        controller.start()
        _state.value = ChannelState.Ready
    }

    override suspend fun stop() {
        controller.stop()
        _state.value = ChannelState.Stopped
    }

    override suspend fun send(scan: ScanEvent): SendResult {
        return try {
            val prefs = context.getPreferences(
                PreferenceStore.EXTRA_KEYS,
                PreferenceStore.TEMPLATE_TEXT,
                PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS,
                PreferenceStore.DIRECT_APPLY_TEMPLATE,
            ).first()

            val extraKeys =
                if (scan.withExtraKeys) PreferenceStore.EXTRA_KEYS.extractEnum(prefs)
                else ExtraKeys.NONE
            val template =
                if (scan.withExtraKeys && extraKeys == ExtraKeys.CUSTOM)
                    PreferenceStore.TEMPLATE_TEXT.extract(prefs)
                else ""
            val directlyApplyTemplate =
                PreferenceStore.DIRECT_APPLY_TEMPLATE.extract(prefs)
            val preserveUnsupported =
                PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS.extract(prefs)

            // External receives the final text exactly like HID does: the simple extra-key
            // suffixes (ENTER/TAB/SPACE) apply here too — they double as the line
            // terminator for plugins, which forward the text verbatim. CUSTOM keeps using
            // the template ({ENTER} → \r\n there); NONE/CUSTOM have a null suffix.
            val processedValue = if (directlyApplyTemplate) scan.value
            else TemplateProcessor.processTemplate(
                scan.value,
                template,
                TemplateProcessor.TemplateMode.RFCOMM,
                scan.source,
                scan.timestamp,
                scannerId(),
                scan.barcodeType,
                preserveUnsupportedPlaceholders = preserveUnsupported,
                scanImageFileName = scan.imageName,
                regexGroups = scan.regexGroups,
            ).let { text -> extraKeys.suffix?.let { text + it } ?: text }

            controller.publishScan(
                rawValue = scan.value,
                processedValue = processedValue,
                format = scan.barcodeType,
                timestamp = scan.timestamp,
                source = scan.source,
                scannerId = scannerId(),
                regexGroups = scan.regexGroups,
                imageName = scan.imageName,
            )
            SendResult.Sent()
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            SendResult.Failed(e)
        }
    }
}
