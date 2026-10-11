package dev.fabik.bluetoothhid.output.rfcomm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import dev.fabik.bluetoothhid.output.ChannelEvent
import dev.fabik.bluetoothhid.output.ChannelState
import dev.fabik.bluetoothhid.output.PeerChannel
import dev.fabik.bluetoothhid.output.ScanEvent
import dev.fabik.bluetoothhid.output.SendResult
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.ExtraKeys
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Output channel for the RFCOMM (SPP) transport. Wraps [RfcommController], which owns the
 * server socket / accept-loop logic, and only maps its state onto [ChannelState].
 *
 * State mapping: the controller only reports "server listening" (via callback), not the
 * client identity, so a served client is represented as [ChannelState.Connected] carrying
 * the expected (selected) device.
 */
@SuppressLint("MissingPermission")
class RfcommChannel(
    private val context: Context,
    private val controller: RfcommController,
    private val scope: CoroutineScope,
) : PeerChannel {

    companion object {
        private const val TAG = "RfcommChannel"
    }

    private val _state = MutableStateFlow<ChannelState>(ChannelState.Stopped)
    override val state: StateFlow<ChannelState> = _state.asStateFlow()

    // Reserved for future use (e.g. surfacing received RFCOMM data to the UI).
    private val _events = MutableSharedFlow<ChannelEvent>(0, 8, BufferOverflow.DROP_OLDEST)
    override val events: Flow<ChannelEvent> = _events.asSharedFlow()

    override val mode: ConnectionMode = ConnectionMode.RFCOMM

    /** Server is listening for a client (no client currently being served). */
    val isListening: StateFlow<Boolean> = _state
        .map { it is ChannelState.Ready || it is ChannelState.Waiting }
        .stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile
    private var running = false
    @Volatile
    private var expectedDevice: BluetoothDevice? = null

    private val lifecycle = Mutex()
    private var autoConnectJob: Job? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    init {
        controller.setListeningStateCallback { listening ->
            if (!running) {
                _state.value = ChannelState.Stopped
            } else if (listening) {
                _state.update { expectedDevice?.let(ChannelState::Waiting) ?: ChannelState.Ready }
            } else {
                _state.update { ChannelState.Connected(expectedDevice) }
            }
        }
    }

    override suspend fun start() = lifecycle.withLock {
        if (running) return@withLock
        running = true

        // Keep the auto-reconnect behavior in sync with the preference while the channel runs.
        autoConnectJob = scope.launch {
            context.getPreference(PreferenceStore.AUTO_CONNECT).collect { enabled ->
                controller.setAutoConnectEnabled(enabled)
            }
        }

        registerBluetoothStateReceiver()

        controller.connectRFCOMM()
        _state.value = expectedDevice?.let { ChannelState.Waiting(it) } ?: ChannelState.Ready
    }

    override suspend fun stop() = lifecycle.withLock {
        running = false

        autoConnectJob?.cancel()
        autoConnectJob = null

        bluetoothStateReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
            bluetoothStateReceiver = null
        }

        // Triggers the listening callback, which sees running=false and stops the state.
        controller.disconnectRFCOMM()
        expectedDevice = null
        _state.value = ChannelState.Stopped
    }

    override suspend fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connecting to $device")

        // Check if device needs pairing first
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "RFCOMM mode: Device not paired, initiating pairing")
            device.createBond()  // Triggers Android pairing dialog
        }

        expectedDevice = device
        controller.setExpectedDeviceAddress(device.address)
        // Ensure RFCOMM server is running (in case it was stopped by disconnect)
        controller.connectRFCOMM()
        _state.value = ChannelState.Waiting(device)
    }

    override suspend fun disconnect(): Boolean {
        expectedDevice = null
        controller.setExpectedDeviceAddress(null)
        controller.disconnectRFCOMM()
        _state.value = ChannelState.Ready
        return true
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

            val processedString = if (directlyApplyTemplate) scan.value
            else TemplateProcessor.processTemplate(
                scan.value,
                template,
                TemplateProcessor.TemplateMode.RFCOMM,
                scan.source,
                scan.timestamp,
                adapter.name,
                scan.barcodeType,
                preserveUnsupportedPlaceholders = preserveUnsupported,
                scanImageFileName = scan.imageName,
                regexGroups = scan.regexGroups,
            )

            controller.sendProcessedData(processedString)
            SendResult.Sent()
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            SendResult.Failed(e)
        }
    }

    // Restart the server when Bluetooth comes back (same behavior the controller-level
    // Bluetooth state monitor used to provide for RFCOMM mode).
    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiver != null) return
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Log.d(TAG, "Bluetooth turning OFF - disconnecting RFCOMM")
                        controller.disconnectRFCOMM()
                        _state.value = ChannelState.Ready
                    }

                    BluetoothAdapter.STATE_ON -> {
                        Log.d(TAG, "Bluetooth turned ON - restarting RFCOMM server")
                        controller.connectRFCOMM()
                    }
                }
            }
        }
        context.registerReceiver(
            bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }
}
