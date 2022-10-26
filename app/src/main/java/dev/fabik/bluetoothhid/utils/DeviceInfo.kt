package dev.fabik.bluetoothhid.utils

import android.bluetooth.BluetoothClass

object DeviceInfo {
    fun deviceClassString(classMajor: Int): String = when (classMajor) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "AUDIO_VIDEO"
        BluetoothClass.Device.Major.COMPUTER -> "COMPUTER"
        BluetoothClass.Device.Major.HEALTH -> "HEALTH"
        BluetoothClass.Device.Major.MISC -> "MISC"
        BluetoothClass.Device.Major.IMAGING -> "IMAGING"
        BluetoothClass.Device.Major.NETWORKING -> "NETWORKING"
        BluetoothClass.Device.Major.PERIPHERAL -> "PERIPHERAL"
        BluetoothClass.Device.Major.PHONE -> "PHONE"
        BluetoothClass.Device.Major.TOY -> "TOY"
        BluetoothClass.Device.Major.WEARABLE -> "WEARABLE"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "UNCATEGORIZED"
        else -> "UNKNOWN"
    }

    fun deviceServiceInfo(bluetoothClass: BluetoothClass): List<String> {
        val services = mutableListOf<String>()
        if (bluetoothClass.hasService(BluetoothClass.Service.AUDIO))
            services.add("AUDIO")
        if (bluetoothClass.hasService(BluetoothClass.Service.CAPTURE))
            services.add("CAPTURE")
        if (bluetoothClass.hasService(BluetoothClass.Service.NETWORKING))
            services.add("NETWORKING")
        if (bluetoothClass.hasService(BluetoothClass.Service.INFORMATION))
            services.add("INFORMATION")
        if (bluetoothClass.hasService(BluetoothClass.Service.LE_AUDIO))
            services.add("LE_AUDIO")
        if (bluetoothClass.hasService(BluetoothClass.Service.LIMITED_DISCOVERABILITY))
            services.add("LIMITED_DISCOVERABILITY")
        if (bluetoothClass.hasService(BluetoothClass.Service.OBJECT_TRANSFER))
            services.add("OBJECT_TRANSFER")
        if (bluetoothClass.hasService(BluetoothClass.Service.POSITIONING))
            services.add("POSITIONING")
        if (bluetoothClass.hasService(BluetoothClass.Service.RENDER))
            services.add("RENDER")
        if (bluetoothClass.hasService(BluetoothClass.Service.TELEPHONY))
            services.add("TELEPHONY")
        return services
    }
}
