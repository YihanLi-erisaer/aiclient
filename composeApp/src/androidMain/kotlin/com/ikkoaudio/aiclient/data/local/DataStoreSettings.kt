package com.ikkoaudio.aiclient.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettings(private val dataStore: DataStore<Preferences>) : SettingsStore {

    companion object {
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val MEMORY_ID = stringPreferencesKey("memory_id")
        private const val DEFAULT_BASE_URL = "http://192.168.100.137:8080"
    }

    override fun getApiBaseUrl(): Flow<String> = dataStore.data.map { prefs ->
        prefs[API_BASE_URL] ?: DEFAULT_BASE_URL
    }

    override suspend fun setApiBaseUrl(url: String) {
        dataStore.edit { it[API_BASE_URL] = url }
    }

    override fun getMemoryId(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[MEMORY_ID]
    }

    override suspend fun setMemoryId(id: String?) {
        dataStore.edit {
            if (id != null) it[MEMORY_ID] = id else it.remove(MEMORY_ID)
        }
    }
}
