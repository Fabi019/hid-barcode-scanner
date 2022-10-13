package dev.fabik.bluetoothhid.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object PrefKeys {
    data class Pref<T>(
        val key: Preferences.Key<T>,
        val default: T
    )

    // Connection
    val AUTO_CONNECT = Pref(booleanPreferencesKey("auto_connect"), false)
    val SHOW_UNNAMED = Pref(booleanPreferencesKey("show_unnamed"), false)
    val SHOW_STATE = Pref(booleanPreferencesKey("show_state"), false)
    val SEND_DELAY = Pref(floatPreferencesKey("send_delay"), 1f)

    // Appearance
    val THEME = Pref(intPreferencesKey("theme"), 0) // System
    val DYNAMIC_THEME = Pref(booleanPreferencesKey("dynamic_theme"), false)

    // Scanner
    val SCAN_FREQUENCY = Pref(intPreferencesKey("scan_freq"), 2) // Normal
    val SCAN_RESOLUTION = Pref(intPreferencesKey("scan_res"), 0) // SD
    val FRONT_CAMERA = Pref(booleanPreferencesKey("front_camera"), false)
    val RESTRICT_AREA = Pref(booleanPreferencesKey("restrict_area"), true)
    val FULL_INSIDE = Pref(booleanPreferencesKey("full_inside"), true)
    val OVERLAY_TYPE = Pref(intPreferencesKey("overlay_type"), 0) // Square
    val AUTO_SEND = Pref(booleanPreferencesKey("auto_send"), false)
    val EXTRA_KEYS = Pref(intPreferencesKey("extra_keys"), 0) // None
    val PLAY_SOUND = Pref(booleanPreferencesKey("play_sound"), false)
    val VIBRATE = Pref(booleanPreferencesKey("vibrate"), false)
    val RAW_VALUE = Pref(booleanPreferencesKey("raw_value"), false)
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

suspend fun <T> Context.setPreference(pref: PrefKeys.Pref<T>, value: T) {
    dataStore.edit {
        it[pref.key] = value
    }
}

fun <T> Context.getPreference(pref: PrefKeys.Pref<T>): Flow<T> {
    return dataStore.data
        .catch { e ->
            Log.e("SettingsStore", "Error reading preference", e)
            emit(preferencesOf(pref.key to pref.default))
        }.map {
            it[pref.key] ?: pref.default
        }
}

@Composable
fun <T> Context.getPreferenceState(pref: PrefKeys.Pref<T>, initial: T): State<T> {
    return remember { getPreference(pref) }.collectAsState(initial)
}

@Composable
fun <T> Context.getPreferenceState(pref: PrefKeys.Pref<T>): State<T?> {
    return remember { getPreference(pref) }.collectAsState(null)
}

@Composable
fun <T> rememberPreferenceNull(
    pref: PrefKeys.Pref<T>,
): MutableState<T?> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
    pref: PrefKeys.Pref<T>,
    initial: T = pref.default
): MutableState<T> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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