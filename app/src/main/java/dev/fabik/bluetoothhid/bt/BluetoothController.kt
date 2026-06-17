package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.ExtraKeys
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

typealias Listener = (BluetoothDevice?, Int) -> Unit

@SuppressLint("MissingPermission")
class BluetoothController(var context: Context) {
    companion object {
        private const val TAG = "BluetoothController"
    }

    private val keyTranslator: KeyTranslator = KeyTranslator(context)

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }

    // RFCOMM Controller integration
    private val rfcommController = RfcommController(context, bluetoothAdapter!!)

    // External-output integration: forwards scans to companion/extension apps via broadcast
    private val externalController = ExternalController(context)

    // Last delivery status reported back by an extension (for optional UI feedback)
    val externalLastResultFlow = externalController.lastResultFlow

    // Per-plugin liveness (running + transport detail), driven by the heartbeat (for optional UI)
    val externalPluginHealthFlow = externalController.pluginHealthFlow

    // Whether external output is actually running (receiver registered + heartbeat live), so the
    // UI can reflect real runtime state instead of just the mode/preference.
    val externalActiveFlow = externalController.isActiveFlow

    /** Ping enabled external plugins now to refresh their reported status (used by the picker UI). */
    fun requestExternalStatus() = externalController.requestStatus()

    /** Kick enabled external plugins now (SET_ENABLED + ping) to start/revive their transport. */
    fun warmUpExternalPlugins() = externalController.warmUpEnabled()

    private var deviceListener: MutableList<Listener> = mutableListOf()

    @Volatile
    private var hidDevice: BluetoothHidDevice? = null

    private var hostDevice: MutableStateFlow<BluetoothDevice?> = MutableStateFlow(null)
    val currentDevice = hostDevice.asStateFlow()

    private var latch: CountDownLatch = CountDownLatch(0)

    private var autoConnectEnabled: Boolean = false
    private var currentConnectionMode: ConnectionMode = ConnectionMode.HID

    private val isExternalMode get() = currentConnectionMode == ConnectionMode.EXTERNAL
    private lateinit var preferences: Map<PreferenceStore.Preference<*>, *>

    var keyboardSender: KeyboardSender? = null
        private set

    private var _isSending: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    // Number of typed characters from the last completed HID send — used for precise undo.
    // Null means no completed send is available for undo (or last send was cancelled).
    private var _lastSentCharCount: MutableStateFlow<Int?> = MutableStateFlow(null)
    val lastSentCharCount = _lastSentCharCount.asStateFlow()

    private var _isCapsLockOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isCapsLockOn = _isCapsLockOn.asStateFlow()

    private var _isRFCOMMListening: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isRFCOMMListeningFlow = _isRFCOMMListening.asStateFlow()

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preferenceObserverJob: Job? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected")

            hostDevice.update { null }
            hidDevice = proxy as? BluetoothHidDevice

            val qos = runBlocking {
                context.getPreferences(
                    PreferenceStore.QOS_SERVICE_TYPE,
                    PreferenceStore.QOS_TOKEN_RATE,
                    PreferenceStore.QOS_TOKEN_BUCKET_SIZE,
                    PreferenceStore.QOS_PEAK_BANDWIDTH,
                    PreferenceStore.QOS_LATENCY,
                    PreferenceStore.QOS_DELAY_VARIATION
                ).first()
            }.let {
                Log.d(TAG, "Using QoS: $it")
                BluetoothHidDeviceAppQosSettings(
                    PreferenceStore.QOS_SERVICE_TYPE.extractEnum(it).value,
                    PreferenceStore.QOS_TOKEN_RATE.extract(it),
                    PreferenceStore.QOS_TOKEN_BUCKET_SIZE.extract(it),
                    PreferenceStore.QOS_PEAK_BANDWIDTH.extract(it),
                    PreferenceStore.QOS_LATENCY.extract(it),
                    PreferenceStore.QOS_DELAY_VARIATION.extract(it)
                )
            }

            // Clean up any stale HID app registration from a previous process instance.
            // This can happen on Android 12+ when the OS kills the service without calling
            // onDestroy, leaving the Bluetooth stack with an orphaned HID registration.
            // Toggling Bluetooth (which clears the stack) was the only workaround before this fix.
            hidDevice?.unregisterApp()

            hidDevice?.registerApp(
                Descriptor.SDP_RECORD,
                null,
                qos,
                Executors.newCachedThreadPool(),
                hidDeviceCallback
            )

            MainScope().launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.bt_service_connected),
                    Toast.LENGTH_SHORT
                ).show()
            }

            latch.countDown()
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected")

            hidDevice = null
            hostDevice.update { null }

            MainScope().launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.bt_service_disconnected),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)

            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice.update { device }
                hidDevice?.let {
                    keyboardSender = KeyboardSender(keyTranslator, it, device)
                }
            } else {
                hostDevice.update { null }
                keyboardSender = null
            }

            deviceListener.forEach { it.invoke(device, state) }
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)

            if (registered && autoConnectEnabled) {
                if (pluggedDevice != null) {
                    Log.d(TAG, "onAppStatusChanged: connecting with $pluggedDevice")
                    hidDevice?.connect(pluggedDevice)
                } else {
                    hidDevice?.getDevicesMatchingConnectionStates(
                        intArrayOf(
                            BluetoothProfile.STATE_CONNECTED,
                            BluetoothProfile.STATE_CONNECTING,
                            BluetoothProfile.STATE_DISCONNECTED,
                            BluetoothProfile.STATE_DISCONNECTING
                        )
                    )?.firstOrNull()?.let {
                        Log.d(TAG, "onAppStatusChanged: connecting with $it")
                        hidDevice?.connect(it)
                    }
                }
            }
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            super.onInterruptData(device, reportId, data)

            data?.getOrNull(0)?.toInt()?.let {
                _isCapsLockOn.update { _ -> it and 0x02 != 0 }
                // isNumLockOn = it and 0x01 != 0
                // isScrollLockOn = it and 0x04 != 0
            }

            Log.d(TAG, "onInterruptData: $device, $reportId, ${data?.contentToString()}")
        }

    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startPreferenceObserver() {
        if (preferenceObserverJob?.isActive == true) {
            Log.d(TAG, "Preference observer already running")
            return
        }

        Log.d(TAG, "Starting preference observer")
        preferenceObserverJob = controllerScope.launch {
            context.getPreferences(
                PreferenceStore.CONNECTION_MODE,
                PreferenceStore.AUTO_CONNECT,
                PreferenceStore.SEND_DELAY,
                PreferenceStore.EXTRA_KEYS,
                PreferenceStore.KEYBOARD_LAYOUT,
                PreferenceStore.TEMPLATE_TEXT,
                PreferenceStore.EXPAND_CODE,
                PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS,
                PreferenceStore.ENABLE_EXTERNAL_OUTPUT
            ).distinctUntilChanged().debounce(200).collect {
                val mode = PreferenceStore.CONNECTION_MODE.extractEnum(it)
                if (currentConnectionMode != mode) {
                    when (mode) {
                        ConnectionMode.RFCOMM -> {
                            Log.d(TAG, "Switching to RFCOMM mode")
                            rfcommController.connectRFCOMM()
                            // Note: HID remains registered but inactive in RFCOMM mode
                        }

                        ConnectionMode.EXTERNAL -> {
                            Log.d(TAG, "Switching to External mode")
                            rfcommController.disconnectRFCOMM()
                            // External has no Bluetooth peer — tear down any active HID device
                            // connection and clear the device state so the UI stops showing
                            // "connected to <device>".
                            hostDevice.value?.let { hidDevice?.disconnect(it) }
                            hostDevice.update { null }
                        }

                        ConnectionMode.HID -> {
                            Log.d(TAG, "Switching to HID mode - disconnecting RFCOMM")
                            rfcommController.disconnectRFCOMM()
                            // HID profile is already registered and will become active
                            // Note: HID registration is handled by the serviceListener in getProfileProxy
                            // Additional HID-specific setup could go in registerHid() if needed
                            registerHid()
                        }
                    }
                    currentConnectionMode = mode
                }

                // External output lifecycle — active in EXTERNAL mode OR when the parallel-output
                // toggle is on (HID/RFCOMM + "also send to external plugins"). Reacts to both the
                // mode and the toggle changing; start()/stop() are idempotent.
                val externalActive = mode == ConnectionMode.EXTERNAL ||
                    PreferenceStore.ENABLE_EXTERNAL_OUTPUT.extract(it)
                if (externalActive) externalController.start() else externalController.stop()

                autoConnectEnabled = PreferenceStore.AUTO_CONNECT.extract(it)
                rfcommController.setAutoConnectEnabled(autoConnectEnabled)

                preferences = it

                Log.d(TAG, "Preferences changed: $it")
            }
        }
    }

    fun startBluetoothStateMonitoring() {
        if (bluetoothStateReceiver != null) {
            Log.d(TAG, "Bluetooth state monitoring already started")
            return
        }

        Log.d(TAG, "Starting Bluetooth state monitoring")
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            Log.d(TAG, "Bluetooth turning OFF - force disconnect all connections")
                            disconnect()
                            rfcommController.disconnectRFCOMM()
                        }

                        BluetoothAdapter.STATE_ON -> {
                            Log.d(TAG, "Bluetooth turned ON - restarting services if needed")
                            if (currentConnectionMode == ConnectionMode.RFCOMM) {
                                rfcommController.connectRFCOMM()
                            }
                        }

                        BluetoothAdapter.STATE_OFF -> {
                            Log.d(TAG, "Bluetooth turned OFF")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
    }

    val bluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled ?: false

    val pairedDevices: Set<BluetoothDevice>
        get() = bluetoothAdapter?.bondedDevices ?: emptySet()

    val isScanning: Boolean
        get() = bluetoothAdapter?.isDiscovering ?: false

    fun registerListener(listener: Listener): Listener {
        deviceListener.add(listener)
        return listener
    }

    fun unregisterListener(listener: Listener) = deviceListener.remove(listener)

    /**
     * Get scanner ID from Bluetooth adapter
     * @return scanner ID
     */
    fun getScannerID(): String? {
        return bluetoothAdapter?.name
    }

    suspend fun register(): Boolean {
        val autoConnect = context.getPreference(PreferenceStore.AUTO_CONNECT).first()
        // Initialize cached connection mode
        val modeOrdinal = context.getPreference(PreferenceStore.CONNECTION_MODE).first()
        currentConnectionMode = ConnectionMode.fromIndex(modeOrdinal)
        return register(autoConnect)
    }

    private fun register(autoConnect: Boolean): Boolean {
        autoConnectEnabled = autoConnect

        if (hidDevice != null) {
            unregister()
        }

        // Start observers when registering
        startPreferenceObserver()
        startBluetoothStateMonitoring()

        // Setup RFCOMM listening state callback
        rfcommController.setListeningStateCallback { isListening ->
            _isRFCOMMListening.update { isListening }
        }

        // Auto-start External output since it doesn't require Bluetooth device selection
        if (currentConnectionMode == ConnectionMode.EXTERNAL) {
            externalController.start()
        }

        return bluetoothAdapter?.getProfileProxy(
            context,
            serviceListener,
            BluetoothProfile.HID_DEVICE
        ) ?: false
    }

    private fun registerHid() {
        Log.d(TAG, "Registering HID profile")
        // HID registration is handled by the serviceListener in getProfileProxy
        // Additional HID-specific setup could go here if needed
    }

    private fun unregisterHid() {
        Log.d(TAG, "Unregistering HID profile")
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        hostDevice.update { null }
    }

    fun unregister() {
        disconnect()

        // Stop observing connection mode and auto-connect changes
        preferenceObserverJob?.cancel()
        preferenceObserverJob = null

        // Stop Bluetooth state monitoring
        bluetoothStateReceiver?.let {
            context.unregisterReceiver(it)
            bluetoothStateReceiver = null
        }

        // Disconnect RFCOMM
        rfcommController.disconnectRFCOMM()

        // In External mode there is no device — just stop forwarding
        if (isExternalMode || PreferenceStore.ENABLE_EXTERNAL_OUTPUT.extract(preferences)) {
            externalController.stop()
        }

        // Unregister HID profile
        unregisterHid()

        // Notify listeners that proxy is disconnected.
        deviceListener.forEach { it.invoke(null, -1) }
    }

    fun scanDevices() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
    }

    fun cancelScan() {
        bluetoothAdapter?.cancelDiscovery()
    }

    suspend fun connect(device: BluetoothDevice) {
        // Cancel discovery because it otherwise slows down the connection.
        cancelScan()

        Log.d(TAG, "connecting to $device")

        // Check connection mode first using cached value
        if (currentConnectionMode == ConnectionMode.RFCOMM) {
            // Check if device needs pairing first
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                Log.d(TAG, "RFCOMM mode: Device not paired, initiating pairing")
                device.createBond()  // Triggers Android pairing dialog
            }

            // RFCOMM mode - set expected device and update currentDevice for UI
            rfcommController.setExpectedDeviceAddress(device.address)
            hostDevice.update { device }
            // Ensure RFCOMM server is running (in case it was stopped by disconnect)
            rfcommController.connectRFCOMM()
            Log.d(TAG, "RFCOMM mode: Set expected device to $device")
            return
        }

        // HID mode - proceed with normal HID connection logic
        val success = hidDevice?.connect(device) ?: false

        if (!success) {
            Log.d(TAG, "unsuccessful connection to device")

            // Try to start service (doesn't matter if it already runs)
            MainScope().launch {
                // Catch ForegroundServiceStartNotAllowedException when app is in background
                runCatching {
                    context.startForegroundService(
                        Intent(context, BluetoothService::class.java)
                    )
                }.onFailure {
                    Log.e("BTService", "Failed to start service", it)
                }
            }

            // Initialize latch to wait for service to be connected.
            latch = CountDownLatch(1)

            // Try to register proxy.
            if (register() && latch.await(5000, TimeUnit.MILLISECONDS)) {
                // Retry connection again
                hidDevice?.connect(device)
                return
            }

            MainScope().launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.bt_proxy_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Cancel the currently running send operation (HID mode only).
     * The keyboard sender will stop after the current key and send a release report (0,0)
     * to avoid stuck keys on the host.
     *
     * Not applicable to RFCOMM mode: in RFCOMM the value is sent as a raw string directly
     * into the stream in one shot, so there is nothing to interrupt by the time the UI reacts.
     */
    fun cancelSending() {
        if (currentConnectionMode != ConnectionMode.HID) return
        Log.d(TAG, "cancelSending")
        keyboardSender?.requestCancel()
    }

    /**
     * Undo the last sent value by transmitting one backspace per character (HID mode only).
     * Clears [lastSentString] afterwards so double-undo is not possible.
     *
     * Not applicable to RFCOMM mode: backspace has no defined meaning in a raw byte stream —
     * the receiving application would need to interpret it, which is not guaranteed.
     */
    suspend fun undoLastSent() {
        if (currentConnectionMode != ConnectionMode.HID) return
        val count = _lastSentCharCount.value ?: return
        if (!_isSending.compareAndSet(expect = false, update = true)) return
        try {
            Log.d(TAG, "undoLastSent: $count backspaces")
            keyboardSender?.sendBackspaces(count)
        } finally {
            // Clear the count and release the lock regardless of whether sendBackspaces throws.
            // Clearing in finally prevents a double-undo if the BT send fails mid-way.
            _lastSentCharCount.update { null }
            _isSending.update { false }
        }
    }

    fun disconnect(): Boolean {
        if (_isSending.value) {
            return false
        }

        // In RFCOMM mode, disconnect server and clear currentDevice
        if (currentConnectionMode == ConnectionMode.RFCOMM) {
            rfcommController.disconnectRFCOMM()
            hostDevice.update { null }
            return true
        }

        // In HID mode, disconnect the device
        return hostDevice.value?.let {
            hidDevice?.disconnect(it)
        } ?: true
    }

    suspend fun sendString(
        string: String,
        withExtraKeys: Boolean = true,
        from: String = "SCAN",
        scanTimestamp: Long? = null,
        barcodeType: String? = null,
        imageName: String? = null,
        regexGroups: List<String> = emptyList()
    ) = with(context) {
        if (!_isSending.compareAndSet(expect = false, update = true)) {
            return@with
        }

        try {
            val sendDelay = PreferenceStore.SEND_DELAY.extract(preferences)
            val extraKeys =
                if (!withExtraKeys) ExtraKeys.NONE
                else PreferenceStore.EXTRA_KEYS.extractEnum(preferences)
            val layout = PreferenceStore.KEYBOARD_LAYOUT.extractEnum(preferences)
            val template =
                if (!withExtraKeys || extraKeys != ExtraKeys.CUSTOM) ""
                else PreferenceStore.TEMPLATE_TEXT.extract(preferences)
            val expand =
                if (!withExtraKeys || extraKeys != ExtraKeys.CUSTOM) false
                else PreferenceStore.EXPAND_CODE.extract(preferences)

            // Get scanner ID
            val scannerID = getScannerID()

            // Get preserve unsupported placeholders preference (RFCOMM and External raw-text path)
            val preserveUnsupported =
                PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS.extract(preferences)

            // External output: active in EXTERNAL mode, or as a parallel "also send to external"
            // toggle in HID/RFCOMM mode. Uses the raw-text (RFCOMM) template path.
            val externalOutputEnabled = isExternalMode ||
                PreferenceStore.ENABLE_EXTERNAL_OUTPUT.extract(preferences)
            if (externalOutputEnabled) {
                val externalText = TemplateProcessor.processTemplate(
                    string,
                    template,
                    TemplateProcessor.TemplateMode.RFCOMM,
                    from,
                    scanTimestamp,
                    scannerID,
                    barcodeType,
                    preserveUnsupported,
                    imageName,
                    regexGroups
                    // External receives the final text exactly like HID does: the simple extra-key
                    // suffixes (ENTER/TAB/SPACE) apply here too — they double as the line
                    // terminator for plugins, which forward the text verbatim. CUSTOM keeps using
                    // the template ({ENTER} → \r\n there); NONE/CUSTOM have a null suffix.
                ).let { text -> extraKeys.suffix?.let { text + it } ?: text }
                externalController.publishScan(
                    rawValue = string,
                    processedValue = externalText,
                    format = barcodeType,
                    timestamp = scanTimestamp ?: System.currentTimeMillis(),
                    source = from,
                    scannerId = scannerID,
                    regexGroups = regexGroups,
                    imageName = imageName,
                )
            }

            // Bluetooth output, by connection mode (EXTERNAL has no Bluetooth peer)
            when (currentConnectionMode) {
                ConnectionMode.RFCOMM -> {
                    val processedString = TemplateProcessor.processTemplate(
                        string,
                        template,
                        TemplateProcessor.TemplateMode.RFCOMM,
                        from,
                        scanTimestamp,
                        scannerID,
                        barcodeType,
                        preserveUnsupported,
                        imageName,
                        regexGroups
                    )
                    rfcommController.sendProcessedData(processedString)
                }

                ConnectionMode.HID -> {
                    // expandCode controls whether HID template tokens inside the barcode value
                    // (e.g. "{ENTER}") are expanded as real keypresses (true) or typed as literal
                    // text (false, default). Handled inside TemplateProcessor by escaping { } when
                    // expandCode=false; KeyboardSender no longer needs to know about it.
                    val processedString = TemplateProcessor.processTemplate(
                        string,
                        template,
                        TemplateProcessor.TemplateMode.HID,
                        from,
                        scanTimestamp,
                        scannerID,
                        barcodeType,
                        preserveUnsupportedPlaceholders = false,  // HID: pass all placeholders to KeyTranslator
                        scanImageFileName = imageName,
                        regexGroups = regexGroups,
                        expandCode = expand
                    )
                    // Store the precise typed-character count for undo (null = cancelled, not stored)
                    keyboardSender?.sendProcessedString(
                        processedString,
                        sendDelay.toLong(),
                        extraKeys,
                        layout.value
                    )?.let { charCount -> _lastSentCharCount.update { charCount } }
                }

                ConnectionMode.EXTERNAL -> {
                    // No Bluetooth output — the scan was already published to plugins above.
                }
            }
        } finally {
            // Always release the sending lock — even if processTemplate or sendProcessedString
            // throws (e.g. SecurityException from BluetoothHidDevice.sendReport).
            // Without this, _isSending stays true and the app is permanently blocked.
            _isSending.update { false }
        }
    }
}

fun BluetoothDevice.removeBond() {
    javaClass.getMethod("removeBond").invoke(this)
}