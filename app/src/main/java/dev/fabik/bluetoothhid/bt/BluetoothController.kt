package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class BluetoothController(var context: Context) {
    companion object {
        private const val TAG = "BluetoothController"
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }

    var keyboardSender: KeyboardSender? = null

    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    private var deviceListener: ((BluetoothDevice?, Int) -> Unit)? = null

    private var autoConnectEnabled = false

    init {
        CoroutineScope(Dispatchers.IO).launch {
            context.getPreference(PrefKeys.AUTO_CONNECT).collect {
                autoConnectEnabled = it
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "onServiceConnected")

            hidDevice = proxy as? BluetoothHidDevice

            hidDevice?.registerApp(
                Descriptor.SDP_RECORD,
                null,
                Descriptor.QOS_OUT,
                Executors.newCachedThreadPool(),
                hidDeviceCallback
            )
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected")

            hidDevice = null
        }
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)

            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                hidDevice?.let { hid ->
                    keyboardSender = KeyboardSender(
                        context.getPreference(PrefKeys.EXTRA_KEYS),
                        context.getPreference(PrefKeys.SEND_DELAY),
                        hid,
                        device
                    )
                }
            } else {
                hostDevice = null
                keyboardSender = null
            }

            deviceListener?.invoke(device, state)
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

    fun bluetoothEnabled(): Boolean = bluetoothAdapter.isEnabled

    fun register(listener: ((BluetoothDevice?, Int) -> Unit)?) {
        deviceListener = listener
        bluetoothAdapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun unregister() {
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        deviceListener = null
    }

    fun pairedDevices(): Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

    fun scanDevices() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
    }

    fun connect(device: BluetoothDevice) {
        hidDevice?.connect(device)
    }

    fun disconnect(): Boolean {
        return hostDevice?.let {
            hidDevice?.disconnect(it)
        } ?: false
    }

}