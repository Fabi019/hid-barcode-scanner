package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.res.AssetManager
import android.util.Log

// Represents a key with its modifier and hid scan code
typealias Key = Pair<Byte, Byte>

// Represents a keymap with a map of chars and their key codes
typealias Keymap = Map<Char, Key>

class KeyTranslator(context: Context) {
    companion object {
        private const val TAG = "KeyTranslator"

        private const val LCTRL: Byte = 0x01
        private const val LSHIFT: Byte = 0x02
        private const val LALT: Byte = 0x04
        private const val LMETA: Byte = 0x08
        private const val RCTRL: Byte = 0x10
        private const val RSHIFT: Byte = 0x20
        private const val RALT: Byte = 0x40
        private const val RMETA = 0x80.toByte()

        private val SPACE = ' ' to Key(0, 0x2C)
        private val TAB = '\t' to Key(0, 0x2B)
        private val RETURN = '\n' to Key(0, 0x28)
    }

    private val assetManager: AssetManager = context.assets

    private val baseMap: Keymap
    private val keyMaps: MutableMap<String, Keymap> = mutableMapOf()

    init {
        assetManager.list("keymaps")?.forEach {
            keyMaps[it.removeSuffix(".layout")] = loadKeymap("keymaps/$it")
        }

        baseMap = keyMaps.remove("base") ?: run {
            Log.e(TAG, "No base keymap found")
            emptyMap()
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

    fun translate(char: Char, locale: String): Key? {
        val keymap = keyMaps[locale] ?: baseMap
        return keymap[char] ?: baseMap[char]
    }

}
