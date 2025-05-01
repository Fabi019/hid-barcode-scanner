package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEController(context: Context) : IBluetoothController(context) {
    companion object {
        const val TAG = "BLEController"

        private val HID_SERVICE_UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val DEVICE_INFO_SERVICE_UUID =
            UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")

        private val HID_INFORMATION_UUID = UUID.fromString("00002A4A-0000-1000-8000-00805f9b34fb")
        private val REPORT_MAP_UUID = UUID.fromString("00002A4B-0000-1000-8000-00805f9b34fb")
        private val HID_CONTROL_POINT_UUID = UUID.fromString("00002A4C-0000-1000-8000-00805f9b34fb")
        private val REPORT_UUID = UUID.fromString("00002A4D-0000-1000-8000-00805f9b34fb")
        private val PROTOCOL_MODE_UUID = UUID.fromString("00002A4E-0000-1000-8000-00805f9b34fb")
        private val PNP_ID_UUID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    }

    private val keyTranslator: KeyTranslator = KeyTranslator(context)

    private var gattServer: BluetoothGattServer? = null

    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: $device, $status, $newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                currentDevice.update { device }
            } else {
                currentDevice.update { null }
                keyboardSender = null
            }

            deviceListener.forEach { it.invoke(device, newState) }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.d(TAG, "onCharacteristicReadRequest: $device, $requestId, $offset, $characteristic")
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic!!.value
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "onCharacteristicWriteRequest: $device, $characteristic, $value")
            characteristic?.value = value
            if (responseNeeded)
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "onDescriptorWriteRequest: $device, $descriptor, $value")
            descriptor?.value = value
            if (responseNeeded)
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            Log.d(TAG, "onDescriptorReadRequest: $device, $offset, $descriptor")
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                descriptor!!.value
            )
        }
    }

    override suspend fun register(): Boolean {
        val gatt = bluetoothManager.openGattServer(context, gattServerCallback)

        val hidService = createHidService()
        val basService = createBatteryService()
        val disSevice = createDeviceInfoService()

        gatt.addService(hidService)
        gatt.addService(basService)
        gatt.addService(disSevice)

        gattServer = gatt
        return true
    }

    private fun createHidService(): BluetoothGattService {
        val service =
            BluetoothGattService(HID_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // HID Information
        val hidInfo = BluetoothGattCharacteristic(
            HID_INFORMATION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        hidInfo.value = byteArrayOf(0x11, 0x01, 0x00, 0x03) // HID version 1.11, country code, flags
        service.addCharacteristic(hidInfo)

        // Report Map
        val reportMap = BluetoothGattCharacteristic(
            REPORT_MAP_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        reportMap.value = byteArrayOf(
            0x05, 0x01,       // Usage Page (Generic Desktop)
            0x09, 0x02,       // Usage (Mouse)
            0xA1.toByte(), 0x01,       // Collection (Application)
            0x09, 0x01,       //   Usage (Pointer)
            0xA1.toByte(), 0x00,       //   Collection (Physical)
            0x05, 0x09,       //     Usage Page (Buttons)
            0x19, 0x01,       //     Usage Minimum (1)
            0x29, 0x03,       //     Usage Maximum (3)
            0x15, 0x00,       //     Logical Minimum (0)
            0x25, 0x01,       //     Logical Maximum (1)
            0x95.toByte(), 0x03,       //     Report Count (3)
            0x75, 0x01,       //     Report Size (1)
            0x81.toByte(), 0x02,       //     Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01,       //     Report Count (1)
            0x75, 0x05,       //     Report Size (5)
            0x81.toByte(), 0x03,       //     Input (Constant)
            0xC0.toByte(),             //   End Collection
            0xC0.toByte()              // End Collection
        )
        service.addCharacteristic(reportMap)

        // HID Control Point
        val controlPoint = BluetoothGattCharacteristic(
            HID_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(controlPoint)

        // Protocol Mode
        val protocolMode = BluetoothGattCharacteristic(
            PROTOCOL_MODE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        protocolMode.value = byteArrayOf(0x01) // Report Protocol
        service.addCharacteristic(protocolMode)

        // Report Characteristic (Input Report)
        val report = BluetoothGattCharacteristic(
            REPORT_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        inputReportCharacteristic = report

        val reportReferenceDescriptor = BluetoothGattDescriptor(
            UUID.fromString("00002908-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ
        )
        reportReferenceDescriptor.value = byteArrayOf(0x08, 0x01) // Report ID 1, Input Report
        report.addDescriptor(reportReferenceDescriptor)

        val cccd = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        report.addDescriptor(cccd)

        service.addCharacteristic(report)

        return service
    }

    private fun createBatteryService(): BluetoothGattService {
        val service =
            BluetoothGattService(BATTERY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val batteryLevel = BluetoothGattCharacteristic(
            BATTERY_LEVEL_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        batteryLevel.value = byteArrayOf(90) // 90%

        val cccd = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        batteryLevel.addDescriptor(cccd)

        service.addCharacteristic(batteryLevel)
        return service
    }

    private fun createDeviceInfoService(): BluetoothGattService {
        val service = BluetoothGattService(
            DEVICE_INFO_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val pnpId = BluetoothGattCharacteristic(
            PNP_ID_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        pnpId.value = byteArrayOf(0x01, 0x5E, 0x04, 0x22, 0x11, 0x01, 0x00)
        service.addCharacteristic(pnpId)

        return service
    }

    override fun unregister() {
        gattServer?.close()
    }

    override fun scanDevices() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val advData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(HID_SERVICE_UUID))
            .addServiceUuid(ParcelUuid(DEVICE_INFO_SERVICE_UUID))
            .addServiceUuid(ParcelUuid(BATTERY_SERVICE_UUID))
            .build()

        bluetoothAdapter?.getBluetoothLeAdvertiser()
            ?.startAdvertising(settings, advData, object : AdvertiseCallback() {})

        super.scanDevices()
    }

    override suspend fun connect(device: BluetoothDevice) {
        gattServer?.connect(device, false)
        bluetoothAdapter?.getBluetoothLeAdvertiser()
            ?.stopAdvertising(object : AdvertiseCallback() {})
    }

    override fun disconnect(): Boolean {
        gattServer?.cancelConnection(currentDevice.value!!)
        return true
    }

    @SuppressLint("NewApi")
    override suspend fun sendString(string: String) {
        val report = ByteArray(3) { 0 }

        keyTranslator.translateString(string, "en").forEach {
            report[0] = it.first
            report[2] = it.second
            inputReportCharacteristic?.value = report
            gattServer?.notifyCharacteristicChanged(
                currentDevice.value!!,
                inputReportCharacteristic!!,
                false,
                report
            )

            delay(25)

            report.fill(0)
            inputReportCharacteristic?.value = report
            gattServer?.notifyCharacteristicChanged(
                currentDevice.value!!,
                inputReportCharacteristic!!,
                false,
                report
            )

            delay(25)
        }
    }
}