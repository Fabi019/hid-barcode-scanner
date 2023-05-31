package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.text.DateFormat
import java.util.Calendar

// Represents a key with its modifier and hid scan code
typealias Key = Pair<Byte, Byte>

// Represents a keymap with a map of chars and their key codes
typealias Keymap = Map<Char, Key>

class KeyTranslator(context: Context) {
    companion object {
        private const val TAG = "KeyTranslator"

//        private const val LCTRL: Byte = 0x01
//        private const val LSHIFT: Byte = 0x02
//        private const val LALT: Byte = 0x04
//        private const val LMETA: Byte = 0x08
//        private const val RCTRL: Byte = 0x10
//        private const val RSHIFT: Byte = 0x20
//        private const val RALT: Byte = 0x40
//        private const val RMETA = 0x80.toByte()

        private val SPACE = ' ' to Key(0, 0x2C)
        private val TAB = '\t' to Key(0, 0x2B)
        private val RETURN = '\n' to Key(0, 0x28)
    }

    private val assetManager: AssetManager = context.assets

    private val baseMap: Keymap
    private val keyMaps: MutableMap<String, Keymap> = mutableMapOf()

    private val staticTemplates = mutableMapOf<String, List<Key>>()
    private val dynamicTemplates = mutableMapOf<String, (String) -> List<Key>>()

    init {
        assetManager.list("keymaps")?.forEach {
            keyMaps[it.removeSuffix(".layout")] = loadKeymap("keymaps/$it")
        }

        baseMap = keyMaps.remove("base") ?: run {
            Log.e(TAG, "No base keymap found?")
            emptyMap()
        }

        staticTemplates["F1"] = listOf(Key(0, 0x3A))
        staticTemplates["F2"] = listOf(Key(0, 0x3B))
        staticTemplates["F3"] = listOf(Key(0, 0x3C))
        staticTemplates["F4"] = listOf(Key(0, 0x3D))
        staticTemplates["F5"] = listOf(Key(0, 0x3E))
        staticTemplates["F6"] = listOf(Key(0, 0x3F))
        staticTemplates["F7"] = listOf(Key(0, 0x40))
        staticTemplates["F8"] = listOf(Key(0, 0x41))
        staticTemplates["F9"] = listOf(Key(0, 0x42))
        staticTemplates["F10"] = listOf(Key(0, 0x43))
        staticTemplates["F11"] = listOf(Key(0, 0x44))
        staticTemplates["F12"] = listOf(Key(0, 0x45))
        staticTemplates["ENTER"] = listOf(Key(0, 0x28))
        staticTemplates["ESC"] = listOf(Key(0, 0x29))
        staticTemplates["BKSP"] = listOf(Key(0, 0x2A))
        staticTemplates["TAB"] = listOf(Key(0, 0x2B))

        val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
        val timeFormat = DateFormat.getTimeInstance()

        dynamicTemplates["DATE"] = { locale ->
            translateString(
                dateFormat.format(Calendar.getInstance().time),
                locale,
            )
        }

        dynamicTemplates["TIME"] = { locale ->
            translateString(
                timeFormat.format(Calendar.getInstance().time),
                locale,
            )
        }
    }

    private fun loadKeymap(fileName: String): Keymap {
        Log.d(TAG, "Loading keymap: $fileName")

        val keymap = mutableMapOf(SPACE, TAB, RETURN)

        val lines = runCatching {
            assetManager.open(fileName).bufferedReader().readLines()
        }.onFailure {
            Log.e(TAG, "Failed to load keymap: $fileName", it)
        }.getOrNull() ?: return keymap

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

    // Translates a string into a list of keys using a template string
    fun translateStringWithTemplate(
        string: String,
        locale: String,
        templateString: String
    ): List<Key> {
        val keys = mutableListOf<Key>()
        val templateRegex = Regex("\\{([A-Z0-9]+)\\}")

        var startIdx = 0
        templateRegex.findAll(templateString).forEach {
            // Adds everything before the first template
            val before = templateString.substring(startIdx, it.range.first)
            keys.addAll(translateString(before, locale))

            // Adds the template
            val template = it.groupValues[1]
            if (template == "CODE") {
                keys.addAll(translateString(string, locale))
            } else {
                staticTemplates[template]?.let { t ->
                    keys.addAll(t)
                } ?: dynamicTemplates[template]?.let { t ->
                    keys.addAll(t(locale))
                } ?: Log.w(TAG, "Unknown template: $template")
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

        string.forEach {
            translate(it, locale)?.let { key ->
                keys.add(key)
            } ?: Log.w(TAG, "Unknown char: $it")
        }

        return keys
    }

    private fun translate(char: Char, locale: String): Key? {
        val keymap = keyMaps[locale] ?: baseMap
        return keymap[char] ?: baseMap[char]
    }

}
