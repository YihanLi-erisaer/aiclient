package com.ikkoaudio.aiclient.di

import com.ikkoaudio.aiclient.data.local.createSettingsStore
import com.ikkoaudio.aiclient.data.remote.impl.KtorAiApi
import com.ikkoaudio.aiclient.data.repository.AiRepository
import com.ikkoaudio.aiclient.feature.chat.ChatViewModel
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import kotlinx.coroutines.CoroutineScope

object AppModule {

    private val logger = Logger(
        config = loggerConfigInit(
            platformLogWriter(),
            minSeverity = Severity.Verbose
        ),
        tag = "AiClient"
    )

    fun createChatViewModel(scope: CoroutineScope): ChatViewModel {
        logger.i { "Kermit initialized. Creating ChatViewModel." }
        val settingsStore = createSettingsStore()
        val httpClient = createHttpClient()
        val api = KtorAiApi(httpClient, logger)
        val repository = AiRepository(api, settingsStore, logger)
        return ChatViewModel(repository, settingsStore, logger, scope)
    }
}
