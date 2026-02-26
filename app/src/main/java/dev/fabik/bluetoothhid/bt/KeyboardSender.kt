package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.util.Log
import dev.fabik.bluetoothhid.utils.ExtraKeys
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

    private val report = ByteArray(3)

    private fun sendReport(report: ByteArray) {
        if (!hidDevice.sendReport(host, Descriptor.ID, report)) {
            Log.e(TAG, "Error sending keyboard report")
        }
    }

    suspend fun sendProcessedString(
        processedString: String,
        sendDelay: Long,
        appendKey: ExtraKeys,
        locale: String,
        expandCode: Boolean,
    ) {
        val finalString = when (appendKey) {
            ExtraKeys.ENTER -> "$processedString\n"
            ExtraKeys.TAB -> "$processedString\t"
            ExtraKeys.SPACE -> "$processedString "
            else -> processedString
        }

        val keys = when (appendKey) {
            ExtraKeys.CUSTOM -> {
                if (expandCode) {
                    // Complex expandCode mechanism - treat processed string as template for expansion
                    val expandedCode = keyboardTranslator.translateStringWithTemplate(finalString, locale)
                    keyboardTranslator.translateStringWithTemplate("", locale, expandedCode)
                } else {
                    // Simple template processing on the already processed string
                    keyboardTranslator.translateStringWithTemplate(finalString, locale)
                }
            }
            else -> keyboardTranslator.translateString(finalString, locale)
        }

        keys.forEach { key ->
            Log.d(TAG, "sendProcessedString: $key")
            sendKey(key, sendDelay / 2)
            delay(sendDelay / 2)
        }
        // Send final release key (just to be sure)
        sendKey(0, 0)
    }

    suspend fun sendKey(key: Key, releaseDelay: Long = 10) =
        sendKey(key.second.toByte(), key.first.toByte(), releaseDelay = releaseDelay)

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
