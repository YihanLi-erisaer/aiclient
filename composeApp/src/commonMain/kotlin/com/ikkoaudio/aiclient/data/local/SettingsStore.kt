package com.ikkoaudio.aiclient.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic settings store.
 * [getMemoryId] / [setMemoryId] are persisted: DataStore on Android, localStorage on JS/Wasm browser.
 */
interface SettingsStore {
    fun getApiBaseUrl(): Flow<String>
    suspend fun setApiBaseUrl(url: String)
    fun getMemoryId(): Flow<String?>
    suspend fun setMemoryId(id: String?)
}
