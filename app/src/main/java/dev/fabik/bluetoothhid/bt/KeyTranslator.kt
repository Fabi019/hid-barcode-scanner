package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import dev.fabik.bluetoothhid.utils.TemplateProcessor
import java.util.Collections

// Represents a key with its modifier and hid scan code
typealias Key = Pair<UByte, UByte>

// Represents a keymap with a map of chars and their key codes
// Each character has an optional second key to support dead keys
typealias Keymap = Map<Char, Pair<Key, Key?>>

class KeyTranslator(context: Context) {
    companion object {
        private const val TAG = "KeyTranslator"

        /**
         * Private Use Area sentinels written by [TemplateProcessor.escapeBracesForHID] when
         * `expandCode = false`.  They mark literal `{` / `}` characters that originated from
         * the scanned barcode value and must be decoded back to braces before keymap lookup,
         * so that embedded HID template tokens are typed as text rather than executed.
         *
         * Must stay in sync with [TemplateProcessor.ESCAPED_OPEN_BRACE] / [TemplateProcessor.ESCAPED_CLOSE_BRACE].
         */
        private const val ESCAPED_OPEN_BRACE = TemplateProcessor.ESCAPED_OPEN_BRACE
        private const val ESCAPED_CLOSE_BRACE = TemplateProcessor.ESCAPED_CLOSE_BRACE

        const val LCTRL: UByte = 0x01u
        const val LSHIFT: UByte = 0x02u
        const val LALT: UByte = 0x04u
        private const val LMETA: UByte = 0x08u
//        private const val RCTRL: UByte = 0x10
//        private const val RSHIFT: UByte = 0x20
//        private const val RALT: UByte = 0x40
//        private const val RMETA: UByte = 0x80

        private val SPACE = ' ' to (Key(0u, 0x2Cu) to null)
        private val TAB = '\t' to (Key(0u, 0x2Bu) to null)
        private val RETURN = '\n' to (Key(0u, 0x28u) to null)

        val CAPS_LOCK_KEY = Key(0u, 0x39u)
        val BACKSPACE_KEY = Key(0u, 0x2Au)

        /** Matches HID template tokens such as `{ENTER}`, `{+F1}`, `{WAIT:500}`. */
        private val HID_TEMPLATE_REGEX = Regex("\\{([+^#@]*[\\w:]+)\\}")

        private const val CUSTOM_KEYMAP_FILE = "custom.layout"
        private var customKeyMapLoaded = false
        var CUSTOM_KEYMAP = mutableMapOf<Char, Pair<Key, Key?>>()

        // Load custom user-defined keys from filesystem
        fun loadCustomKeyMap(context: Context) {
            // Only load once
            if (customKeyMapLoaded) {
                return
            }
            customKeyMapLoaded = true

            runCatching {
                val file = context.filesDir.resolve(CUSTOM_KEYMAP_FILE)

                if (!file.exists()) {
                    Log.d(TAG, "No custom keymap exists: $file")
                    return
                }

                Log.d(TAG, "Loading from $file")

                CUSTOM_KEYMAP = loadKeymap(file.readLines()).toMutableMap()
            }.onFailure {
                Log.e(TAG, "Error loading custom keymap:", it)
            }
        }

        // Save user-defined keys to filesystem
        fun saveCustomKeyMap(context: Context) {
            runCatching {
                val file = context.filesDir.resolve(CUSTOM_KEYMAP_FILE)

                // Cleanup file
                if (CUSTOM_KEYMAP.isEmpty()) {
                    context.deleteFile(file.name)
                    return
                }

                file.bufferedWriter().use {
                    keymapToString(CUSTOM_KEYMAP).forEach { l ->
                        it.write(l)
                        it.newLine()
                    }
                }
            }.onFailure {
                Log.e("CustomKeys", "Error saving custom keymap", it)
            }
        }

        fun keymapToString(keymap: Keymap): List<String> {
            return keymap.map { (k, v) ->
                "$k ${v.first.second.toString(16)} ${v.first.first.toString(16)}" + (v.second?.let {
                    " ${it.second.toString(16)} ${it.first.toString(16)}"
                } ?: "")
            }
        }

        fun loadKeymap(lines: List<String>): Keymap {
            val keymap = mutableMapOf<Char, Pair<Key, Key?>>()

            lines.forEach {
                if (it.startsWith("##") || it.isBlank())
                    return@forEach

                runCatching {
                    val split = it.split(" ")
                    val first = Key(split[2].toUByte(16), split[1].toUByte(16))
                    val second =
                        if (split.size >= 5) Key(split[4].toUByte(16), split[3].toUByte(16))
                        else null
                    keymap[split[0].first()] = first to second
                }.onFailure { e ->
                    Log.e(TAG, "Failed to parse keymap line: $it", e)
                }
            }

            return keymap
        }
    }

    private val assetManager: AssetManager = context.assets

    private val baseMap: Keymap
    private val keyMaps: MutableMap<String, Keymap> = mutableMapOf()

    private val staticTemplates = mutableMapOf<String, Key>()
    private val dynamicTemplates = mutableMapOf<String, (String, String) -> List<Key>>()

