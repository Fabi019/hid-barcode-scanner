package dev.fabik.bluetoothhid.ui.model

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class DevicesViewModel : ViewModel() {
    var foundDevices = mutableStateListOf<BluetoothDevice>()
    var pairedDevices = mutableStateListOf<BluetoothDevice>()

    var isScanning by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
}