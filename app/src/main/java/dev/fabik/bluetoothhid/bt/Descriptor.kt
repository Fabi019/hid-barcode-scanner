package dev.fabik.bluetoothhid.bt

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings

object Descriptor {
    const val ID = 0x8

    private val KEYBOARD = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),                         // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),                         // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),                         // Collection (Application)
        0x85.toByte(), ID.toByte(),                           //     REPORT_ID (Keyboard)
        0x05.toByte(), 0x07.toByte(),                         //     Usage Page (Key Codes)
        0x19.toByte(), 0xe0.toByte(),                         //     Usage Minimum (224)
        0x29.toByte(), 0xe7.toByte(),                         //     Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(),                         //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),                         //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),                         //     Report Size (1)
        0x95.toByte(), 0x08.toByte(),                         //     Report Count (8)
        0x81.toByte(), 0x02.toByte(),                         //     Input (Data, Variable, Absolute)

        0x95.toByte(), 0x01.toByte(),                         //     Report Count (1)
        0x75.toByte(), 0x08.toByte(),                         //     Report Size (8)
        0x81.toByte(), 0x01.toByte(),                         //     Input (Constant) reserved byte(1)

        0x95.toByte(), 0x01.toByte(),                         //     Report Count (1)
        0x75.toByte(), 0x08.toByte(),                         //     Report Size (8)
        0x15.toByte(), 0x00.toByte(),                         //     Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),                         //     Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),                         //     Usage Page (Key codes)
        0x19.toByte(), 0x00.toByte(),                         //     Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),                         //     Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),                         //     Input (Data, Array) Key array(6 bytes)
        0xc0.toByte()                                         // End Collection (Application)
    )

    val SDP_RECORD = BluetoothHidDeviceAppSdpSettings(
        "Keyboard Input",
        "HID Device",
        "Android",
        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
        KEYBOARD
    )

    val QOS_OUT = BluetoothHidDeviceAppQosSettings(
        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
        800,
        9,
        0,
        11250,
        BluetoothHidDeviceAppQosSettings.MAX
    )
}
