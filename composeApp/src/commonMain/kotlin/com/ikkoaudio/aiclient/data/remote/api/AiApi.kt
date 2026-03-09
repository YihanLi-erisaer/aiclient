package com.ikkoaudio.aiclient.data.remote.api

import com.ikkoaudio.aiclient.domain.model.LlmModel
import kotlinx.coroutines.flow.Flow

/**
 * API interface for ASR, LLM, TTS, and combined ASR_LLM_TTS services.
 */
interface AiApi {

    suspend fun transcribeAudio(baseUrl: String, fileBytes: ByteArray, fileName: String): Result<String>

    suspend fun chat(baseUrl: String, memoryId: String?, message: String): Result<String>

    suspend fun chatStream(baseUrl: String, memoryId: String?, message: String): Flow<Result<String>>

    suspend fun getModels(baseUrl: String): Result<List<LlmModel>>

    suspend fun imageChat(baseUrl: String, fileBytes: ByteArray, fileName: String, userMessage: String): Result<String>

    suspend fun imageChatStream(baseUrl: String, fileBytes: ByteArray, fileName: String, userMessage: String): Flow<Result<String>>

    suspend fun textToSpeech(baseUrl: String, text: String): Result<ByteArray>

    suspend fun asrLlmTtsChat(baseUrl: String, fileBytes: ByteArray, fileName: String, memoryId: String?): Result<ByteArray>
}
