package com.ikkoaudio.aiclient.data.repository

import com.ikkoaudio.aiclient.asr.LocalAsrEngine
import com.ikkoaudio.aiclient.asr.LocalAsrPreferences
import com.ikkoaudio.aiclient.asr.LocalAsrProvider
import com.ikkoaudio.aiclient.asr.StreamingAsrSession
import com.ikkoaudio.aiclient.core.audio.PcmPlayback
import com.ikkoaudio.aiclient.data.remote.api.AiApi
import com.ikkoaudio.aiclient.domain.model.LlmModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AiRepository(
    private val api: AiApi,
    private val settingsStore: com.ikkoaudio.aiclient.data.local.SettingsStore,
    private val logger: Logger,
    private val localAsr: LocalAsrEngine? = LocalAsrProvider.get(),
) {

    val isLocalAsrAvailable: Boolean get() = localAsr?.isReady == true

    val isStreamingAsrAvailable: Boolean get() = localAsr?.supportsStreaming == true

    fun createStreamingSession(): StreamingAsrSession? = localAsr?.createStreamingSession()

    suspend fun transcribeLocal(wavBytes: ByteArray): Result<String> {
        val engine = localAsr ?: return Result.failure(
            IllegalStateException("Local ASR not available on this platform")
        )
        if (!engine.isReady) return Result.failure(
            IllegalStateException("Local ASR model not loaded")
        )
        return engine.transcribeWav(wavBytes)
    }

    suspend fun transcribeAudio(baseUrl: String, fileBytes: ByteArray, fileName: String): Result<String> {
        val local = localAsr
        if (LocalAsrPreferences.requireLocalAsr) {
            val engine = local ?: return Result.failure(
                IllegalStateException("Local ASR is required but this platform has no sherpa-onnx engine (e.g. use Android).")
            )
            if (!engine.isReady) {
                return Result.failure(
                    IllegalStateException(
                        "Local ASR is required but the sherpa-onnx Zipformer is not loaded. Add assets/models/sherpa-asr/ (encoder/decoder/joiner + tokens, or Zipformer CTC model.onnx + tokens)."
                    )
                )
            }
            return engine.transcribeWav(fileBytes)
        }
        if (LocalAsrPreferences.preferLocalWhenReady && local != null && local.isReady) {
            return local.transcribeWav(fileBytes)
        }
        return api.transcribeAudio(baseUrl, fileBytes, fileName)
    }

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

    suspend fun asrLlmTtsChat(baseUrl: String, fileBytes: ByteArray, fileName: String, memoryId: String?): Result<PcmPlayback> =
        api.asrLlmTtsChat(baseUrl, fileBytes, fileName, memoryId)

    suspend fun asrLlmTtsChatWebSocket(
        wsUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        memoryId: String?,
        onInterimText: ((String) -> Unit)? = null,
    ): Result<ByteArray> =
        api.asrLlmTtsChatWebSocket(wsUrl, fileBytes, fileName, memoryId, onInterimText)

    suspend fun textLlmTtsChatWebSocket(
        wsUrl: String,
        text: String,
        memoryId: String?,
        onInterimText: ((String) -> Unit)? = null,
    ): Result<ByteArray> =
        api.textLlmTtsChatWebSocket(wsUrl, text, memoryId, onInterimText)

    suspend fun checkVoiceChatWebSocketHandshake(wsUrl: String): Result<String> =
        api.checkVoiceChatWebSocketHandshake(wsUrl)
}
