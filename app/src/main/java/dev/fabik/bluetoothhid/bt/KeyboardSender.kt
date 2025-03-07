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
            sendKey(key, sendDelay / 2)
            delay(sendDelay / 2)
        }
        // Send final release key (just to be sure)
        sendKey(0, 0)
    }

    suspend fun sendKey(key: Key, releaseDelay: Long = 10) =
        sendKey(key.second, key.first, releaseDelay = releaseDelay)

    private suspend fun sendKey(
        key: Byte,
        modifier: Byte = 0,
        releaseKey: Boolean = true,
        releaseDelay: Long = 10
    ) {
        report[0] = modifier
        report[2] = key

        sendReport(report)

        if (releaseKey) {
            report.fill(0)
            delay(releaseDelay)
            sendReport(report)
        }
    }
}
