package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

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

    private var deviceListener: MutableList<Listener> = mutableListOf()

    @Volatile
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? by mutableStateOf(null)

    private var latch: CountDownLatch = CountDownLatch(0)

    private var autoConnectEnabled: Boolean = false

    var keyboardSender: KeyboardSender? = null
        private set

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected")

            hostDevice = null
            hidDevice = proxy as? BluetoothHidDevice

            hidDevice?.registerApp(
                Descriptor.SDP_RECORD,
                null,
                Descriptor.QOS_OUT,
                Executors.newCachedThreadPool(),
                hidDeviceCallback
            )

            Toast.makeText(
                context,
                context.getString(R.string.bt_service_connected),
                Toast.LENGTH_SHORT
            ).show()

            latch.countDown()
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected")

            hidDevice = null
            hostDevice = null

            Toast.makeText(
                context,
                context.getString(R.string.bt_service_disconnected),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)

            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                hidDevice?.let {
                    keyboardSender = KeyboardSender(keyTranslator, it, device)
                }
            } else {
                hostDevice = null
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
    }

    val currentDevice: BluetoothDevice?
        get() = hostDevice

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

        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)

        hidDevice = null
        hostDevice = null

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

    fun connect(device: BluetoothDevice) {
        // Cancel discovery because it otherwise slows down the connection.
        bluetoothAdapter?.cancelDiscovery()

        hidDevice?.connect(device) ?: run {
            // Initialize latch to wait for service to be connected.
            latch = CountDownLatch(1)

            // Try to register proxy.
            if (!runBlocking { register() }) {
                Toast.makeText(
                    context,
                    context.getString(R.string.bt_proxy_error),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.proxy_waiting),
                    Toast.LENGTH_SHORT
                ).show()

                CoroutineScope(Dispatchers.IO).launch {
                    latch.await()
                    hidDevice?.connect(device)
                }
            }
        }
    }

    fun disconnect(): Boolean {
        return hostDevice?.let {
            hidDevice?.disconnect(it)
        } ?: false
    }

    suspend fun sendString(string: String) = with(context) {
        val sendDelay = getPreference(PreferenceStore.SEND_DELAY).first()
        val extraKeys = getPreference(PreferenceStore.EXTRA_KEYS).first()
        val layout = getPreference(PreferenceStore.KEYBOARD_LAYOUT).first()

        keyboardSender?.sendString(
            string, sendDelay.toLong(), extraKeys,
            when (layout) {
                1 -> "de"
                2 -> "fr"
                3 -> "en"
                4 -> "es"
                5 -> "it"
                else -> "us"
            }
        )
    }

}

fun BluetoothDevice.removeBond() {
    try {
        javaClass.getMethod("removeBond").invoke(this)
    } catch (e: Exception) {
        Log.e("BluetoothDevice", "Removing bond with $address has failed.", e)
    }
}
