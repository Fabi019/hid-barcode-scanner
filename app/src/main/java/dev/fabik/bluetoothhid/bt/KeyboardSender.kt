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
        private const val TAG = "KeyboardSender"
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
     * were actually typed on the host, or `null` if the send was cancelled before any visible
     * character was typed.
     *
     * The returned count is the number of backspaces required to undo what was typed:
     * - On full completion: total visible characters (letters, digits, Enter, Tab, Space, …).
     *   Note: {BKSP} is intentionally excluded — it removes a character rather than adding one,
     *   so counting it would send one extra backspace per {BKSP} and delete an unrelated char.
     * - On cancel mid-send: visible characters typed so far — enables partial undo via
     *   [sendBackspaces] when the send is interrupted.
     * - On cancel before any visible character: `null` (nothing to undo).
     *
     * Character counting is performed in-loop using information from [KeyTranslator]:
     * each translated key carries a flag indicating whether it completes a visible character
     * on the host (dead-key prefix keys are flagged `false`; control combinations and
     * non-text templates such as {F1} or {WAIT} are also flagged `false`).
     */
    suspend fun sendProcessedString(
        processedString: String,
        sendDelay: Long,
        appendKey: ExtraKeys,
        locale: String,
    ): Int? {
        cancelRequested = false

        val finalString = appendKey.suffix?.let { "$processedString$it" } ?: processedString

        // Always use translateStringWithTemplateDetailed, even for non-CUSTOM modes.
        //
        // Reason: TemplateProcessor may have escaped literal `{`/`}` characters in the barcode
        // value with Private Use Area sentinels (U+E001/U+E002) when expandCode=false.
        // translateStringDetailed does NOT know about those sentinels and would log "Unknown char"
        // for every brace in the barcode.  translateStringWithTemplateDetailed calls
        // restoreEscapedBraces() on every text segment, so the sentinels are decoded back to
        // `{`/`}` before keymap lookup — regardless of whether the template contained HID tokens.
        //
        // For non-CUSTOM modes, finalString contains no unresolved `{…}` HID template tokens
        // (TemplateProcessor already substituted them all), so the template-matching pass is a
        // cheap no-op and correctness is preserved.
        val keys: List<Pair<Key, Boolean>> =
            keyboardTranslator.translateStringWithTemplateDetailed(finalString, locale)

        var charCount = 0

        try {
            for ((key, completesChar) in keys) {
                if (cancelRequested) return charCount.takeIf { it > 0 }
                Log.d(TAG, "sendProcessedString: $key")
                sendKey(key, sendDelay / 2)
                // Increment immediately after sendKey so the count reflects what was actually
                // transmitted — BluetoothHidDevice.sendReport is fire-and-forget, so by the
                // time sendKey() returns the host has already received the report.
                if (completesChar) charCount++
                if (cancelRequested) return charCount.takeIf { it > 0 }
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
