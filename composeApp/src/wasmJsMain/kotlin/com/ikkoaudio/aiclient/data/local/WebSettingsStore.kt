package com.ikkoaudio.aiclient.data.local

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Persists memoryId in the browser so it survives tab close / refresh.
 */
class WebSettingsStore : SettingsStore {

    private val baseUrl = MutableStateFlow("/")

    private val memoryId = MutableStateFlow<String?>(readStoredMemoryId())

    override fun getApiBaseUrl(): Flow<String> = baseUrl

    override suspend fun setApiBaseUrl(url: String) {
        // API URL is developer-only; users cannot change it
    }

    override fun getMemoryId(): Flow<String?> = memoryId

    override suspend fun setMemoryId(id: String?) {
        memoryId.value = id
        if (id != null) {
            localStorage.setItem(MEMORY_KEY, id)
        } else {
            localStorage.removeItem(MEMORY_KEY)
        }
    }

    private fun readStoredMemoryId(): String? =
        localStorage.getItem(MEMORY_KEY)?.takeIf { it.isNotBlank() }

    companion object {
        private const val MEMORY_KEY = "aiclient.memoryId"
    }
}
