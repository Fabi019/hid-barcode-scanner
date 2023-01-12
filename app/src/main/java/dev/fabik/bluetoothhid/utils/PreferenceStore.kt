package dev.fabik.bluetoothhid.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val Context.dataStore by preferencesDataStore("settings")

open class PreferenceStore {
    data class Preference<T>(
        val key: Preferences.Key<T>,
        val defaultValue: T
    )

    companion object {
        private infix fun <T> Preferences.Key<T>.defaultsTo(value: T) =
            Preference(this, value)

        // Connection
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect") defaultsTo false
        val SHOW_UNNAMED = booleanPreferencesKey("show_unnamed") defaultsTo false
        val SEND_DELAY = floatPreferencesKey("send_delay") defaultsTo 1f
        val KEYBOARD_LAYOUT = intPreferencesKey("keyboard_layout") defaultsTo 0

        // Appearance
        val THEME = intPreferencesKey("theme") defaultsTo 0 // System
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme") defaultsTo false

        // Scanner
        val SCAN_FREQUENCY = intPreferencesKey("scan_freq") defaultsTo 2 // Normal
        val CODE_TYPES = stringSetPreferencesKey("code_types") defaultsTo setOf()
        val SCAN_RESOLUTION = intPreferencesKey("scan_res") defaultsTo 0 // SD
        val AUTO_FOCUS = booleanPreferencesKey("auto_focus") defaultsTo true
        val FRONT_CAMERA = booleanPreferencesKey("front_camera") defaultsTo false
        val RESTRICT_AREA = booleanPreferencesKey("restrict_area") defaultsTo true
        val FULL_INSIDE = booleanPreferencesKey("full_inside") defaultsTo true
        val OVERLAY_TYPE = intPreferencesKey("overlay_type") defaultsTo 0 // Square
        val AUTO_SEND = booleanPreferencesKey("auto_send") defaultsTo false
        val EXTRA_KEYS = intPreferencesKey("extra_keys") defaultsTo 0 // None
        val PLAY_SOUND = booleanPreferencesKey("play_sound") defaultsTo false
        val VIBRATE = booleanPreferencesKey("vibrate") defaultsTo false
        val RAW_VALUE = booleanPreferencesKey("raw_value") defaultsTo false
        val HIGHLIGHT_TYPE = intPreferencesKey("highlight") defaultsTo 0 // Box
    }
}

suspend fun <T> Context.setPreference(pref: PreferenceStore.Preference<T>, value: T) {
    dataStore.edit {
        it[pref.key] = value
    }
}

fun <T> Context.getPreference(pref: PreferenceStore.Preference<T>): Flow<T> = dataStore.data
    .catch { e ->
        Log.e("PreferenceStore", "Error reading preference", e)
        emit(preferencesOf(pref.key to pref.defaultValue))
    }.map {
        it[pref.key] ?: pref.defaultValue
    }

@Composable
fun <T> Context.getPreferenceState(pref: PreferenceStore.Preference<T>, initial: T): State<T> {
    return remember { getPreference(pref) }.collectAsState(initial)
}

@Composable
fun <T> Context.getPreferenceState(pref: PreferenceStore.Preference<T>): State<T?> {
    return remember { getPreference(pref) }.collectAsState(null)
}

@Composable
fun <T> Context.getPreferenceStateBlocking(pref: PreferenceStore.Preference<T>): State<T> {
    val flow = remember { getPreference(pref) }
    return flow.collectAsState(runBlocking { flow.first() })
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
fun <T> rememberPreferenceDefault(
    pref: PreferenceStore.Preference<T>,
    initial: T = pref.defaultValue
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = context.getPreferenceState(pref, initial)

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
