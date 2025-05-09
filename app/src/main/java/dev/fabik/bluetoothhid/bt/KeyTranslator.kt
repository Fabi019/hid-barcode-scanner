package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.res.AssetManager
import android.util.Base64
import android.util.Log
import java.text.DateFormat
import java.util.Calendar
import java.util.Collections
import kotlin.experimental.or

// Represents a key with its modifier and hid scan code
typealias Key = Pair<Byte, Byte>

// Represents a keymap with a map of chars and their key codes
typealias Keymap = Map<Char, Key>

class KeyTranslator(context: Context) {
    companion object {
        private const val TAG = "KeyTranslator"

        const val LCTRL: Byte = 0x01
        const val LSHIFT: Byte = 0x02
        const val LALT: Byte = 0x04
        private const val LMETA: Byte = 0x08
//        private const val RCTRL: Byte = 0x10
//        private const val RSHIFT: Byte = 0x20
//        private const val RALT: Byte = 0x40
//        private const val RMETA = 0x80.toByte()

        private val SPACE = ' ' to Key(0, 0x2C)
        private val TAB = '\t' to Key(0, 0x2B)
        private val RETURN = '\n' to Key(0, 0x28)

        val CAPS_LOCK_KEY = Key(0, 0x39)

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
                        Key(modifier.toByte(16), code.toByte(16))
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

        staticTemplates["F1"] = Key(0, 0x3A)
        staticTemplates["F2"] = Key(0, 0x3B)
        staticTemplates["F3"] = Key(0, 0x3C)
        staticTemplates["F4"] = Key(0, 0x3D)
        staticTemplates["F5"] = Key(0, 0x3E)
        staticTemplates["F6"] = Key(0, 0x3F)
        staticTemplates["F7"] = Key(0, 0x40)
        staticTemplates["F8"] = Key(0, 0x41)
        staticTemplates["F9"] = Key(0, 0x42)
        staticTemplates["F10"] = Key(0, 0x43)
        staticTemplates["F11"] = Key(0, 0x44)
        staticTemplates["F12"] = Key(0, 0x45)
        staticTemplates["ENTER"] = Key(0, 0x28)
        staticTemplates["ESC"] = Key(0, 0x29)
        staticTemplates["BKSP"] = Key(0, 0x2A)
        staticTemplates["TAB"] = Key(0, 0x2B)
        staticTemplates["RIGHT"] = Key(0, 0x4F)
        staticTemplates["LEFT"] = Key(0, 0x50)
        staticTemplates["DOWN"] = Key(0, 0x51)
        staticTemplates["UP"] = Key(0, 0x52)

//     winlin97:  added unused function keys for special usage
//                F1-F12 are binded in many Windows apps - also in Explorer
//                Now You can write an external app that can recognize this keys
//                as a triggers (StartKey -> Barcode -> StopKey), 
//                without any unexpected interactions with the system.
        staticTemplates["F13"] = Key(0, 0x68)
        staticTemplates["F14"] = Key(0, 0x69)
        staticTemplates["F15"] = Key(0, 0x6a)
        staticTemplates["F16"] = Key(0, 0x6b)
        staticTemplates["F17"] = Key(0, 0x6c)
        staticTemplates["F18"] = Key(0, 0x6d)
        staticTemplates["F19"] = Key(0, 0x6e)
        staticTemplates["F20"] = Key(0, 0x6f)
        staticTemplates["F21"] = Key(0, 0x70)
        staticTemplates["F22"] = Key(0, 0x71)
        staticTemplates["F23"] = Key(0, 0x72)
        staticTemplates["F24"] = Key(0, 0x73)

        val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
        val timeFormat = DateFormat.getTimeInstance()

        dynamicTemplates["DATE"] = { locale, args ->
            translateString(
                dateFormat.format(Calendar.getInstance().time),
                locale,
            )
        }

        dynamicTemplates["TIME"] = { locale, args ->
            translateString(
                timeFormat.format(Calendar.getInstance().time),
                locale,
            )
        }

        dynamicTemplates["WAIT"] = { locale, args ->
            Collections.nCopies<Key>(args.toIntOrNull() ?: 1, Key(0, 0))
        }
    }

    // Translates a string into a list of keys using a template string
    fun translateStringWithTemplate(
        string: String,
        locale: String,
        templateString: String,
        expandedCode: List<Key>? = null
    ): List<Key> {
        val keys = mutableListOf<Key>()
        val templateRegex = Regex("\\{([+^#@]*[\\w:]+)\\}")

        var startIdx = 0
        templateRegex.findAll(templateString).forEach {
            // Adds everything before the first template
            val before = templateString.substring(startIdx, it.range.first)
            keys.addAll(translateString(before, locale))

            // Adds the template
            val template = it.groupValues[1]
            if (template.startsWith("CODE")) {
                if (expandedCode == null) {
                    val text = when (template) {
                        "CODE_HEX" -> string.toByteArray()
                            .joinToString("") { b -> "%02x".format(b) }

                        "CODE_B64" -> Base64.encodeToString(string.toByteArray(), Base64.NO_WRAP)
                        else -> string
                    }
                    keys.addAll(translateString(text, locale))
                } else {
                    keys.addAll(expandedCode)
                }
            } else {
                var modifiers = 0.toByte()
                var temp = template
                var wasModifier = true

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
                    Log.d(TAG, "Template $temp modifier $modifiers")

                    staticTemplates[temp]?.let { t ->
                        keys.add(t.first or modifiers to t.second)
                    } ?: dynamicTemplates[temp.substringBefore(':')]?.let { t ->
                        keys.addAll(t(locale, temp.substringAfter(':', "")))
                    } ?: translateString(temp, locale).forEach { t ->
                        keys.add(Key(t.first or modifiers, t.second))
                    }
                }
            }

            startIdx = it.range.last + 1
        }

        // Adds the rest of the template
        val after = templateString.substring(startIdx)
        keys.addAll(translateString(after, locale))

        return keys
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
