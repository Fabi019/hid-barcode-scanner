package dev.fabik.bluetoothhid.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Separate store for profile metadata — never part of any profile's settings
private val Context.globalDataStore: DataStore<Preferences> by preferencesDataStore("global")

object ProfileManager {
    const val DEFAULT = "Default"
    private const val DEFAULT_ID = "default"

    private val ACTIVE_KEY = stringPreferencesKey("active_profile")
    // JSON object: { displayName: fileId, ... }  e.g. {"Default":"default","Work":"550e8400-..."}
    private val PROFILES_KEY = stringPreferencesKey("profiles_map")

    // Keyed by file ID (UUID or "default"), not by display name — avoids collisions
    private val storeCache = ConcurrentHashMap<String, DataStore<Preferences>>()
    // In-memory map: displayName → fileId, populated in initialize()
    private val profileMap = ConcurrentHashMap<String, String>()

    private val _activeProfile = MutableStateFlow(DEFAULT)
    val activeProfile: StateFlow<String> = _activeProfile.asStateFlow()

    private fun parseProfileMap(json: String): Map<String, String> =
        runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(mapOf(DEFAULT to DEFAULT_ID))

    private fun serializeProfileMap(map: Map<String, String>): String =
        JSONObject(map).toString()

    @Synchronized
    private fun getOrCreateStoreById(context: Context, fileId: String): DataStore<Preferences> {
        return storeCache.getOrPut(fileId) {
            val appContext = context.applicationContext
            // Default profile reuses the existing "settings" store (backward compat)
            val fileName = if (fileId == DEFAULT_ID) "settings" else "settings_$fileId"
            PreferenceDataStoreFactory.create {
                appContext.preferencesDataStoreFile(fileName)
            }
        }
    }

    fun currentStore(context: Context): DataStore<Preferences> {
        val id = profileMap[_activeProfile.value] ?: DEFAULT_ID
        return getOrCreateStoreById(context, id)
    }

    fun activeStoreFlow(context: Context): Flow<DataStore<Preferences>> =
        _activeProfile.map { name ->
            val id = profileMap[name] ?: DEFAULT_ID
            getOrCreateStoreById(context, id)
        }

    suspend fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.globalDataStore.data.first()

        val map = prefs[PROFILES_KEY]?.let { parseProfileMap(it) } ?: run {
            val defaultMap = mapOf(DEFAULT to DEFAULT_ID)
            appContext.globalDataStore.edit { it[PROFILES_KEY] = serializeProfileMap(defaultMap) }
            defaultMap
        }
        profileMap.clear()
        profileMap.putAll(map)

        val savedProfile = prefs[ACTIVE_KEY] ?: DEFAULT
        _activeProfile.value = if (map.containsKey(savedProfile)) savedProfile else DEFAULT
    }

    fun getProfilesFlow(context: Context): Flow<Set<String>> =
        context.applicationContext.globalDataStore.data.map { prefs ->
            prefs[PROFILES_KEY]?.let { parseProfileMap(it).keys.toSet() } ?: setOf(DEFAULT)
        }

    suspend fun switchProfile(context: Context, name: String) {
        _activeProfile.value = name
        context.applicationContext.globalDataStore.edit { it[ACTIVE_KEY] = name }
    }

    suspend fun createProfile(context: Context, name: String) {
        val id = UUID.randomUUID().toString()
        profileMap[name] = id
        context.applicationContext.globalDataStore.edit { prefs ->
            val current = prefs[PROFILES_KEY]?.let { parseProfileMap(it) } ?: mapOf(DEFAULT to DEFAULT_ID)
            prefs[PROFILES_KEY] = serializeProfileMap(current + (name to id))
        }
    }

    suspend fun deleteProfile(context: Context, name: String) {
        if (name == DEFAULT || name == _activeProfile.value) return
        val id = profileMap.remove(name) ?: return
        context.applicationContext.globalDataStore.edit { prefs ->
            val current = prefs[PROFILES_KEY]?.let { parseProfileMap(it) } ?: mapOf(DEFAULT to DEFAULT_ID)
            prefs[PROFILES_KEY] = serializeProfileMap(current - name)
        }
        storeCache.remove(id)
        val appContext = context.applicationContext
        val fileName = "settings_$id"
        appContext.filesDir.resolve("datastore/$fileName.preferences_pb").delete()
        appContext.filesDir.resolve("datastore/$fileName.preferences_pb.tmp").delete()
    }
}
