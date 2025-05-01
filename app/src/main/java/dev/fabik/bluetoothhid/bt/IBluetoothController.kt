package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("MissingPermission")
abstract class IBluetoothController(var context: Context) {
    protected val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    protected val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }

    var keyboardSender: KeyboardSender? = null
        protected set

    var currentDevice = MutableStateFlow<BluetoothDevice?>(null)
    var isSending = MutableStateFlow<Boolean>(false)
    var isCapsLockOn = MutableStateFlow<Boolean>(false)

    var deviceListener: MutableList<Listener> = mutableListOf()

    val bluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val pairedDevices: Set<BluetoothDevice>
        get() = bluetoothAdapter?.bondedDevices ?: emptySet()

    val isScanning: Boolean
        get() = bluetoothAdapter?.isDiscovering == true

    fun registerListener(listener: Listener): Listener {
        deviceListener.add(listener)
        return listener
    }

    fun unregisterListener(listener: Listener) = deviceListener.remove(listener)

    abstract suspend fun register(): Boolean
    abstract fun unregister()
    abstract suspend fun connect(device: BluetoothDevice)
    abstract fun disconnect(): Boolean
    abstract suspend fun sendString(string: String)

    open fun scanDevices() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
    }

    fun cancelScan() {
        bluetoothAdapter?.cancelDiscovery()
    }
}