package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class BluetoothController(context: Context) {
    companion object {
        private const val TAG = "BluetoothController"
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }

    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    private var deviceListener: ((BluetoothHidDevice?, BluetoothDevice?) -> Unit)? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            Log.i(TAG, "onServiceConnected")

            hidDevice = proxy as BluetoothHidDevice

            hidDevice!!.registerApp(
                Descriptor.SDP_RECORD,
                null,
                Descriptor.QOS_OUT,
                Executors.newCachedThreadPool(),
                hidDeviceCallback
            )
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.i(TAG, "onServiceDisconnected")

            hidDevice = null
            deviceListener?.invoke(null, null)
        }
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)

            hostDevice = if (state == BluetoothProfile.STATE_CONNECTED) {
                device
            } else {
                null
            }

            deviceListener?.invoke(hidDevice, hostDevice)
        }
    }

    fun bluetoothEnabled(): Boolean = bluetoothAdapter.isEnabled

    fun register(context: Context, listener: ((BluetoothHidDevice?, BluetoothDevice?) -> Unit)?) {
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

    fun disconnect() {
        hostDevice?.let {
            hidDevice?.disconnect(it)
        }
    }

}