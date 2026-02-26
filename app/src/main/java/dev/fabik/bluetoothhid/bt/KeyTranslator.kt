package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.util.Collections

// Represents a key with its modifier and hid scan code
typealias Key = Pair<UByte, UByte>

// Represents a keymap with a map of chars and their key codes
typealias Keymap = Map<Char, Key>

class KeyTranslator(context: Context) {
    companion object {
        private const val TAG = "KeyTranslator"

        const val LCTRL: UByte = 0x01u
        const val LSHIFT: UByte = 0x02u
        const val LALT: UByte = 0x04u
        private const val LMETA: UByte = 0x08u
//        private const val RCTRL: UByte = 0x10
//        private const val RSHIFT: UByte = 0x20
//        private const val RALT: UByte = 0x40
//        private const val RMETA: UByte = 0x80

        private val SPACE = ' ' to Key(0u, 0x2Cu)
        private val TAB = '\t' to Key(0u, 0x2Bu)
        private val RETURN = '\n' to Key(0u, 0x28u)

        val CAPS_LOCK_KEY = Key(0u, 0x39u)

        private const val CUSTOM_KEYMAP_FILE = "custom.layout"
        private var customKeyMapLoaded = false
        var CUSTOM_KEYMAP = mutableMapOf<Char, Key>()

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
                    CUSTOM_KEYMAP.forEach { k, (m, h) ->
                        it.write("$k ${h.toString(16)} ${m.toString(16)}")
                        it.newLine()
                    }
                }
            }.onFailure {
                Log.e("CustomKeys", "Error saving custom keymap", it)
            }
        }

        private fun loadKeymap(lines: List<String>): Keymap {
            val keymap = mutableMapOf<Char, Key>()

            lines.forEach {
                if (it.startsWith("##") || it.isBlank())
                    return@forEach

                runCatching {
                    val (key, code, modifier) = it.split(" ")

                    keymap[key.first()] =
                        Key(modifier.toUByte(16), code.toUByte(16))
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
            // Adds everything before the template
            val before = processedString.substring(startIdx, it.range.first)
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

        // Adds the rest of the template
        val after = processedString.substring(startIdx)
        keys.addAll(translateString(after, locale))

        return keys
    }

    // Process HID-specific templates (modifiers, F-keys, arrows, etc.)
    private fun processHidSpecificTemplate(template: String, locale: String, keys: MutableList<Key>) {
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

    // Converts a normal string into a list of keys
    fun translateString(string: String, locale: String): List<Key> {
        val keys = mutableListOf<Key>()

        Log.d(TAG, "Translating: '$string' with locale '$locale'")

        string.forEach {
            translate(it, locale)?.let { key ->
                keys.add(key)
            } ?: Log.w(TAG, "Unknown char: $it (${it.code})")
        }

        return keys
    }

    private fun translate(char: Char, locale: String): Key? {
        val keymap = keyMaps[locale] ?: baseMap
        return CUSTOM_KEYMAP[char] ?: keymap[char] ?: baseMap[char]
    }

}
