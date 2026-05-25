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
     * [translateStringWithTemplate], so that literal braces originating from barcode data are
     * typed as real characters rather than remaining as unrecognised codepoints.
     */
    private fun restoreEscapedBraces(text: String): String =
        text.replace(ESCAPED_OPEN_BRACE, '{').replace(ESCAPED_CLOSE_BRACE, '}')

    // Translates a string into a list of keys using a template string
    // Note: Basic templates (CODE, DATE, TIME, etc.) should be processed by TemplateProcessor first
    fun translateStringWithTemplate(
        processedString: String,
        locale: String,
        expandedCode: List<Key>? = null
    ): List<Key> {
        val keys = mutableListOf<Key>()
        val templateRegex = Regex("\\{([+^#@]*[\\w:]+)\\}")

        var startIdx = 0
        templateRegex.findAll(processedString).forEach {
            // Adds everything before the template, restoring any escaped braces that came
            // from barcode data (expandCode=false path in TemplateProcessor).
            val before = restoreEscapedBraces(processedString.substring(startIdx, it.range.first))
            keys.addAll(translateString(before, locale))

            // Process HID-specific template
            val template = it.groupValues[1]
            if (template.startsWith("CODE")) {
                // Handle expandedCode mechanism (for complex template processing)
                if (expandedCode != null) {
                    keys.addAll(expandedCode)
                } else {
                    // This shouldn't happen if TemplateProcessor handled basic templates
                    Log.w(TAG, "CODE template found in processed string: $template")
                    keys.addAll(translateString(template, locale))
                }
            } else {
                processHidSpecificTemplate(template, locale, keys)
            }

            startIdx = it.range.last + 1
        }

        // Adds the rest of the template, also restoring any trailing escaped braces.
        val after = restoreEscapedBraces(processedString.substring(startIdx))
        keys.addAll(translateString(after, locale))

        return keys
    }

    // Process HID-specific templates (modifiers, F-keys, arrows, etc.)
    private fun processHidSpecificTemplate(
        template: String,
        locale: String,
        keys: MutableList<Key>
    ) {
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

        if (temp.isNotEmpty()) {
            Log.d(TAG, "HID template: $temp, modifiers: $modifiers")

            staticTemplates[temp]?.let { t ->
                keys.add(t.first or modifiers to t.second)
            } ?: dynamicTemplates[temp.substringBefore(':')]?.let { t ->
                keys.addAll(t(locale, temp.substringAfter(':', "")))
            } ?: translateString(temp, locale).forEach { t ->
                keys.add(Key(t.first or modifiers, t.second))
            }
        }
    }

    /**
     * Counts the number of characters that will actually appear as typed text on the host
     * for a given [processedString] going through [translateStringWithTemplate].
     *
     * - Regular chars: each counts as 1 (dead-key sequences still produce one visible char).
     * - Text-producing HID templates ({ENTER}, {TAB}, {BKSP}, {SPACE}): count as 1 each.
     * - Non-text HID templates ({F1}–{F24}, {LEFT}, {RIGHT}, {UP}, {DOWN}, {ESC}, {WAIT}): 0.
     */
    fun countTypedChars(processedString: String, locale: String): Int {
        val templateRegex = Regex("\\{([+^#@]*[\\w:]+)\\}")
        // Templates that produce exactly one typed character on the host
        val textProducingTemplates = setOf("ENTER", "TAB", "BKSP", "SPACE")

        var count = 0
        var startIdx = 0

        templateRegex.findAll(processedString).forEach { match ->
            // Count plain chars before this template
            count += processedString.substring(startIdx, match.range.first).length
            // Count the template itself if it produces text
            val templateName = match.groupValues[1].trimStart('+', '^', '#', '@')
                .substringBefore(':')
            if (templateName in textProducingTemplates || templateName.startsWith("CODE")) {
                count += 1
            }
            // Non-text templates (Fx, arrows, ESC, WAIT, …) contribute 0
            startIdx = match.range.last + 1
        }

        // Count remaining plain chars after last template
        count += processedString.substring(startIdx).length

        return count
    }

    // Converts a normal string into a list of keys
    fun translateString(string: String, locale: String): List<Key> {
        val keys = mutableListOf<Key>()

        Log.d(TAG, "Translating: '$string' with locale '$locale'")

        string.forEach { chr ->
            translate(chr, locale)?.let { (key, dkey) ->
                keys.add(key)
                dkey?.let { keys.add(it) }
            } ?: Log.w(TAG, "Unknown char: $chr (${chr.code})")
        }

        return keys
    }

    private fun translate(char: Char, locale: String): Pair<Key, Key?>? {
        val keymap = keyMaps[locale] ?: baseMap
        return CUSTOM_KEYMAP[char] ?: keymap[char] ?: baseMap[char]
    }

}
