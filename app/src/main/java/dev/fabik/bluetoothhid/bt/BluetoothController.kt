package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    var keyboardSender: KeyboardSender? = null
        private set

    private var _isSending: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private var _isCapsLockOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isCapsLockOn = _isCapsLockOn.asStateFlow()

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected")

            // Initialize RFCOMM if needed
            MainScope().launch {
                val connectionMode = context.getPreference(PreferenceStore.CONNECTION_MODE).first()
                if (connectionMode == 1) {
                    rfcommController.connectRFCOMM()
                }
            }

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

            // Disconnect RFCOMM
            rfcommController.disconnectRFCOMM()

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

    suspend fun register(): Boolean =
        register(context.getPreference(PreferenceStore.AUTO_CONNECT).first())

    private fun register(autoConnect: Boolean): Boolean {
        autoConnectEnabled = autoConnect

        if (hidDevice != null) {
            unregister()
        }

        return bluetoothAdapter?.getProfileProxy(
            context,
            serviceListener,
            BluetoothProfile.HID_DEVICE
        ) ?: false
    }

    fun unregister() {
        disconnect()

        // Disconnect RFCOMM
        rfcommController.disconnectRFCOMM()

        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)

        hidDevice = null
        hostDevice.update { null }

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

        return hostDevice.value?.let {
            hidDevice?.disconnect(it)
        } ?: true
    }

    suspend fun sendString(string: String, withExtraKeys: Boolean = true) = with(context) {
        if (!_isSending.compareAndSet(false, true)) {
            return@with
        }

        val sendDelay = getPreference(PreferenceStore.SEND_DELAY).first()
        val extraKeys = if (!withExtraKeys) 0 else getPreference(PreferenceStore.EXTRA_KEYS).first()
        val layout = getPreference(PreferenceStore.KEYBOARD_LAYOUT).first()
        val template =
            if (!withExtraKeys) "" else getPreference(PreferenceStore.TEMPLATE_TEXT).first()
        val expand =
            if (!withExtraKeys) false else getPreference(PreferenceStore.EXPAND_CODE).first()
        val connectionMode = getPreference(PreferenceStore.CONNECTION_MODE).first()

        // Check connection mode - RFCOMM or HID
        if (connectionMode == 1) {
            // RFCOMM mode - process template for text output
            val processedString = TemplateProcessor.processTemplate(
                string,
                template,
                TemplateProcessor.TemplateMode.RFCOMM
            )
            rfcommController.sendProcessedData(processedString)
        } else {
            // HID mode - process template for HID conversion
            val processedString = TemplateProcessor.processTemplate(
                string,
                template,
                TemplateProcessor.TemplateMode.HID
            )
            val locale = when (layout) {
                1 -> "de"
                2 -> "fr"
                3 -> "en"
                4 -> "es"
                5 -> "it"
                6 -> "tr"
                7 -> "pl"
                8 -> "cz"
                else -> "us"
            }
            keyboardSender?.sendProcessedString(
                processedString,
                sendDelay.toLong(),
                extraKeys,
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