package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.util.Log
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
open class KeyboardSender(
    private val keyboardTranslator: KeyTranslator,
    private val hidDevice: BluetoothHidDevice,
    private val host: BluetoothDevice
) {
    companion object {
        const val TAG = "KeyboardSender"
    }

    private val report = ByteArray(3) { 0 }

    private fun sendReport(report: ByteArray) {
        if (!hidDevice.sendReport(host, Descriptor.ID, report)) {
            Log.e(TAG, "Error sending keyboard report")
        }
    }

    suspend fun sendString(
        string: String,
        sendDelay: Long,
        appendKey: Int,
        locale: String,
        template: String,
        expandCode: Boolean,
    ) {
        when (appendKey) {
            1 -> keyboardTranslator.translateString("$string\n", locale)
            2 -> keyboardTranslator.translateString("$string\t", locale)
            3 -> keyboardTranslator.translateString("$string ", locale)
            4 -> {
                if (expandCode) {
                    val expandedCode =
                        keyboardTranslator.translateStringWithTemplate(string, locale, string)
                    keyboardTranslator.translateStringWithTemplate(
                        "",
                        locale,
                        template,
                        expandedCode
                    )
                } else {
                    keyboardTranslator.translateStringWithTemplate(string, locale, template)
                }
            }
            else -> keyboardTranslator.translateString(string, locale)
        }.forEach { key ->
            Log.d(TAG, "sendString: $key")
            sendKey(key)
            delay(sendDelay)
        }
    }

    suspend fun sendKey(key: Key) = sendKey(key.second, key.first)

    private suspend fun sendKey(key: Byte, modifier: Byte = 0, releaseKey: Boolean = true) {
        report[0] = modifier
        report[2] = key

        sendReport(report)

        if (releaseKey) {
            report.fill(0)
            delay(1)
            sendReport(report)
        }
    }
}
