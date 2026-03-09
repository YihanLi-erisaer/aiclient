package com.ikkoaudio.aiclient.data.remote.impl

import com.ikkoaudio.aiclient.data.remote.api.AiApi
import com.ikkoaudio.aiclient.data.remote.dto.ChatResponse
import com.ikkoaudio.aiclient.data.remote.dto.ModelItem
import com.ikkoaudio.aiclient.data.remote.dto.ModelsResponse
import com.ikkoaudio.aiclient.data.remote.dto.TranscribeResponse
import com.ikkoaudio.aiclient.domain.model.LlmModel
import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class KtorAiApi(
    private val client: HttpClient,
    private val logger: Logger
) : AiApi {

    override suspend fun transcribeAudio(baseUrl: String, fileBytes: ByteArray, fileName: String): Result<String> {
        return runCatching {
            val response: HttpResponse = client.post("$baseUrl/api/asr/transcribe") {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                    })
                }))
            }
            val body = response.bodyAsText()
            logger.d { "ASR transcribe response: $body" }
            runCatching {
                Json.decodeFromString<TranscribeResponse>(body).text ?: body
            }.getOrElse { body }
        }.onFailure { logger.e { "ASR transcribe failed: ${it.message}" } }
    }

    override suspend fun chat(baseUrl: String, memoryId: String?, message: String): Result<String> {
        return runCatching {
            val url = buildString {
                append("$baseUrl/api/llm/chat")
                memoryId?.let { append("?memoryId=$it") }
            }
            val response: HttpResponse = client.post(url) {
                setBody(Json.encodeToString(JsonPrimitive(message)))
                contentType(ContentType.Application.Json)
            }
            val body = response.bodyAsText()
            logger.d { "LLM chat response: $body" }
            parseChatResponse(body)
        }.onFailure { logger.e { "LLM chat failed: ${it.message}" } }
    }

    override suspend fun chatStream(baseUrl: String, memoryId: String?, message: String): Flow<Result<String>> = callbackFlow {
        try {
            val url = buildString {
                append("$baseUrl/api/llm/chat_stream")
                memoryId?.let { append("?memoryId=$it") }
            }
            client.preparePost(url) {
                setBody(Json.encodeToString(JsonPrimitive(message)))
                contentType(ContentType.Application.Json)
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val chunk = channel.readUTF8Line() ?: break
                    if (chunk.startsWith("data:")) {
                        val data = chunk.removePrefix("data:").trim()
                        if (data != "[DONE]" && data.isNotBlank()) {
                            trySend(Result.success(data))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e { "LLM chat stream failed: ${e.message}" }
            trySend(Result.failure(e))
        }
        awaitClose { }
    }

    override suspend fun getModels(baseUrl: String): Result<List<LlmModel>> {
        return runCatching {
            val response: HttpResponse = client.get("$baseUrl/api/llm/models")
            val body = response.bodyAsText()
            logger.d { "Models response: $body" }
            val parsed = Json.decodeFromString<ModelsResponse>(body)
            val models = parsed.models?.map { ModelItem(it.name, it.modified_at) }
                ?: emptyList()
            models.map { LlmModel(it.name, it.modified_at) }
        }.onFailure { logger.e { "Get models failed: ${it.message}" } }
    }

    override suspend fun imageChat(
        baseUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        userMessage: String
    ): Result<String> {
        return runCatching {
            val response: HttpResponse = client.post("$baseUrl/api/llm/image") {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                    })
                    append("userMessage", userMessage)
                }))
            }
            val body = response.bodyAsText()
            logger.d { "LLM image response: $body" }
            parseChatResponse(body)
        }.onFailure { logger.e { "LLM image failed: ${it.message}" } }
    }

    override suspend fun imageChatStream(
        baseUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        userMessage: String
    ): Flow<Result<String>> = callbackFlow {
        try {
            client.preparePost("$baseUrl/api/llm/image_stream") {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                    })
                    append("userMessage", userMessage)
                }))
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val chunk = channel.readUTF8Line() ?: break
                    if (chunk.startsWith("data:") && !chunk.contains("[DONE]")) {
                        val data = chunk.removePrefix("data:").trim()
                        if (data.isNotBlank()) trySend(Result.success(data))
                    }
                }
            }
        } catch (e: Exception) {
            logger.e { "LLM image stream failed: ${e.message}" }
            trySend(Result.failure(e))
        }
        awaitClose { }
    }

    override suspend fun textToSpeech(baseUrl: String, text: String): Result<ByteArray> {
        return runCatching {
            val response: HttpResponse = client.post("$baseUrl/api/tts/speak") {
                setBody(Json.encodeToString(JsonPrimitive(text)))
                contentType(ContentType.Application.Json)
            }
            val contentType = response.contentType()
            if (contentType?.contentType == "application/json") {
                val body = response.bodyAsText()
                val json = Json.parseToJsonElement(body)
                val url = json.toString().removeSurrounding("\"")
                if (url.startsWith("http")) {
                    client.get(url).body<ByteArray>()
                } else {
                    response.body<ByteArray>()
                }
            } else {
                response.body<ByteArray>()
            }
        }.onFailure { logger.e { "TTS failed: ${it.message}" } }
    }

    override suspend fun asrLlmTtsChat(
        baseUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        memoryId: String?
    ): Result<ByteArray> {
        return runCatching {
            val url = buildString {
                append("$baseUrl/api/asr_llm_tts/chat")
                memoryId?.let { append("?memoryId=$it") }
            }
            client.post(url) {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                    })
                    memoryId?.let { append("memoryId", it) }
                }))
            }.body<ByteArray>()
        }.onFailure { logger.e { "ASR_LLM_TTS chat failed: ${it.message}" } }
    }

    private fun parseChatResponse(body: String): String {
        return runCatching {
            Json.decodeFromString<ChatResponse>(body).let { resp ->
                resp.message ?: resp.content ?: resp.response ?: body
            }
        }.getOrElse { body }
    }
}
