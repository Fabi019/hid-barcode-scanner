package dev.fabik.bluetoothhid.output.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.bt.BluetoothService
import dev.fabik.bluetoothhid.bt.Descriptor
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.bt.KeyboardSender
import dev.fabik.bluetoothhid.output.Cancellable
import dev.fabik.bluetoothhid.output.ChannelEvent
import dev.fabik.bluetoothhid.output.ChannelState
import dev.fabik.bluetoothhid.output.PeerChannel
import dev.fabik.bluetoothhid.output.ScanEvent
import dev.fabik.bluetoothhid.output.SendResult
import dev.fabik.bluetoothhid.output.Undoable
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.ExtraKeys
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.getPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

@SuppressLint("MissingPermission")
class HidChannel(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val keyTranslator: KeyTranslator,
    private val scope: CoroutineScope,
    private val ensureServiceRunning: () -> Unit = {
        context.startForegroundService(Intent(context, BluetoothService::class.java))
    }
) : PeerChannel, Cancellable, Undoable, BluetoothHidDevice.Callback(),
    BluetoothProfile.ServiceListener {

    companion object {
        private const val TAG = "HidChannel"
        private const val PROXY_TIMEOUT_MS = 5_000L
        private const val CAPS_LOCK_MASK = 0x02
    }

    private val _state = MutableStateFlow<ChannelState>(ChannelState.Stopped)
    private val _events = MutableSharedFlow<ChannelEvent>(0, 8, BufferOverflow.DROP_OLDEST)

    override val mode: ConnectionMode = ConnectionMode.HID
    override val state: StateFlow<ChannelState> = _state.asStateFlow()
    override val events: Flow<ChannelEvent> = _events.asSharedFlow()

    private val _lastSentCharCount = MutableStateFlow<Int?>(null)
    val lastSentCharCount: StateFlow<Int?> = _lastSentCharCount.asStateFlow()

    override val canUndo: StateFlow<Boolean> =
        _lastSentCharCount.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _isCapsLockOn = MutableStateFlow(false)
    val isCapsLockOn: StateFlow<Boolean> = _isCapsLockOn.asStateFlow()

    @Volatile
    private var hidDevice: BluetoothHidDevice? = null
    @Volatile
    private var sender: KeyboardSender? = null
    val keyboardSender: KeyboardSender? get() = sender

    @Volatile
    private var cancelRequested = false
    @Volatile
    private var proxyWaiter: CompletableDeferred<BluetoothHidDevice>? = null

    @Volatile
    private var autoConnect = false

    private val lifecycle = Mutex()
    private val busy = AtomicBoolean(false)

    private val callbackExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    val bluetoothEnabled: Boolean
        get() = adapter.isEnabled

    val pairedDevices: Set<BluetoothDevice>
        get() = adapter.bondedDevices ?: emptySet()

    val isScanning: Boolean
        get() = adapter.isDiscovering

    override suspend fun start() = lifecycle.withLock {
        val current = _state.value
        val running = current is ChannelState.Ready ||
                current is ChannelState.Connecting ||
                current is ChannelState.Connected
        if (hidDevice != null && running) return

        autoConnect = context.getPreference(PreferenceStore.AUTO_CONNECT).first()

        val device = hidDevice ?: acquireProxy() ?: return
        registerApp(device)
    }

    override suspend fun stop() = lifecycle.withLock {
        sender?.requestCancel()

        val hid = hidDevice
        if (hid != null) {
            currentHost()?.let { runCatching { hid.disconnect(it) } }
        }

        _state.value = ChannelState.Stopped

        sender = null
        hidDevice = null
        _lastSentCharCount.value = null
        _isCapsLockOn.value = false

        if (hid != null) {
            runCatching { hid.unregisterApp() }
            runCatching { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        }
    }

    private suspend fun acquireProxy(): BluetoothHidDevice? {
        val waiter = CompletableDeferred<BluetoothHidDevice>()
        proxyWaiter = waiter
        try {
            if (!adapter.getProfileProxy(context, this, BluetoothProfile.HID_DEVICE)) {
                Log.e(TAG, "Failed to get profile proxy")
                _events.tryEmit(ChannelEvent.Notice(R.string.bt_proxy_error))
                return null
            }
            return withTimeoutOrNull(PROXY_TIMEOUT_MS) { waiter.await() }
        } finally {
            proxyWaiter = null
        }
    }

    private suspend fun registerApp(hid: BluetoothHidDevice) {
        runCatching { hid.unregisterApp() }

        try {
            hid.registerApp(Descriptor.SDP_RECORD, null, readQos(), callbackExecutor, this)
            _state.value = ChannelState.Ready
            _events.tryEmit(ChannelEvent.Notice(R.string.bt_service_connected))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register app", t)
            _state.value = ChannelState.Failed(t)
            _events.tryEmit(ChannelEvent.Notice(R.string.register_app_permission_error))
        }
    }

    private fun currentHost(): BluetoothDevice? = when (val s = _state.value) {
        is ChannelState.Connected -> s.device
        is ChannelState.Connecting -> s.device
        else -> null
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice) {
        adapter.cancelDiscovery()
        Log.d(TAG, "connecting to $device")

        if (hidDevice?.connect(device) == true) return

        Log.d(TAG, "unsuccessful connection to device, rebuilding proxy")

        try {
            ensureServiceRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }

        stop()
        start()

        if (_state.value is ChannelState.Failed) return

        if (hidDevice?.connect(device) != true) {
            Log.w(TAG, "HID connect() rejected for $device")
            _events.tryEmit(ChannelEvent.Notice(R.string.bt_proxy_error))
        }
    }

    override suspend fun disconnect(): Boolean {
        if (busy.get()) return false
        val host = currentHost() ?: return true
        return hidDevice?.disconnect(host) ?: true
    }

    override suspend fun send(scan: ScanEvent): SendResult {
        val keyboard = sender ?: return SendResult.NotConnected
        if (!busy.compareAndSet(false, true)) return SendResult.Busy

        return try {
            val s = readSettings()

            val extraKeys = if (scan.withExtraKeys) s.extraKeys else ExtraKeys.NONE
            val useTemplate = scan.withExtraKeys && extraKeys == ExtraKeys.CUSTOM

            val processed = if (s.directlyApplyTemplate) {
                scan.value
            } else {
                TemplateProcessor.processTemplate(
                    scan.value,
                    if (useTemplate) s.template else "",
                    TemplateProcessor.TemplateMode.HID,
                    scan.source,
                    scan.timestamp,
                    adapter.name,
                    scan.barcodeType,
                    preserveUnsupportedPlaceholders = false,
                    scanImageFileName = scan.imageName,
                    regexGroups = scan.regexGroups,
                    expandCode = useTemplate && s.expand,
                )
            }

            cancelRequested = false
            val typed = keyboard.sendProcessedString(
                processed, s.sendDelay, extraKeys, s.layoutLocale
            )

            typed?.let { _lastSentCharCount.value = it }

            if (cancelRequested) SendResult.Cancelled else SendResult.Sent(typed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            SendResult.Failed(e)
        } finally {
            busy.set(false)
        }
    }

    override fun cancelSend() {
        cancelRequested = true
        sender?.requestCancel()
    }

    override suspend fun undo() {
        val count = _lastSentCharCount.value ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            Log.d(TAG, "undo: $count backspaces")
            sender?.sendBackspaces(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "undo failed", e)
        } finally {
            _lastSentCharCount.value = null
            busy.set(false)
        }
    }

    private suspend fun readSettings(): HidSettings =
        context.getPreferences(
            PreferenceStore.SEND_DELAY,
            PreferenceStore.EXTRA_KEYS,
            PreferenceStore.KEYBOARD_LAYOUT,
            PreferenceStore.TEMPLATE_TEXT,
            PreferenceStore.EXPAND_CODE,
            PreferenceStore.DIRECT_APPLY_TEMPLATE,
        ).first().let {
            HidSettings(
                sendDelay = PreferenceStore.SEND_DELAY.extract(it).toLong(),
                extraKeys = PreferenceStore.EXTRA_KEYS.extractEnum(it),
                layoutLocale = PreferenceStore.KEYBOARD_LAYOUT.extractEnum(it).value,
                template = PreferenceStore.TEMPLATE_TEXT.extract(it),
                expand = PreferenceStore.EXPAND_CODE.extract(it),
                directlyApplyTemplate = PreferenceStore.DIRECT_APPLY_TEMPLATE.extract(it),
            )
        }

    private suspend fun readQos(): BluetoothHidDeviceAppQosSettings =
        context.getPreferences(
            PreferenceStore.QOS_SERVICE_TYPE,
            PreferenceStore.QOS_TOKEN_RATE,
            PreferenceStore.QOS_TOKEN_BUCKET_SIZE,
            PreferenceStore.QOS_PEAK_BANDWIDTH,
            PreferenceStore.QOS_LATENCY,
            PreferenceStore.QOS_DELAY_VARIATION,
        ).first().let {
            Log.d(TAG, "Using QoS: $it")
            BluetoothHidDeviceAppQosSettings(
                PreferenceStore.QOS_SERVICE_TYPE.extractEnum(it).value,
                PreferenceStore.QOS_TOKEN_RATE.extract(it),
                PreferenceStore.QOS_TOKEN_BUCKET_SIZE.extract(it),
                PreferenceStore.QOS_PEAK_BANDWIDTH.extract(it),
                PreferenceStore.QOS_LATENCY.extract(it),
                PreferenceStore.QOS_DELAY_VARIATION.extract(it),
            )
        }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        Log.d(TAG, "onServiceConnected")

        val hid = proxy as? BluetoothHidDevice ?: return
        hidDevice = hid

        val waiter = proxyWaiter
        if (waiter != null) {
            waiter.complete(hid)
        } else if (_state.value !is ChannelState.Stopped) {
            scope.launch { lifecycle.withLock { registerApp(hid) } }
        }
    }

    override fun onServiceDisconnected(profile: Int) {
        Log.d(TAG, "onServiceDisconnected")

        hidDevice = null
        sender = null

        _state.value = ChannelState.Stopped
        _events.tryEmit(ChannelEvent.Notice(R.string.bt_service_disconnected))
    }

    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        super.onConnectionStateChanged(device, state)
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                hidDevice?.let { sender = KeyboardSender(keyTranslator, it, device) }
                _state.value = ChannelState.Connected(device)
            }

            BluetoothProfile.STATE_CONNECTING -> _state.update {
                it as? ChannelState.Stopped ?: ChannelState.Connecting(device)
            }

            else -> {
                sender = null
                // Undo only makes sense for the host that received the text
                _lastSentCharCount.value = null
                _state.update {
                    if (it is ChannelState.Stopped || it is ChannelState.Failed) it
                    else ChannelState.Ready
                }
            }
        }
    }

    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        super.onAppStatusChanged(pluggedDevice, registered)
        if (!registered || !autoConnect) return

        val hid = hidDevice ?: return
        if (pluggedDevice != null) {
            Log.d(TAG, "onAppStatusChanged: connecting with $pluggedDevice")
            hid.connect(pluggedDevice)
        } else {
            hid.getDevicesMatchingConnectionStates(
                intArrayOf(
                    BluetoothProfile.STATE_CONNECTED,
                    BluetoothProfile.STATE_CONNECTING,
                    BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTING,
                )
            ).firstOrNull()?.let {
                Log.d(TAG, "onAppStatusChanged: connecting with $it")
                hid.connect(it)
            }
        }
    }

    override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
        super.onInterruptData(device, reportId, data)
        // Output report from the host: bit 0 = num lock, bit 1 = caps lock, bit 2 = scroll lock.
        data?.getOrNull(0)?.toInt()?.let { _isCapsLockOn.value = it and CAPS_LOCK_MASK != 0 }
        Log.d(TAG, "onInterruptData: $device, $reportId, ${data?.contentToString()}")
    }

    fun scanDevices() {
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()
    }

    fun cancelScan() {
        adapter.cancelDiscovery()
    }
}