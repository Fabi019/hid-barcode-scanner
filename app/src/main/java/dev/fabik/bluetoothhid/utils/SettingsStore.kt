package dev.fabik.bluetoothhid.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SettingKeys {
    val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val Context.autoConnect: Flow<Boolean>
    get() = dataStore.data.map {
        it[SettingKeys.AUTO_CONNECT] ?: false
    }

suspend fun Context.setAutoConnect(value: Boolean) {
    dataStore.edit {
        it[SettingKeys.AUTO_CONNECT] = value
    }
}

val Context.dynamicTheme: Flow<Boolean>
    get() = dataStore.data.map {
        it[SettingKeys.DYNAMIC_THEME] ?: false
    }

suspend fun Context.setDynamicTheme(value: Boolean) {
    dataStore.edit {
        it[SettingKeys.DYNAMIC_THEME] = value
    }
}

