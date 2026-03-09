package com.ikkoaudio.aiclient.data.repository

import com.ikkoaudio.aiclient.data.remote.api.AiApi
import com.ikkoaudio.aiclient.domain.model.LlmModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AiRepository(
    private val api: AiApi,
    private val settingsStore: com.ikkoaudio.aiclient.data.local.SettingsStore,
    private val logger: Logger
) {

    suspend fun transcribeAudio(baseUrl: String, fileBytes: ByteArray, fileName: String): Result<String> =
        api.transcribeAudio(baseUrl, fileBytes, fileName)

    suspend fun chat(baseUrl: String, memoryId: String?, message: String): Result<String> =
        api.chat(baseUrl, memoryId, message)

    suspend fun chatStream(baseUrl: String, memoryId: String?, message: String): Flow<Result<String>> =
        api.chatStream(baseUrl, memoryId, message)

    suspend fun getModels(baseUrl: String): Result<List<LlmModel>> = api.getModels(baseUrl)

    suspend fun imageChat(baseUrl: String, fileBytes: ByteArray, fileName: String, userMessage: String): Result<String> =
        api.imageChat(baseUrl, fileBytes, fileName, userMessage)

    suspend fun imageChatStream(baseUrl: String, fileBytes: ByteArray, fileName: String, userMessage: String): Flow<Result<String>> =
        api.imageChatStream(baseUrl, fileBytes, fileName, userMessage)

    suspend fun textToSpeech(baseUrl: String, text: String): Result<ByteArray> =
        api.textToSpeech(baseUrl, text)

    suspend fun asrLlmTtsChat(baseUrl: String, fileBytes: ByteArray, fileName: String, memoryId: String?): Result<ByteArray> =
        api.asrLlmTtsChat(baseUrl, fileBytes, fileName, memoryId)
}
