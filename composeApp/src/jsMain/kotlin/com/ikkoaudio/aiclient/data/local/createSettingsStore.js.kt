package com.ikkoaudio.aiclient.data.local

actual fun createSettingsStore(): SettingsStore = InMemorySettingsStore()
