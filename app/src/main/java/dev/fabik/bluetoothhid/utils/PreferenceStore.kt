package dev.fabik.bluetoothhid.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val Context.dataStore by preferencesDataStore("settings")

// Enums for type-safe preference values
enum class ConnectionMode { HID, RFCOMM }
enum class KeyboardLayout(val index: Int) {
    US(0), DE(1), FR(2), GB(3), ES(4), IT(5), TR(6), PL(7), CZ(8);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: US
    }
}
enum class ExtraKeys(val index: Int) {
    NONE(0), ENTER(1), TAB(2), SPACE(3), CUSTOM(4);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: NONE
    }
}
enum class Theme { SYSTEM, LIGHT, DARK }
enum class FocusMode(val index: Int) {
    AUTO(0), MANUAL(1), MACRO(2), CONTINUOUS(3), EDOF(4), INFINITY(5), CONTINUOUS_PICTURE(6);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: AUTO
    }
}
enum class ScanResolution(val index: Int) {
    SD_480P(0), HD_720P(1), FHD_1080P(2), UHD_2160P(3);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: HD_720P
    }
}
enum class ScanFrequency(val index: Int) {
    FASTEST(0), FAST(1), NORMAL(2), SLOW(3);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: NORMAL
    }
}
enum class OverlayType(val index: Int) {
    SQUARE(0), RECTANGLE(1), CUSTOM(2);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: SQUARE
    }
}
enum class Binarizer(val index: Int) {
    LOCAL_AVERAGE(0), GLOBAL_HISTOGRAM(1), FIXED_THRESHOLD(2), BOOL_CAST(3);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: LOCAL_AVERAGE
    }
}
enum class TextMode(val index: Int) {
    PLAIN(0), ECI(1), HRI(2), HEX(3), ESCAPED(4);
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: HRI
    }
}

open class PreferenceStore {
    sealed class Preference<T>(
        val key: Preferences.Key<T>,
        val defaultValue: T
    ) {
        fun extract(prefs: Map<Preference<*>, *>): T = prefs[this] as? T ?: defaultValue
    }

    class StringPref(key: Preferences.Key<String>, defaultValue: String) :
        Preference<String>(key, defaultValue)

    class IntPref(key: Preferences.Key<Int>, defaultValue: Int) :
        Preference<Int>(key, defaultValue)

    class BooleanPref(key: Preferences.Key<Boolean>, defaultValue: Boolean) :
        Preference<Boolean>(key, defaultValue)

    class FloatPref(key: Preferences.Key<Float>, defaultValue: Float) :
        Preference<Float>(key, defaultValue)

    class SetPref(key: Preferences.Key<Set<String>>, defaultValue: Set<String>) :
        Preference<Set<String>>(key, defaultValue)

    // Enum preference that stores as Int but exposes as Enum
    class EnumPref<E : Enum<E>>(
        key: Preferences.Key<Int>,
        defaultValue: E,
        private val fromOrdinal: (Int) -> E
    ) : Preference<Int>(key, defaultValue.ordinal) {
        fun toEnum(value: Int): E = fromOrdinal(value)
        val defaultEnum: E get() = fromOrdinal(defaultValue)

        // Extract as enum from multi-preference map
        fun extractEnum(prefs: Map<Preference<*>, *>): E {
            val intValue = extract(prefs)
            return fromOrdinal(intValue)
        }
    }

