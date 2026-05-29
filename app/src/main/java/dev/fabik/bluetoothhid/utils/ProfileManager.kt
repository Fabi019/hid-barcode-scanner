package dev.fabik.bluetoothhid.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Separate store for profile metadata — never part of any profile's settings
private val Context.globalDataStore: DataStore<Preferences> by preferencesDataStore("global")

object ProfileManager {
    const val DEFAULT = "Default"
    private val ACTIVE_KEY = stringPreferencesKey("active_profile")
    private val NAMES_KEY = stringSetPreferencesKey("profile_names")

    private val storeCache = HashMap<String, DataStore<Preferences>>()

    private val _activeProfile = MutableStateFlow(DEFAULT)
    val activeProfile: StateFlow<String> = _activeProfile.asStateFlow()

    private fun sanitize(name: String) = name.lowercase().replace(Regex("[^a-z0-9_]"), "_")

    @Synchronized
    fun getOrCreateStore(context: Context, profileName: String): DataStore<Preferences> {
        return storeCache.getOrPut(profileName) {
            val appContext = context.applicationContext
            // Default profile reuses the existing settings.preferences_pb file (backward compat)
            val fileName = if (profileName == DEFAULT) "settings" else "settings_${sanitize(profileName)}"
            PreferenceDataStoreFactory.create {
                appContext.filesDir.resolve("datastore/$fileName.preferences_pb")
            }
        }
    }

    fun currentStore(context: Context): DataStore<Preferences> =
        getOrCreateStore(context, _activeProfile.value)

    fun activeStoreFlow(context: Context): Flow<DataStore<Preferences>> =
        _activeProfile.map { getOrCreateStore(context, it) }

    suspend fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.globalDataStore.data.first()
        val savedProfile = prefs[ACTIVE_KEY] ?: DEFAULT
        // Ensure the profiles list always contains at least Default
        if (prefs[NAMES_KEY] == null) {
            appContext.globalDataStore.edit { it[NAMES_KEY] = setOf(DEFAULT) }
        }
        _activeProfile.value = savedProfile
    }

    fun getProfilesFlow(context: Context): Flow<Set<String>> =
        context.applicationContext.globalDataStore.data.map {
            it[NAMES_KEY] ?: setOf(DEFAULT)
        }

    suspend fun switchProfile(context: Context, name: String) {
        _activeProfile.value = name
        context.applicationContext.globalDataStore.edit { it[ACTIVE_KEY] = name }
    }

    suspend fun createProfile(context: Context, name: String) {
        context.applicationContext.globalDataStore.edit { prefs ->
            prefs[NAMES_KEY] = (prefs[NAMES_KEY] ?: setOf(DEFAULT)) + name
        }
    }

    suspend fun deleteProfile(context: Context, name: String) {
        if (name == DEFAULT || name == _activeProfile.value) return
        context.applicationContext.globalDataStore.edit { prefs ->
            prefs[NAMES_KEY] = (prefs[NAMES_KEY] ?: setOf(DEFAULT)) - name
        }
        synchronized(this) { storeCache.remove(name) }
        val appContext = context.applicationContext
        val fileName = "settings_${sanitize(name)}"
        appContext.filesDir.resolve("datastore/$fileName.preferences_pb").delete()
        appContext.filesDir.resolve("datastore/$fileName.preferences_pb.tmp").delete()
    }
}
