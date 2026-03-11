package com.ikkoaudio.aiclient.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class InMemorySettingsStore : SettingsStore {

    private val baseUrl = MutableStateFlow("http://192.168.100.183:8080")
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
