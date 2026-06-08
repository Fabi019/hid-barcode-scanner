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

    // TCP Controller integration
    private val tcpController = TcpController(context)

    private var deviceListener: MutableList<Listener> = mutableListOf()

    @Volatile
    private var hidDevice: BluetoothHidDevice? = null

    private var hostDevice: MutableStateFlow<BluetoothDevice?> = MutableStateFlow(null)
    val currentDevice = hostDevice.asStateFlow()

    private var latch: CountDownLatch = CountDownLatch(0)

    private var autoConnectEnabled: Boolean = false
    private var currentConnectionMode: ConnectionMode = ConnectionMode.HID
    private var currentTcpServerPort: String = ""
    private var currentTcpServerMaxClients: Int = -1
    private var currentTcpClientHost: String = ""
    private var currentTcpClientPort: String = ""
    private var currentIdleTimeoutMs: Int = -1
    private var currentConnectTimeoutMs: Int = -1
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

    private var _isTCPListening: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isTCPListeningFlow = _isTCPListening.asStateFlow()

    private var _isTCPConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isTCPConnectedFlow = _isTCPConnected.asStateFlow()

    private var _tcpStatus: MutableStateFlow<TcpStatusData> = MutableStateFlow(TcpStatusData())
    val tcpStatusFlow = _tcpStatus.asStateFlow()

    private val isTcpMode get() =
        currentConnectionMode == ConnectionMode.TCP_SERVER || currentConnectionMode == ConnectionMode.TCP_CLIENT

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
                PreferenceStore.TCP_SERVER_PORT,
                PreferenceStore.TCP_SERVER_MAX_CLIENTS,
                PreferenceStore.TCP_SERVER_CLIENT_IDLE_TIMEOUT_MS,
                PreferenceStore.TCP_CLIENT_HOST,
                PreferenceStore.TCP_CLIENT_PORT,
                PreferenceStore.TCP_CLIENT_CONNECT_TIMEOUT_MS
            ).distinctUntilChanged().debounce(200).collect {
                val mode = PreferenceStore.CONNECTION_MODE.extractEnum(it)
                val tcpServerPort = PreferenceStore.TCP_SERVER_PORT.extract(it)
                val tcpServerMaxClients = PreferenceStore.TCP_SERVER_MAX_CLIENTS.extract(it)
                val tcpClientHost = PreferenceStore.TCP_CLIENT_HOST.extract(it)
                val tcpClientPort = PreferenceStore.TCP_CLIENT_PORT.extract(it)
                val idleTimeoutMs = PreferenceStore.TCP_SERVER_CLIENT_IDLE_TIMEOUT_MS.extract(it)
                val connectTimeoutMs = PreferenceStore.TCP_CLIENT_CONNECT_TIMEOUT_MS.extract(it)
                if (currentConnectionMode != mode) {
                    when (mode) {
                        ConnectionMode.RFCOMM -> {
                            Log.d(TAG, "Switching to RFCOMM mode")
                            tcpController.stop()
                            rfcommController.connectRFCOMM()
                        }

                        ConnectionMode.TCP_SERVER -> {
                            Log.d(TAG, "Switching to TCP Server mode")
                            rfcommController.disconnectRFCOMM()
                            tcpController.startServer()
                        }

                        ConnectionMode.TCP_CLIENT -> {
                            Log.d(TAG, "Switching to TCP Client mode")
                            rfcommController.disconnectRFCOMM()
                            tcpController.startClient()
                        }

                        ConnectionMode.HID -> {
                            Log.d(TAG, "Switching to HID mode")
                            rfcommController.disconnectRFCOMM()
                            tcpController.stop()
                            registerHid()
                        }
                    }
                    currentConnectionMode = mode
                } else if (currentConnectionMode == ConnectionMode.TCP_SERVER
                    && (tcpServerPort != currentTcpServerPort
                        || tcpServerMaxClients != currentTcpServerMaxClients
                        || idleTimeoutMs != currentIdleTimeoutMs)) {
                    Log.d(TAG, "TCP Server settings changed — restarting")
                    tcpController.restartServer()
                } else if (currentConnectionMode == ConnectionMode.TCP_CLIENT
                    && (tcpClientHost != currentTcpClientHost || tcpClientPort != currentTcpClientPort
                        || connectTimeoutMs != currentConnectTimeoutMs)) {
                    Log.d(TAG, "TCP Client settings changed — restarting")
                    tcpController.restartClient()
                }
                currentTcpServerPort = tcpServerPort
                currentTcpServerMaxClients = tcpServerMaxClients
                currentTcpClientHost = tcpClientHost
                currentTcpClientPort = tcpClientPort
                currentIdleTimeoutMs = idleTimeoutMs
                currentConnectTimeoutMs = connectTimeoutMs
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
        // Initialize TCP tracking fields so the first observer emit doesn't trigger spurious restarts
        currentTcpServerPort = context.getPreference(PreferenceStore.TCP_SERVER_PORT).first()
        currentTcpServerMaxClients = context.getPreference(PreferenceStore.TCP_SERVER_MAX_CLIENTS).first()
        currentTcpClientHost = context.getPreference(PreferenceStore.TCP_CLIENT_HOST).first()
        currentTcpClientPort = context.getPreference(PreferenceStore.TCP_CLIENT_PORT).first()
        currentIdleTimeoutMs = context.getPreference(PreferenceStore.TCP_SERVER_CLIENT_IDLE_TIMEOUT_MS).first()
        currentConnectTimeoutMs = context.getPreference(PreferenceStore.TCP_CLIENT_CONNECT_TIMEOUT_MS).first()
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

        // Setup TCP listening state callback
        tcpController.setListeningStateCallback { isListening ->
            _isTCPListening.update { isListening }
        }

        // Setup TCP connected state callback
        tcpController.setConnectedStateCallback { connected ->
            _isTCPConnected.update { connected }
        }

        // Setup TCP addresses callback
        tcpController.setConnectedAddressesCallback { data ->
            _tcpStatus.update { data }
        }

        // Auto-start TCP modes since they don't require Bluetooth device selection
        when (currentConnectionMode) {
            ConnectionMode.TCP_SERVER -> tcpController.startServer()
            ConnectionMode.TCP_CLIENT -> tcpController.startClient()
            else -> {}
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

        // Stop TCP
        tcpController.stop()

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

        // TCP modes manage their own connections — no device selection needed
        if (isTcpMode) return

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

        // In TCP modes, stop the controller
        if (isTcpMode) {
            tcpController.stop()
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

            // Get preserve unsupported placeholders preference (RFCOMM only)
            val preserveUnsupported =
                PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS.extract(preferences)

            // Check connection mode - RFCOMM, TCP, or HID using cached value
            if (currentConnectionMode == ConnectionMode.RFCOMM || isTcpMode) {
                // RFCOMM and TCP share the same raw-text template path
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
                if (currentConnectionMode == ConnectionMode.RFCOMM) rfcommController.sendProcessedData(processedString)
                else tcpController.sendProcessedData(processedString)
            } else {
                // HID mode - process template for HID conversion.
                // expandCode controls whether HID template tokens inside the barcode value (e.g.
                // "{ENTER}" embedded in the scanned data) are expanded as real keypresses (true)
                // or typed as literal text (false, the default).  This is handled entirely inside
                // TemplateProcessor by escaping { } in the barcode value when expandCode=false;
                // KeyboardSender no longer needs to know about it.
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