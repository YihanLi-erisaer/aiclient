package com.ikkoaudio.aiclient.data.local

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.ikkoaudio.aiclient.di.getAppContext

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

actual fun createSettingsStore(): SettingsStore {
    val context = getAppContext() as? Context
        ?: throw IllegalStateException("App context not initialized. Call initAppContext() in Application.onCreate()")
    return DataStoreSettings(context.settingsDataStore)
}