    companion object {
        private infix fun Preferences.Key<String>.defaultsTo(value: String) =
            StringPref(this, value)

        private infix fun Preferences.Key<Int>.defaultsTo(value: Int) =
            IntPref(this, value)

        private infix fun Preferences.Key<Boolean>.defaultsTo(value: Boolean) =
            BooleanPref(this, value)

        private infix fun Preferences.Key<Float>.defaultsTo(value: Float) =
            FloatPref(this, value)

        private infix fun Preferences.Key<Set<String>>.defaultsTo(value: Set<String>) =
            SetPref(this, value)

        private fun <E : Enum<E>> Preferences.Key<Int>.enumDefaultsTo(
            value: E,
            fromOrdinal: (Int) -> E
        ) = EnumPref(this, value, fromOrdinal)

        // Connection
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect") defaultsTo false
        val CONNECTION_MODE = intPreferencesKey("connection_mode").enumDefaultsTo(
            ConnectionMode.HID
        ) { ConnectionMode.entries[it] }
        //val SHOW_UNNAMED = booleanPreferencesKey("show_unnamed") defaultsTo false // Removed
        val SEND_WITH_VOLUME = booleanPreferencesKey("send_vol_key") defaultsTo false
        val SEND_DELAY = floatPreferencesKey("send_delay") defaultsTo 10f
        val KEYBOARD_LAYOUT = intPreferencesKey("keyboard_layout").enumDefaultsTo(
            KeyboardLayout.US
        ) { KeyboardLayout.fromIndex(it) }
        val EXTRA_KEYS = intPreferencesKey("extra_keys").enumDefaultsTo(
            ExtraKeys.NONE
        ) { ExtraKeys.fromIndex(it) }
        val TEMPLATE_TEXT = stringPreferencesKey("template_text") defaultsTo ""
        val ENABLE_JS = booleanPreferencesKey("enable_js") defaultsTo false
        val JS_CODE = stringPreferencesKey("js_code") defaultsTo ""
        val EXPAND_CODE = booleanPreferencesKey("expand_code") defaultsTo false
        val INSECURE_RFCOMM = booleanPreferencesKey("insecure_rfcomm") defaultsTo false
        val PRESERVE_UNSUPPORTED_PLACEHOLDERS = booleanPreferencesKey("preserve_unsupported_placeholders") defaultsTo false

        // Appearance
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on") defaultsTo false
        val ALLOW_SCREEN_ROTATION = booleanPreferencesKey("allow_screen_rotation") defaultsTo false
        val SCANNER_FULL_SCREEN = booleanPreferencesKey("scanner_full_screen") defaultsTo false
        val THEME = intPreferencesKey("theme").enumDefaultsTo(
            Theme.SYSTEM
        ) { Theme.entries[it] }
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme") defaultsTo false

        // Camera
        // val AUTO_FOCUS = booleanPreferencesKey("auto_focus") defaultsTo true // Removed
        val FRONT_CAMERA = booleanPreferencesKey("front_camera") defaultsTo false
        val FIX_EXPOSURE = booleanPreferencesKey("fix_exposure") defaultsTo false
        val FOCUS_MODE = intPreferencesKey("focus_mode").enumDefaultsTo(
            FocusMode.AUTO
        ) { FocusMode.fromIndex(it) }
        val PREVIEW_PERFORMANCE_MODE =
            booleanPreferencesKey("preview_performance_mode") defaultsTo false
        val SCAN_RESOLUTION = intPreferencesKey("scan_res").enumDefaultsTo(
            ScanResolution.HD_720P
        ) { ScanResolution.fromIndex(it) }
        // val AUTO_ZOOM = booleanPreferencesKey("auto_zoom") defaultsTo false - Removed

        // Scanner
        val SCAN_FREQUENCY = intPreferencesKey("scan_freq").enumDefaultsTo(
            ScanFrequency.NORMAL
        ) { ScanFrequency.fromIndex(it) }
        val CODE_TYPES = stringSetPreferencesKey("code_types") defaultsTo setOf()
        val SCAN_REGEX = stringPreferencesKey("scan_regex") defaultsTo ""
        val RESTRICT_AREA = booleanPreferencesKey("restrict_area") defaultsTo true
        val FULL_INSIDE = booleanPreferencesKey("full_inside") defaultsTo true
        val OVERLAY_TYPE = intPreferencesKey("overlay_type").enumDefaultsTo(
            OverlayType.SQUARE
        ) { OverlayType.fromIndex(it) }
        val AUTO_SEND = booleanPreferencesKey("auto_send") defaultsTo false
        val PLAY_SOUND = booleanPreferencesKey("play_sound") defaultsTo false
        val VIBRATE = booleanPreferencesKey("vibrate") defaultsTo false
        val CLEAR_AFTER_SEND = booleanPreferencesKey("clear_after_send") defaultsTo false

        // val RAW_VALUE = booleanPreferencesKey("raw_value") defaultsTo false - Removed
        // val SHOW_POSSIBLE = booleanPreferencesKey("show_possible") defaultsTo false - Removed
        // val HIGHLIGHT_TYPE = intPreferencesKey("highlight") defaultsTo 0 // Box - Removed
        val PRIVATE_MODE = booleanPreferencesKey("private_mode") defaultsTo false
        val PERSIST_HISTORY = booleanPreferencesKey("persist_history") defaultsTo true

        val ADV_TRY_HARDER = booleanPreferencesKey("adv_try_harder") defaultsTo false
        val ADV_TRY_ROTATE = booleanPreferencesKey("adv_try_rotate") defaultsTo false
        val ADV_TRY_INVERT = booleanPreferencesKey("adv_try_invert") defaultsTo false
        val ADV_TRY_DOWNSCALE = booleanPreferencesKey("adv_try_downscale") defaultsTo false
        val ADV_MIN_LINE_COUNT = intPreferencesKey("adv_min_line_count") defaultsTo 2
        val ADV_BINARIZER = intPreferencesKey("adv_binarizer").enumDefaultsTo(
            Binarizer.LOCAL_AVERAGE
        ) { Binarizer.fromIndex(it) }
        val ADV_DOWNSCALE_FACTOR = intPreferencesKey("adv_downscale_factor") defaultsTo 3
        val ADV_DOWNSCALE_THRESHOLD = intPreferencesKey("adv_downscale_threshold") defaultsTo 500
        val ADV_TEXT_MODE = intPreferencesKey("adv_text_mode").enumDefaultsTo(
            TextMode.HRI
        ) { TextMode.fromIndex(it) }

        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode") defaultsTo BuildConfig.DEBUG
        val OCR_COMPAT = booleanPreferencesKey("ocr_compat") defaultsTo false

        // Utility preferences
        val OVERLAY_POS_X = floatPreferencesKey("overlay_pos_x") defaultsTo 0.0f
        val OVERLAY_POS_Y = floatPreferencesKey("overlay_pos_y") defaultsTo 0.0f
        val OVERLAY_WIDTH = floatPreferencesKey("overlay_width") defaultsTo 100.0f
        val OVERLAY_HEIGHT = floatPreferencesKey("overlay_height") defaultsTo 100.0f
    }
}

