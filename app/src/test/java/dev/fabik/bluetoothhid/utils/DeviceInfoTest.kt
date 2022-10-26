package dev.fabik.bluetoothhid.utils

import android.bluetooth.BluetoothClass
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DeviceInfoTest {

    @Test
    fun testDeviceClassString() {
        val computerClass = DeviceInfo.deviceClassString(BluetoothClass.Device.Major.COMPUTER)
        assertEquals("COMPUTER", computerClass)

        val uncategorizedClass =
            DeviceInfo.deviceClassString(BluetoothClass.Device.Major.UNCATEGORIZED)
        assertEquals("UNCATEGORIZED", uncategorizedClass)

        val unknownClass = DeviceInfo.deviceClassString(17)
        assertEquals("UNKNOWN", unknownClass)
    }

    @Test
    fun testDeviceServiceInfo() {
        val bluetoothClass = mock<BluetoothClass>()

        whenever(bluetoothClass.hasService(BluetoothClass.Service.AUDIO)).thenReturn(true)
        whenever(bluetoothClass.hasService(BluetoothClass.Service.CAPTURE)).thenReturn(false)
        whenever(bluetoothClass.hasService(BluetoothClass.Service.NETWORKING)).thenReturn(true)
        whenever(bluetoothClass.hasService(BluetoothClass.Service.INFORMATION)).thenReturn(false)
        whenever(bluetoothClass.hasService(BluetoothClass.Service.LE_AUDIO)).thenReturn(true)

        assertEquals(
            listOf("AUDIO", "NETWORKING", "LE_AUDIO"),
            DeviceInfo.deviceServiceInfo(bluetoothClass)
        )
    }

}
