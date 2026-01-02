package dev.fabik.bluetoothhid.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
// Note: ordinal is used as index - no need for explicit val index
enum class ConnectionMode {
    HID, RFCOMM;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: HID
    }
}
enum class KeyboardLayout {
    US, DE, FR, GB, ES, IT, TR, PL, CZ;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: US
    }
}
enum class ExtraKeys {
    NONE, ENTER, TAB, SPACE, CUSTOM;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: NONE
    }
}
enum class Theme {
    SYSTEM, LIGHT, DARK;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: SYSTEM
    }
}
enum class FocusMode {
    AUTO, MANUAL, MACRO, CONTINUOUS, EDOF, INFINITY, CONTINUOUS_PICTURE;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: AUTO
    }
}
enum class ScanResolution {
    SD_480P, HD_720P, FHD_1080P, UHD_2160P;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: HD_720P
    }
}
enum class ScanFrequency {
    FASTEST, FAST, NORMAL, SLOW;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: NORMAL
    }
}
enum class OverlayType {
    SQUARE, RECTANGLE, CUSTOM;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: SQUARE
    }
}
enum class Binarizer {
    LOCAL_AVERAGE, GLOBAL_HISTOGRAM, FIXED_THRESHOLD, BOOL_CAST;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: LOCAL_AVERAGE
    }
}
enum class TextMode {
    PLAIN, ECI, HRI, HEX, ESCAPED;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: HRI
    }
}
enum class CropMode {
    NONE, SCAN_AREA, BARCODE;
    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: NONE
    }
}

enum class ScanImageFormat(var value: Bitmap.CompressFormat) {
    JPEG(Bitmap.CompressFormat.JPEG), PNG(Bitmap.CompressFormat.PNG), WEBP_LOSSY(Bitmap.CompressFormat.WEBP_LOSSY), WEBP_LOSSLESS(
        Bitmap.CompressFormat.WEBP_LOSSLESS
    );

    companion object {
        fun fromIndex(index: Int) = entries.getOrNull(index) ?: JPEG
    }
}

open class PreferenceStore {
    // Simple generic preference class - no need for type-specific wrappers
    open class Preference<T>(
        val key: Preferences.Key<T>,
        val defaultValue: T
    ) {
        fun extract(prefs: Map<Preference<*>, *>): T = prefs[this] as? T ?: defaultValue
    }

    // Enum preference that stores as Int but exposes as Enum
    // Uses -1 as sentinel value, fromOrdinal must handle it and return default
    class EnumPref<E : Enum<E>>(
        key: Preferences.Key<Int>,
        val fromOrdinal: (Int) -> E  // Public for direct access, must handle -1
    ) : Preference<Int>(key, -1) {
        // Get default enum value by calling fromOrdinal with -1
        fun getDefaultEnum(): E = fromOrdinal(-1)

        // Extract as enum from multi-preference map
        fun extractEnum(prefs: Map<Preference<*>, *>): E {
            val intValue = extract(prefs)
            return fromOrdinal(intValue)
        }
    }

