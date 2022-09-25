package dev.fabik.bluetoothhid.bt

import android.view.KeyEvent
import kotlin.experimental.and
import kotlin.experimental.or

@JvmInline
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
value class KeyboardReport(
    val bytes: ByteArray = ByteArray(3) { 0 }
) {
    var leftControl: Boolean
        get() = bytes[0] and 0b1 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b1
            else bytes[0] and 0b11111110.toByte()
        }

    var leftShift: Boolean
        get() = bytes[0] and 0b10 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b10
            else bytes[0] and 0b11111101.toByte()
        }

    var leftAlt: Boolean
        get() = bytes[0] and 0b10 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b100
            else bytes[0] and 0b11111011.toByte()
        }
    var leftGui: Boolean
        get() = bytes[0] and 0b10 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b1000
            else bytes[0] and 0b11110111.toByte()
        }

    var rightControl: Boolean
        get() = bytes[0] and 0b1 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b10000
            else bytes[0] and 0b11101111.toByte()
        }

    var rightShift: Boolean
        get() = bytes[0] and 0b10 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b100000
            else bytes[0] and 0b11011111.toByte()
        }

    var rightAlt: Boolean
        get() = bytes[0] and 0b10 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b1000000
            else bytes[0] and 0b10111111.toByte()
        }

    var rightGui: Boolean
        get() = bytes[0] and 0b10 != 0.toByte()
        set(value) {
            bytes[0] = if (value) bytes[0] or 0b10000000.toByte()
            else bytes[0] and 0b01111111
        }

    var key1: Byte
        get() = bytes[2]
        set(value) {
            bytes[2] = value
        }

//    var key2: Byte
//        get() = bytes[3]
//        set(value) { bytes[3] = value }
//
//    var key3: Byte
//        get() = bytes[4]
//        set(value) { bytes[4] = value }
//
//    var key4: Byte
//        get() = bytes[5]
//        set(value) { bytes[5] = value }
//
//    var key5: Byte
//        get() = bytes[6]
//        set(value) { bytes[6] = value }
//
//    var key6: Byte
//        get() = bytes[7]
//        set(value) { bytes[7] = value }

    fun reset() = bytes.fill(0)

    companion object {
        const val ID = 8

        val SCANCODE_TABLE = mapOf(
            KeyEvent.KEYCODE_A to 4,
            KeyEvent.KEYCODE_B to 5,
            KeyEvent.KEYCODE_C to 6,
            KeyEvent.KEYCODE_D to 7,
            KeyEvent.KEYCODE_E to 8,
            KeyEvent.KEYCODE_F to 9,
            KeyEvent.KEYCODE_G to 10,
            KeyEvent.KEYCODE_H to 11,
            KeyEvent.KEYCODE_I to 12,
            KeyEvent.KEYCODE_J to 13,
            KeyEvent.KEYCODE_K to 14,
            KeyEvent.KEYCODE_L to 15,
            KeyEvent.KEYCODE_M to 16,
            KeyEvent.KEYCODE_N to 17,
            KeyEvent.KEYCODE_O to 18,
            KeyEvent.KEYCODE_P to 19,
            KeyEvent.KEYCODE_Q to 20,
            KeyEvent.KEYCODE_R to 21,
            KeyEvent.KEYCODE_S to 22,
            KeyEvent.KEYCODE_T to 23,
            KeyEvent.KEYCODE_U to 24,
            KeyEvent.KEYCODE_V to 25,
            KeyEvent.KEYCODE_W to 26,
            KeyEvent.KEYCODE_X to 27,
            KeyEvent.KEYCODE_Y to 28,
            KeyEvent.KEYCODE_Z to 29,

            KeyEvent.KEYCODE_1 to 30,
            KeyEvent.KEYCODE_2 to 31,
            KeyEvent.KEYCODE_3 to 32,
            KeyEvent.KEYCODE_4 to 33,
            KeyEvent.KEYCODE_5 to 34,
            KeyEvent.KEYCODE_6 to 35,
            KeyEvent.KEYCODE_7 to 36,
            KeyEvent.KEYCODE_8 to 37,
            KeyEvent.KEYCODE_9 to 38,
            KeyEvent.KEYCODE_0 to 39,

            KeyEvent.KEYCODE_F1 to 58,
            KeyEvent.KEYCODE_F2 to 59,
            KeyEvent.KEYCODE_F3 to 60,
            KeyEvent.KEYCODE_F4 to 61,
            KeyEvent.KEYCODE_F5 to 62,
            KeyEvent.KEYCODE_F6 to 63,
            KeyEvent.KEYCODE_F7 to 64,
            KeyEvent.KEYCODE_F8 to 65,
            KeyEvent.KEYCODE_F9 to 66,
            KeyEvent.KEYCODE_F10 to 67,
            KeyEvent.KEYCODE_F11 to 68,
            KeyEvent.KEYCODE_F12 to 69,

            KeyEvent.KEYCODE_ENTER to 40,
            KeyEvent.KEYCODE_ESCAPE to 41,
            KeyEvent.KEYCODE_DEL to 42,
            KeyEvent.KEYCODE_TAB to 43,
            KeyEvent.KEYCODE_SPACE to 44,
            KeyEvent.KEYCODE_MINUS to 45,
            KeyEvent.KEYCODE_EQUALS to 46,
            KeyEvent.KEYCODE_LEFT_BRACKET to 47,
            KeyEvent.KEYCODE_RIGHT_BRACKET to 48,
            KeyEvent.KEYCODE_BACKSLASH to 49,
            KeyEvent.KEYCODE_SEMICOLON to 51,
            KeyEvent.KEYCODE_APOSTROPHE to 52,
            KeyEvent.KEYCODE_GRAVE to 53,
            KeyEvent.KEYCODE_COMMA to 54,
            KeyEvent.KEYCODE_PERIOD to 55,
            KeyEvent.KEYCODE_SLASH to 56,

            KeyEvent.KEYCODE_SCROLL_LOCK to 71,
            KeyEvent.KEYCODE_INSERT to 73,
            KeyEvent.KEYCODE_HOME to 74,
            KeyEvent.KEYCODE_PAGE_UP to 75,
            KeyEvent.KEYCODE_FORWARD_DEL to 76,
            KeyEvent.KEYCODE_MOVE_END to 77,
            KeyEvent.KEYCODE_PAGE_DOWN to 78,
            KeyEvent.KEYCODE_NUM_LOCK to 83,

            KeyEvent.KEYCODE_DPAD_RIGHT to 79,
            KeyEvent.KEYCODE_DPAD_LEFT to 80,
            KeyEvent.KEYCODE_DPAD_DOWN to 81,
            KeyEvent.KEYCODE_DPAD_UP to 82,

            KeyEvent.KEYCODE_AT to 31,
            KeyEvent.KEYCODE_POUND to 32,
            KeyEvent.KEYCODE_STAR to 37
        )
    }
}