    init {
        assetManager.list("keymaps")?.forEach {
            val fileName = "keymaps/$it"

            val lines = runCatching {
                assetManager.open(fileName).bufferedReader().readLines()
            }.onFailure {
                Log.e(TAG, "Failed to load keymap: $fileName", it)
            }.getOrNull() ?: return@forEach

            keyMaps[it.removeSuffix(".layout")] = loadKeymap(lines)
        }

        baseMap = (keyMaps.remove("base") ?: run {
            Log.e(TAG, "No base keymap found?")
            emptyMap()
        }) + mutableMapOf(SPACE, TAB, RETURN)

        loadCustomKeyMap(context)

        staticTemplates["F1"] = Key(0u, 0x3Au)
        staticTemplates["F2"] = Key(0u, 0x3Bu)
        staticTemplates["F3"] = Key(0u, 0x3Cu)
        staticTemplates["F4"] = Key(0u, 0x3Du)
        staticTemplates["F5"] = Key(0u, 0x3Eu)
        staticTemplates["F6"] = Key(0u, 0x3Fu)
        staticTemplates["F7"] = Key(0u, 0x40u)
        staticTemplates["F8"] = Key(0u, 0x41u)
        staticTemplates["F9"] = Key(0u, 0x42u)
        staticTemplates["F10"] = Key(0u, 0x43u)
        staticTemplates["F11"] = Key(0u, 0x44u)
        staticTemplates["F12"] = Key(0u, 0x45u)
        staticTemplates["ENTER"] = Key(0u, 0x28u)
        staticTemplates["ESC"] = Key(0u, 0x29u)
        staticTemplates["BKSP"] = Key(0u, 0x2Au)
        staticTemplates["TAB"] = Key(0u, 0x2Bu)
        staticTemplates["RIGHT"] = Key(0u, 0x4Fu)
        staticTemplates["LEFT"] = Key(0u, 0x50u)
        staticTemplates["DOWN"] = Key(0u, 0x51u)
        staticTemplates["UP"] = Key(0u, 0x52u)

//     winlin97:  added unused function keys for special usage
//                F1-F12 are binded in many Windows apps - also in Explorer
//                Now You can write an external app that can recognize this keys
//                as a triggers (StartKey -> Barcode -> StopKey),
//                without any unexpected interactions with the system.
        staticTemplates["F13"] = Key(0u, 0x68u)
        staticTemplates["F14"] = Key(0u, 0x69u)
        staticTemplates["F15"] = Key(0u, 0x6au)
        staticTemplates["F16"] = Key(0u, 0x6bu)
        staticTemplates["F17"] = Key(0u, 0x6cu)
        staticTemplates["F18"] = Key(0u, 0x6du)
        staticTemplates["F19"] = Key(0u, 0x6eu)
        staticTemplates["F20"] = Key(0u, 0x6fu)
        staticTemplates["F21"] = Key(0u, 0x70u)
        staticTemplates["F22"] = Key(0u, 0x71u)
        staticTemplates["F23"] = Key(0u, 0x72u)
        staticTemplates["F24"] = Key(0u, 0x73u)

        // Note: DATE and TIME templates are now handled by TemplateProcessor

        dynamicTemplates["WAIT"] = { locale, args ->
            Collections.nCopies<Key>(args.toIntOrNull() ?: 1, Key(0u, 0u))
        }
    }

    /**
     * Restores Private Use Area brace sentinels introduced by [TemplateProcessor.escapeBracesForHID]
     * (when `expandCode = false`) back to their original `{` / `}` characters.
     *
     * This is called on the plain-text segments *between* HID template matches inside
     * [translateStringWithTemplateDetailed], so that literal braces originating from barcode
     * data are typed as real characters rather than remaining as unrecognised codepoints.
     */
    private fun restoreEscapedBraces(text: String): String =
        text.replace(ESCAPED_OPEN_BRACE, '{').replace(ESCAPED_CLOSE_BRACE, '}')

    /**
     * Translates [processedString] — which may contain HID template tokens such as `{ENTER}`,
     * `{F1}`, `{WAIT:ms}`, etc. — into a list of HID key events.  Each event carries a flag
     * indicating whether it completes a visible character on the host, used by
     * [dev.fabik.bluetoothhid.bt.KeyboardSender] to track the exact typed-character count
     * in-loop (including after a partial cancel).
     *
     * Basic templates (`{CODE}`, `{DATE}`, `{TIME}`, …) must be substituted by
     * [dev.fabik.bluetoothhid.utils.TemplateProcessor] before this method is called.
     */
    fun translateStringWithTemplateDetailed(
        processedString: String,
        locale: String,
    ): List<Pair<Key, Boolean>> {
        val result = mutableListOf<Pair<Key, Boolean>>()

        var startIdx = 0
        HID_TEMPLATE_REGEX.findAll(processedString).forEach {
            // Adds everything before the template, restoring any escaped braces that came
            // from barcode data (expandCode=false path in TemplateProcessor).
            val before = restoreEscapedBraces(processedString.substring(startIdx, it.range.first))
            result.addAll(translateStringDetailed(before, locale))

            // Process HID-specific template
            val template = it.groupValues[1]
            if (template.startsWith("CODE")) {
                // {CODE} should always be substituted by TemplateProcessor before reaching here.
                Log.w(TAG, "Unsubstituted CODE template in processed string — typing literally")
                result.addAll(translateStringDetailed(template, locale))
            } else {
                result.addAll(processHidSpecificTemplateDetailed(template, locale))
            }

            startIdx = it.range.last + 1
        }

        // Adds the rest of the template, also restoring any trailing escaped braces.
        val after = restoreEscapedBraces(processedString.substring(startIdx))
        result.addAll(translateStringDetailed(after, locale))

        return result
    }