    companion object {
        // Simple infix functions to create preferences - no wrapper classes needed
        private infix fun <T> Preferences.Key<T>.defaultsTo(value: T) =
            Preference(this, value)

        private infix fun <E : Enum<E>> Preferences.Key<Int>.enumDefaultsTo(
            fromOrdinal: (Int) -> E
        ) = EnumPref(this, fromOrdinal)

        // Connection
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect") defaultsTo false
        val CONNECTION_MODE = intPreferencesKey("connection_mode").enumDefaultsTo(ConnectionMode::fromIndex)
        //val SHOW_UNNAMED = booleanPreferencesKey("show_unnamed") defaultsTo false // Removed
        val SEND_WITH_VOLUME = booleanPreferencesKey("send_vol_key") defaultsTo false
        val SEND_DELAY = floatPreferencesKey("send_delay") defaultsTo 10f
        val KEYBOARD_LAYOUT = intPreferencesKey("keyboard_layout").enumDefaultsTo(KeyboardLayout::fromIndex)
        val EXTRA_KEYS = intPreferencesKey("extra_keys").enumDefaultsTo(ExtraKeys::fromIndex)
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
        val THEME = intPreferencesKey("theme").enumDefaultsTo(Theme::fromIndex)
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme") defaultsTo false

        // Camera
        // val AUTO_FOCUS = booleanPreferencesKey("auto_focus") defaultsTo true // Removed
        val FRONT_CAMERA = booleanPreferencesKey("front_camera") defaultsTo false
        val FIX_EXPOSURE = booleanPreferencesKey("fix_exposure") defaultsTo false
        val FOCUS_MODE = intPreferencesKey("focus_mode").enumDefaultsTo(FocusMode::fromIndex)
        val PREVIEW_PERFORMANCE_MODE = booleanPreferencesKey("preview_performance_mode") defaultsTo false
        val SCAN_RESOLUTION = intPreferencesKey("scan_res").enumDefaultsTo(ScanResolution::fromIndex)
        // val AUTO_ZOOM = booleanPreferencesKey("auto_zoom") defaultsTo false - Removed

        // Scanner
        val SCAN_FREQUENCY = intPreferencesKey("scan_freq").enumDefaultsTo(ScanFrequency::fromIndex)
        val CODE_TYPES = stringSetPreferencesKey("code_types") defaultsTo setOf()
        val SCAN_REGEX = stringPreferencesKey("scan_regex") defaultsTo ""
        val RESTRICT_AREA = booleanPreferencesKey("restrict_area") defaultsTo true
        val FULL_INSIDE = booleanPreferencesKey("full_inside") defaultsTo true
        val OVERLAY_TYPE = intPreferencesKey("overlay_type").enumDefaultsTo(OverlayType::fromIndex)
        val AUTO_SEND = booleanPreferencesKey("auto_send") defaultsTo false
        val PLAY_SOUND = booleanPreferencesKey("play_sound") defaultsTo false
        val VIBRATE = booleanPreferencesKey("vibrate") defaultsTo false
        val CLEAR_AFTER_SEND = booleanPreferencesKey("clear_after_send") defaultsTo false

        // val RAW_VALUE = booleanPreferencesKey("raw_value") defaultsTo false - Removed
        // val SHOW_POSSIBLE = booleanPreferencesKey("show_possible") defaultsTo false - Removed
        // val HIGHLIGHT_TYPE = intPreferencesKey("highlight").enumDefaultsTo(Highlight::fromIndex) // Box - Removed
        val PRIVATE_MODE = booleanPreferencesKey("private_mode") defaultsTo false
        val PERSIST_HISTORY = booleanPreferencesKey("persist_history") defaultsTo true

        val ADV_TRY_HARDER = booleanPreferencesKey("adv_try_harder") defaultsTo false
        val ADV_TRY_ROTATE = booleanPreferencesKey("adv_try_rotate") defaultsTo false
        val ADV_TRY_INVERT = booleanPreferencesKey("adv_try_invert") defaultsTo false
        val ADV_TRY_DOWNSCALE = booleanPreferencesKey("adv_try_downscale") defaultsTo false
        val ADV_MIN_LINE_COUNT = intPreferencesKey("adv_min_line_count") defaultsTo 2
        val ADV_BINARIZER = intPreferencesKey("adv_binarizer").enumDefaultsTo(Binarizer::fromIndex)
        val ADV_DOWNSCALE_FACTOR = intPreferencesKey("adv_downscale_factor") defaultsTo 3
        val ADV_DOWNSCALE_THRESHOLD = intPreferencesKey("adv_downscale_threshold") defaultsTo 500
        val ADV_TEXT_MODE = intPreferencesKey("adv_text_mode").enumDefaultsTo(TextMode::fromIndex)

        val SAVE_SCAN = booleanPreferencesKey("save_scan") defaultsTo false
        val SAVE_SCAN_PATH = stringPreferencesKey("save_scan_path") defaultsTo ""
        val SAVE_SCAN_CROP_MODE =
            intPreferencesKey("save_scan_crop_mode") enumDefaultsTo CropMode::fromIndex
        val SAVE_SCAN_QUALITY = intPreferencesKey("save_scan_quality") defaultsTo 70
        val SAVE_SCAN_FILE_PATTERN =
            stringPreferencesKey("save_scan_file_pattern") defaultsTo "scan_{TIMESTAMP}"
        val SAVE_SCAN_IMAGE_FORMAT =
            intPreferencesKey("save_scan_filetype") enumDefaultsTo ScanImageFormat::fromIndex

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
    // Use prefs array as key to ensure stable flow across recompositions
    return remember(*prefs) { getPreferences(*prefs) }.collectAsStateWithLifecycle(null)
}

@Composable
fun <T> Context.getPreferenceStateDefault(
    pref: PreferenceStore.Preference<T>,
    initial: T = pref.defaultValue
): State<T> {
    // Use pref.key to ensure stable flow across recompositions
    val flow = remember(pref.key) { getPreference(pref) }
    return flow.collectAsState(initial)
}

@Composable
fun <T> Context.getPreferenceState(pref: PreferenceStore.Preference<T>): State<T?> {
    // Use pref.key to ensure stable flow across recompositions
    val flow = remember(pref.key) { getPreference(pref) }
    return flow.collectAsState(null)
}

@Composable
fun <T> Context.getPreferenceStateBlocking(pref: PreferenceStore.Preference<T>): State<T> {
    // Use pref.key to ensure stable flow and initial value across recompositions
    val flow = remember(pref.key) { getPreference(pref) }
    val initial = remember(pref.key) { runBlocking { flow.first() } }
    return flow.collectAsState(initial)
}

@Composable
fun <T> rememberPreference(
    pref: PreferenceStore.Preference<T>,
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = context.getPreferenceStateBlocking(pref)

    // Use pref.key as remember key to ensure stable MutableState instance
    return remember(pref.key) {
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

    // Use pref.key as remember key to ensure stable MutableState instance
    return remember(pref.key) {
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
    val intState = context.getPreferenceStateBlocking(pref)

    // Use pref.key as remember key to ensure stable MutableState instance
    // Using intState.value would recreate the object on every value change (race condition bug)
    return remember(pref.key) {
        object : MutableState<E> {
            override var value: E
                get() = pref.fromOrdinal(intState.value)
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