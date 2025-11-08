package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
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
import dev.fabik.bluetoothhid.utils.KeyboardLayout
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import dev.fabik.bluetoothhid.utils.getPreference
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

    private var deviceListener: MutableList<Listener> = mutableListOf()

    @Volatile
    private var hidDevice: BluetoothHidDevice? = null

    private var hostDevice: MutableStateFlow<BluetoothDevice?> = MutableStateFlow(null)
    val currentDevice = hostDevice.asStateFlow()

    private var latch: CountDownLatch = CountDownLatch(0)

    private var autoConnectEnabled: Boolean = false

    // Cache connection mode to avoid repeated preference calls
    @Volatile
    private var currentConnectionMode: ConnectionMode = ConnectionMode.HID

    var keyboardSender: KeyboardSender? = null
        private set

    private var _isSending: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private var _isCapsLockOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isCapsLockOn = _isCapsLockOn.asStateFlow()

    private var _isRFCOMMListening: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isRFCOMMListeningFlow = _isRFCOMMListening.asStateFlow()

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var modeObserverJob: Job? = null
    private var autoConnectObserverJob: Job? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected")

            hostDevice.update { null }
            hidDevice = proxy as? BluetoothHidDevice

            hidDevice?.registerApp(
                Descriptor.SDP_RECORD,
                null,
                Descriptor.QOS_OUT,
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
    fun startModeObserver() {
        if (modeObserverJob?.isActive == true) {
            Log.d(TAG, "Mode observer already running")
            return
        }

        Log.d(TAG, "Starting CONNECTION_MODE observer")
        modeObserverJob = controllerScope.launch {
            context.getPreference(PreferenceStore.CONNECTION_MODE)
                .distinctUntilChanged()
                .debounce(200)
                .collect { modeOrdinal ->
                    val mode = ConnectionMode.fromIndex(modeOrdinal)
                    Log.d(TAG, "Connection mode changed to: $mode")
                    currentConnectionMode = mode // Cache the mode
                    when (mode) {
                        ConnectionMode.RFCOMM -> {
                            Log.d(TAG, "Switching to RFCOMM mode")
                            rfcommController.connectRFCOMM()
                            // Note: HID remains registered but inactive in RFCOMM mode
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
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
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

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startAutoConnectObserver() {
        if (autoConnectObserverJob?.isActive == true) {
            Log.d(TAG, "AutoConnect observer already running")
            return
        }

        Log.d(TAG, "Starting AUTO_CONNECT observer")
        autoConnectObserverJob = controllerScope.launch {
            context.getPreference(PreferenceStore.AUTO_CONNECT)
                .distinctUntilChanged()
                .debounce(150)
                .collect { autoConnect ->
                    Log.d(TAG, "AutoConnect setting changed to: $autoConnect")
                    autoConnectEnabled = autoConnect

                    // Propagate to RFCOMM
                    rfcommController.setAutoConnectEnabled(autoConnect)
                }
        }
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
        startModeObserver()
        startAutoConnectObserver()
        startBluetoothStateMonitoring()

        // Setup RFCOMM listening state callback
        rfcommController.setListeningStateCallback { isListening ->
            _isRFCOMMListening.update { isListening }
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
        modeObserverJob?.cancel()
        modeObserverJob = null
        autoConnectObserverJob?.cancel()
        autoConnectObserverJob = null

        // Stop Bluetooth state monitoring
        bluetoothStateReceiver?.let {
            context.unregisterReceiver(it)
            bluetoothStateReceiver = null
        }

        // Disconnect RFCOMM
        rfcommController.disconnectRFCOMM()

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
        imageName: String? = null
    ) = with(context) {
        if (!_isSending.compareAndSet(expect = false, update = true)) {
            return@with
        }

        val sendDelay = getPreference(PreferenceStore.SEND_DELAY).first()
        val extraKeysOrdinal = if (!withExtraKeys) 0 else getPreference(PreferenceStore.EXTRA_KEYS).first()
        val extraKeys = ExtraKeys.fromIndex(extraKeysOrdinal)
        val layoutOrdinal = getPreference(PreferenceStore.KEYBOARD_LAYOUT).first()
        val layout = KeyboardLayout.fromIndex(layoutOrdinal)
        val template =
            if (!withExtraKeys) "" else getPreference(PreferenceStore.TEMPLATE_TEXT).first()
        val expand =
            if (!withExtraKeys) false else getPreference(PreferenceStore.EXPAND_CODE).first()

        // Get scanner ID
        val scannerID = getScannerID()

        // Get preserve unsupported placeholders preference (RFCOMM only)
        val preserveUnsupported = getPreference(PreferenceStore.PRESERVE_UNSUPPORTED_PLACEHOLDERS).first()

        // Check connection mode - RFCOMM or HID using cached value
        if (currentConnectionMode == ConnectionMode.RFCOMM) {
            // RFCOMM mode - process template for text output
            val processedString = TemplateProcessor.processTemplate(
                string,
                template,
                TemplateProcessor.TemplateMode.RFCOMM,
                from,
                scanTimestamp,
                scannerID,
                barcodeType,
                preserveUnsupported,  // Pass preference
                imageName
            )
            rfcommController.sendProcessedData(processedString)
        } else {
            // HID mode - process template for HID conversion
            val processedString = TemplateProcessor.processTemplate(
                string,
                template,
                TemplateProcessor.TemplateMode.HID,
                from,
                scanTimestamp,
                scannerID,
                barcodeType,
                false,  // HID always false - placeholders needed for KeyTranslator
                imageName
            )
            val locale = when (layout) {
                KeyboardLayout.US -> "us"
                KeyboardLayout.DE -> "de"
                KeyboardLayout.FR -> "fr"
                KeyboardLayout.GB -> "en"
                KeyboardLayout.ES -> "es"
                KeyboardLayout.IT -> "it"
                KeyboardLayout.TR -> "tr"
                KeyboardLayout.PL -> "pl"
                KeyboardLayout.CZ -> "cz"
            }
            keyboardSender?.sendProcessedString(
                processedString,
                sendDelay.toLong(),
                extraKeys.ordinal,
                locale,
                expand
            )
        }

        _isSending.update { false }
    }
}

fun BluetoothDevice.removeBond() {
    javaClass.getMethod("removeBond").invoke(this)
}