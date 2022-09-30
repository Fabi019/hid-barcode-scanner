package dev.fabik.bluetoothhid.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PrefKeys {
    data class Pref<T>(
        val key: Preferences.Key<T>,
        val default: T
    )

    // Connection
    val AUTO_CONNECT = Pref(booleanPreferencesKey("auto_connect"), false)
    val DISABLE_WARNINGS = Pref(booleanPreferencesKey("disable_warnings"), false)

    // Appearance
    val DYNAMIC_THEME = Pref(booleanPreferencesKey("dynamic_theme"), false)

    // Scanner
    val SCAN_FREQUENCY = Pref(intPreferencesKey("scan_freq"), 2) // Normal
    val SCAN_RESOLUTION = Pref(intPreferencesKey("scan_res"), 0) // SD
    val FRONT_CAMERA = Pref(booleanPreferencesKey("front_camera"), false)
    val RESTRICT_AREA = Pref(booleanPreferencesKey("restrict_area"), true)
    val AUTO_SEND = Pref(booleanPreferencesKey("auto_send"), true)
    val EXTRA_KEYS = Pref(intPreferencesKey("auto_send"), 0) // None
    val PLAY_SOUND = Pref(booleanPreferencesKey("play_sound"), false)
    val RAW_VALUE = Pref(booleanPreferencesKey("raw_value"), true)
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

suspend fun <T> Context.setPreference(pref: PrefKeys.Pref<T>, value: T) {
    dataStore.edit {
        it[pref.key] = value
    }
}

fun <T> Context.getPreference(pref: PrefKeys.Pref<T>): Flow<T> {
    return dataStore.data.map {
        it[pref.key] ?: pref.default
    }
}

@Composable
fun <T> Context.getPreferenceState(pref: PrefKeys.Pref<T>, initial: T): State<T> {
    return getPreference(pref).collectAsState(initial)
}

@Composable
fun <T> Context.getPreferenceState(pref: PrefKeys.Pref<T>): State<T?> {
    return getPreference(pref).collectAsState(null)
}