suspend fun <T> Context.setPreference(pref: PreferenceStore.Preference<T>, value: T) {
    withContext(Dispatchers.IO) {
        dataStore.edit {
            it[pref.key] = value
        }
    }
}

fun <T> Context.getPreference(pref: PreferenceStore.Preference<T>): Flow<T> = dataStore.data
    .catch { e ->
        Log.e("PreferenceStore", "Error reading preference", e)
        emit(preferencesOf(pref.key to pref.defaultValue))
    }
    .map {
        it[pref.key] ?: pref.defaultValue
    }
    .flowOn(Dispatchers.IO)

fun Context.getPreferences(vararg prefs: PreferenceStore.Preference<*>): Flow<Map<PreferenceStore.Preference<*>, *>> {
    return dataStore.data
        .catch { e ->
            Log.e("PreferenceStore", "Error reading preferences", e)
            emit(emptyPreferences())
        }
        .map { preferences ->
            prefs.associateWith { pref ->
                preferences[pref.key] ?: pref.defaultValue
            }
        }
        .flowOn(Dispatchers.IO)
}

@Composable
fun Context.getMultiPreferenceState(vararg prefs: PreferenceStore.Preference<*>): State<Map<PreferenceStore.Preference<*>, *>?> {
    return remember { getPreferences(*prefs) }.collectAsStateWithLifecycle(null)
}

@Composable
fun <T> Context.getPreferenceStateDefault(
    pref: PreferenceStore.Preference<T>,
    initial: T = pref.defaultValue
): State<T> {
    val flow = remember { getPreference(pref) }
    val rawState = flow.collectAsState(initial)
    return remember { derivedStateOf { rawState.value } }
}

@Composable
fun <T> Context.getPreferenceState(pref: PreferenceStore.Preference<T>): State<T?> {
    val flow = remember { getPreference(pref) }
    val rawState = flow.collectAsState(null)
    return remember { derivedStateOf { rawState.value } }
}

@Composable
fun <T> Context.getPreferenceStateBlocking(pref: PreferenceStore.Preference<T>): State<T> {
    val flow = remember { getPreference(pref) }
    val initial = remember { runBlocking { flow.first() } }
    val rawState = flow.collectAsState(initial)
    return remember { derivedStateOf { rawState.value } }
}

@Composable
fun <T> rememberPreference(
    pref: PreferenceStore.Preference<T>,
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = context.getPreferenceStateBlocking(pref)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    scope.launch {
                        context.setPreference(pref, value)
                    }
                }

            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
fun <T> rememberPreferenceNull(
    pref: PreferenceStore.Preference<T>,
): MutableState<T?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = context.getPreferenceState(pref)

    return remember {
        object : MutableState<T?> {
            override var value: T?
                get() = state.value
                set(value) {
                    scope.launch {
                        context.setPreference(pref, value!!)
                    }
                }

            override fun component1(): T? = value
            override fun component2(): (T?) -> Unit = { value = it }
        }
    }
}

@Composable
fun <E : Enum<E>> rememberEnumPreference(
    pref: PreferenceStore.EnumPref<E>,
): MutableState<E> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val intState = context.getPreferenceStateDefault(pref)

    return remember(intState.value) {
        object : MutableState<E> {
            override var value: E
                get() = pref.toEnum(intState.value)
                set(value) {
                    scope.launch {
                        context.setPreference(pref, value.ordinal)
                    }
                }

            override fun component1(): E = value
            override fun component2(): (E) -> Unit = { value = it }
        }
    }
}