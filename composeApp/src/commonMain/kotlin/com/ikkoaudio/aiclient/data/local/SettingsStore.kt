package com.ikkoaudio.aiclient.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic settings store.
 * Uses DataStore on Android, localStorage/simple store on JS.
 */
interface SettingsStore {
    fun getApiBaseUrl(): Flow<String>
    suspend fun setApiBaseUrl(url: String)
    fun getMemoryId(): Flow<String?>
    suspend fun setMemoryId(id: String?)
}
