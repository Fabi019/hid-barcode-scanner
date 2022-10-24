package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.content.res.AssetManager
import android.util.Log

typealias Keymap = Map<Char, Pair<Byte, Byte>>

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
        private const val RMETA: Byte = 0x80.toByte()

        private val SPACE = ' ' to (0.toByte() to 0x2C.toByte())
        private val TAB = '\t' to (0.toByte() to 0x2B.toByte())
        private val RETURN = '\n' to (0.toByte() to 0x28.toByte())
    }

    private val assetManager: AssetManager = context.assets

    private val baseMap: Keymap = loadKeymap("keymaps/us.cfg")
    private val keyMaps: MutableMap<String, Keymap> = mutableMapOf()

    init {
        assetManager.list("keymaps")?.forEach {
            keyMaps[it.removeSuffix(".cfg")] = loadKeymap("keymaps/$it")
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
            if (it.startsWith("#") || it.isBlank())
                return@forEach

            val parts = it.split("\t")
            val scanCode = parts[0].toInt(16).toByte()

            val keys = parts.subList(1, parts.size)

            keys.forEachIndexed { index, key ->
                val char = key.first()
                when (index) {
                    0 -> keymap[char] = Pair(0, scanCode)
                    1 -> keymap[char] = Pair(LSHIFT, scanCode)
                    2 -> keymap[char] = Pair(RALT, scanCode)
                }
            }
        }

        return keymap
    }

    fun translate(char: Char, locale: String): Pair<Byte, Byte>? {
        val keymap = keyMaps[locale] ?: baseMap
        return keymap[char] ?: baseMap[char]
    }

}
