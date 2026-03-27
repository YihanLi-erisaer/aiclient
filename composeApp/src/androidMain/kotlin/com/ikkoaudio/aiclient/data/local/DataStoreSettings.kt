package com.ikkoaudio.aiclient.data.local

import com.ikkoaudio.aiclient.feature.chat.ChatState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DataStoreSettings(private val dataStore: DataStore<Preferences>) : SettingsStore {

    companion object {
        private val MEMORY_ID = stringPreferencesKey("memory_id")
    }

    override fun getApiBaseUrl(): Flow<String> = flowOf(ChatState.Defaults.API_BASE_URL)

    override suspend fun setApiBaseUrl(url: String) {
        // API URL is developer-only; users cannot change it
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
