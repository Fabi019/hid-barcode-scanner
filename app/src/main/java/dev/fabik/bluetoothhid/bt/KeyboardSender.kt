package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.util.Log
import android.view.KeyEvent

@SuppressLint("MissingPermission")
open class KeyboardSender(
    private val hidDevice: BluetoothHidDevice,
    private val host: BluetoothDevice
) {
    companion object {
        const val TAG = "KeyboardSender"
    }

    private val keyboardReport = KeyboardReport()

    protected open fun sendReport() {
        if (!hidDevice.sendReport(host, KeyboardReport.ID, keyboardReport.bytes)) {
            Log.e(TAG, "Report wasn't sent")
        }
    }

    protected open fun setModifiers(event: KeyEvent) {
        if (event.isShiftPressed) keyboardReport.leftShift = true
        if (event.isAltPressed) keyboardReport.leftAlt = true
        if (event.isCtrlPressed) keyboardReport.leftControl = true
        if (event.isMetaPressed) keyboardReport.leftGui = true
        if (event.keyCode == KeyEvent.KEYCODE_AT || event.keyCode == KeyEvent.KEYCODE_POUND || event.keyCode == KeyEvent.KEYCODE_STAR) {
            keyboardReport.leftShift = true
        }
    }

    fun sendKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        val byteKey = KeyboardReport.SCANCODE_TABLE[keyCode] ?: return false

        keyboardReport.key1 = byteKey.toByte()

        event?.let {
            setModifiers(event)
        }

        sendReport()

        keyboardReport.reset()

        sendReport()

        return true
    }

}