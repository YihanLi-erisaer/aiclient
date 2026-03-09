package com.ikkoaudio.aiclient.di

import com.ikkoaudio.aiclient.data.local.SettingsStore
import com.ikkoaudio.aiclient.data.local.createSettingsStore
import com.ikkoaudio.aiclient.data.remote.impl.KtorAiApi
import com.ikkoaudio.aiclient.data.repository.AiRepository
import com.ikkoaudio.aiclient.presentation.chat.ChatViewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope

object AppModule {

    private val logger = Logger.withTag("AiClient")

    fun createChatViewModel(scope: CoroutineScope): ChatViewModel {
        val settingsStore = createSettingsStore()
        val httpClient = createHttpClient()
        val api = KtorAiApi(httpClient, logger)
        val repository = AiRepository(api, settingsStore, logger)
        return ChatViewModel(repository, settingsStore, logger, scope)
    }
}
