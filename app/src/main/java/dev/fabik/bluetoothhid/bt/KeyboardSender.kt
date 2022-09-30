package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
open class KeyboardSender(
    private val hidDevice: BluetoothHidDevice,
    private val host: BluetoothDevice
) {
    companion object {
        const val TAG = "KeyboardSender"
    }

    private val keyboardReport = KeyboardReport()

    private val keyCharacterMap: KeyCharacterMap =
        KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

    protected open fun sendReport() {
        if (!hidDevice.sendReport(host, KeyboardReport.ID, keyboardReport.bytes)) {
            Log.e(TAG, "Report wasn't sent")
        }
    }

    protected open fun setModifiers(event: KeyEvent) {
        keyboardReport.leftShift = event.isShiftPressed
        keyboardReport.leftAlt = event.isAltPressed
        keyboardReport.leftControl = event.isCtrlPressed
        keyboardReport.leftGui = event.isMetaPressed
    }

    fun sendString(string: String) {
        CoroutineScope(Dispatchers.IO).launch {
            keyCharacterMap.getEvents(string.toCharArray()).forEach {
                if (it.action == KeyEvent.ACTION_DOWN) {
                    sendKeyEvent(it.keyCode, it, true)
                    delay(1)
                }
            }
        }
    }

    fun sendKeyEvent(keyCode: Int, event: KeyEvent?, releaseKey: Boolean = true): Boolean {
        val key = KeyboardReport.SCANCODE_TABLE[keyCode] ?: return false

        keyboardReport.key1 = key.toByte()

        event?.let {
            setModifiers(event)
        }

        sendReport()

        if (releaseKey) {
            keyboardReport.reset()
            sendReport()
        }

        return true
    }

}