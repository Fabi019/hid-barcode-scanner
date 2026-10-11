package dev.fabik.bluetoothhid.bt

import android.bluetooth.BluetoothDevice

fun BluetoothDevice.removeBond() {
    javaClass.getMethod("removeBond").invoke(this)
}
