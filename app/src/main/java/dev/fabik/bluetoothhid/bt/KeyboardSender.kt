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

    @Volatile
    private var cancelRequested = false

    /** Request cancellation of the currently running [sendProcessedString]. */
    fun requestCancel() {
        cancelRequested = true
    }

    private fun sendReport(report: ByteArray) {
        if (!hidDevice.sendReport(host, Descriptor.ID, report)) {
            Log.e(TAG, "Error sending keyboard report")
        }
    }

    /**
     * Sends [processedString] as HID keystrokes and returns the number of characters that
     * were actually typed on the host, or `null` if the send was cancelled before it finished.
     *
     * The returned count is the number of backspaces required to fully undo the send:
     * - For non-CUSTOM extra keys: equals [finalString].length — always exact, because each
     *   character in the string maps to exactly one visible character on the host (dead-key
     *   sequences take two keypresses but still produce one character = one backspace).
     * - For CUSTOM extra keys: equals the number of non-template characters plus one per
     *   text-producing HID template (e.g. {ENTER}, {TAB}). Pure control templates like
     *   {F1} or {LEFT} produce no visible characters and are not counted.
     */
    suspend fun sendProcessedString(
        processedString: String,
        sendDelay: Long,
        appendKey: ExtraKeys,
        locale: String,
    ): Int? {
        cancelRequested = false

        val finalString = appendKey.suffix?.let { "$processedString$it" } ?: processedString

        val (keys, charCount) = if (appendKey == ExtraKeys.CUSTOM) {
            // TemplateProcessor has already handled both {CODE} substitution and the expandCode
            // semantics: when expandCode=false, any HID template tokens embedded in the barcode
            // value (e.g. "{ENTER}") were escaped with Private Use Area sentinels so that
            // KeyTranslator types them as literal characters rather than executing them as
            // keypresses.  A single pass through translateStringWithTemplate is therefore correct
            // for both expandCode=true and expandCode=false; no two-pass mechanism is needed here.
            keyboardTranslator.translateStringWithTemplate(finalString, locale)
                .let { it to keyboardTranslator.countTypedChars(finalString, locale) }
        } else {
            // Non-CUSTOM: finalString is a plain string, length = exact undo char count
            keyboardTranslator.translateString(finalString, locale) to finalString.length
        }

        try {
            for (key in keys) {
                if (cancelRequested) return null
                Log.d(TAG, "sendProcessedString: $key")
                sendKey(key, sendDelay / 2)
                if (cancelRequested) return null
                delay(sendDelay / 2)
            }
        } finally {
            // Always release all keys at the end, even if cancelled
            sendKey(0, 0)
        }

        return charCount
    }

    /**
     * Send [count] backspace key presses to undo the last transmitted value.
     * @param count number of backspaces to send (one per character of the original value)
     */
    suspend fun sendBackspaces(count: Int) {
        Log.d(TAG, "sendBackspaces: $count")
        repeat(count) {
            sendKey(KeyTranslator.BACKSPACE_KEY, 10)
            delay(10)
        }
        // Release all keys after backspaces
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
