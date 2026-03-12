package com.ikkoaudio.aiclient.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemorySettingsStore : SettingsStore {

    // Use relative URL in browser so webpack proxy can forward /api/* and avoid CORS.
    private val baseUrl = MutableStateFlow("http://192.168.100.137:8080")
    private val memoryId = MutableStateFlow<String?>(null)

    override fun getApiBaseUrl(): Flow<String> = baseUrl

    override suspend fun setApiBaseUrl(url: String) {
        baseUrl.value = url
    }

    override fun getMemoryId(): Flow<String?> = memoryId

    override suspend fun setMemoryId(id: String?) {
        memoryId.value = id
    }
}