    // Process HID-specific templates (modifiers, F-keys, arrows, etc.)
    private fun processHidSpecificTemplateDetailed(
        template: String,
        locale: String
    ): List<Pair<Key, Boolean>> {
        val result = mutableListOf<Pair<Key, Boolean>>()
        var modifiers = 0.toUByte()
        var temp = template
        var wasModifier = true

        // Parse modifiers: +^#@
        do {
            when (temp.firstOrNull()) {
                '+' -> modifiers = modifiers or LCTRL
                '^' -> modifiers = modifiers or LSHIFT
                '#' -> modifiers = modifiers or LALT
                '@' -> modifiers = modifiers or LMETA
                else -> wasModifier = false
            }
            if (wasModifier) {
                temp = temp.substring(1)
            }
        } while (wasModifier)

        if (temp.isEmpty()) {
            Log.w(TAG, "Malformed HID template: modifier(s) with no key name (original: '$template')")
            return result
        }

        if (temp.isNotEmpty()) {
            Log.d(TAG, "HID template: $temp, modifiers: $modifiers")

            staticTemplates[temp]?.let { t ->
                // A static template key produces a visible character only when sent without
                // extra modifiers.  {+ENTER} (Ctrl+Enter) is a control combo, not visible text.
                result.add(Key(t.first or modifiers, t.second) to (isTextProducingKeycode(t.second) && modifiers == 0.toUByte()))
            } ?: dynamicTemplates[temp.substringBefore(':')]?.let { t ->
                t(locale, temp.substringAfter(':', "")).forEach { key ->
                    result.add(key to false) // dynamic templates (WAIT, etc.) produce no visible chars
                }
            } ?: run {
                // Fallback: unknown template — treat as plain text with optional extra modifiers.
                // Extra modifier flags (e.g. {+A} = Ctrl+A) turn it into a control combo → not visible.
                val hasExtraModifiers = modifiers != 0.toUByte()
                translateStringDetailed(temp, locale).forEach { (key, completesChar) ->
                    result.add(Key(key.first or modifiers, key.second) to (completesChar && !hasExtraModifiers))
                }
            }
        }
        return result
    }

    /**
     * Translates [string] into HID key events.  Each event carries a flag indicating whether
     * For dead-key sequences (e.g. `é` = dead-accent key + `e` key): the dead-accent key has
     * `completesChar = false`; the following `e` key has `completesChar = true`.
     * For regular single-key characters: `completesChar = true`.
     */
    internal fun translateStringDetailed(string: String, locale: String): List<Pair<Key, Boolean>> {
        val result = mutableListOf<Pair<Key, Boolean>>()

        Log.d(TAG, "Translating: '$string' with locale '$locale'")

        string.forEach { chr ->
            translate(chr, locale)?.let { (key, dkey) ->
                if (dkey != null) {
                    result.add(key to false)  // dead key prefix — does not yet complete a char
                    result.add(dkey to true)  // second key completes the visible character
                } else {
                    result.add(key to true)
                }
            } ?: Log.w(TAG, "Unknown char: $chr (${chr.code})")
        }

        return result
    }

    /**
     * Returns true if [keycode] adds a visible character on the host, meaning exactly one
     * backspace is needed to undo it.  HID keycodes 0x04–0x38 cover all such keys: printable
     * characters (letters, digits, punctuation), Space (0x2C), Tab (0x2B), and Enter (0x28).
     *
     * Explicitly excluded from the range:
     * - ESC (0x29): no visible effect.
     * - Backspace (0x2A): *removes* a character rather than adding one.  Counting it as a
     *   visible char would cause undo to send one extra backspace per {BKSP} in the template,
     *   deleting a character the user never typed.
     *
     * Note: this function only inspects the keycode.  Callers must additionally check that no
     * modifier flags are set — a key combined with Ctrl/Alt/Meta becomes a control combo that
     * produces no visible character (e.g. Ctrl+Enter, Ctrl+Tab).
     */
    private fun isTextProducingKeycode(keycode: UByte): Boolean =
        keycode.toInt() in 0x04..0x38
            && keycode != 0x29.toUByte()   // ESC
            && keycode != 0x2A.toUByte()   // Backspace

    private fun translate(char: Char, locale: String): Pair<Key, Key?>? {
        val keymap = keyMaps[locale] ?: baseMap
        return CUSTOM_KEYMAP[char] ?: keymap[char] ?: baseMap[char]
    }

}
