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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KtorAiApi(
    private val client: HttpClient,
    private val logger: Logger
) : AiApi {

    private fun normalizeUrl(url: String) = url.trimEnd('/')
    private fun fileContentType(fileName: String): String = when {
        fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
        fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        else -> "application/octet-stream"
    }

    override suspend fun transcribeAudio(baseUrl: String, fileBytes: ByteArray, fileName: String): Result<String> {
        return runCatching {
            val base = normalizeUrl(baseUrl)
            logger.i { "ASR transcribe to $base/api/asr/transcribe" }
            val response: HttpResponse = client.post("$base/api/asr/transcribe") {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, fileContentType(fileName))
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
            val base = normalizeUrl(baseUrl)
            val url = buildString {
                append("$base/api/llm/chat")
                memoryId?.let { append("?memoryId=$it") }
            }
            logger.i { "LLM chat request -> $url, textLength=${message.length}" }
            val response: HttpResponse = client.post(url) {
                setBody(message)
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.e { "LLM chat non-success status=${response.status.value}, body=${errorBody.take(220)}" }
                throw IllegalStateException("LLM chat HTTP ${response.status.value}: ${errorBody.take(160)}")
            }
            val body = response.bodyAsText()
            logger.d { "LLM chat response: $body" }
            parseChatResponse(body)
        }.onFailure { logger.e { "LLM chat failed: ${it.message}" } }
    }

    override suspend fun chatStream(baseUrl: String, memoryId: String?, message: String): Flow<Result<String>> = callbackFlow {
        try {
            val base = normalizeUrl(baseUrl)
            val url = buildString {
                append("$base/api/llm/chat_stream")
                memoryId?.let { append("?memoryId=$it") }
            }
            logger.i { "LLM chat_stream request -> $url, textLength=${message.length}" }
            client.preparePost(url) {
                setBody(message)
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "text/event-stream")
            }.execute { response ->
                val contentType = response.contentType()?.toString() ?: "unknown"
                logger.i { "LLM chat_stream response status=${response.status.value}, contentType=$contentType" }
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    logger.e { "LLM chat_stream non-success status=${response.status.value}, body=${errorBody.take(220)}" }
                    trySend(Result.failure(IllegalStateException("LLM chat_stream HTTP ${response.status.value}: ${errorBody.take(160)}")))
                    close()
                    return@execute
                }
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val chunk = channel.readUTF8Line() ?: break
                    if (chunk.startsWith("data:")) {
                        val data = chunk.removePrefix("data:").trim()
                        if (data != "[DONE]" && data.isNotBlank()) {
                            trySend(Result.success(data))
                        }
                    } else if (chunk.isNotBlank()) {
                        // Fallback for non-SSE stream implementations that return plain text lines.
                        trySend(Result.success(chunk))
                    }
                }
            }
            close()
        } catch (e: Exception) {
            logger.e { "LLM chat stream failed: ${e.message}" }
            trySend(Result.failure(e))
            close()
        }
        awaitClose { }
    }

    override suspend fun getModels(baseUrl: String): Result<List<LlmModel>> {
        return runCatching {
            val base = normalizeUrl(baseUrl)
            val response: HttpResponse = client.get("$base/api/llm/models")
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
            val base = normalizeUrl(baseUrl)
            val response: HttpResponse = client.post("$base/api/llm/image") {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, fileContentType(fileName))
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
            val base = normalizeUrl(baseUrl)
            client.preparePost("$base/api/llm/image_stream") {
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, fileContentType(fileName))
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
            close()
        } catch (e: Exception) {
            logger.e { "LLM image stream failed: ${e.message}" }
            trySend(Result.failure(e))
            close(e)
        }
        awaitClose { }
    }

    override suspend fun textToSpeech(baseUrl: String, text: String): Result<ByteArray> {
        return runCatching {
            val base = normalizeUrl(baseUrl)
            logger.i { "TTS request -> $base/api/tts/speak, textLength=${text.length}" }
            val response: HttpResponse = client.post("$base/api/tts/speak") {
                setBody(text)
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.e { "TTS non-success status=${response.status.value}, body=${errorBody.take(220)}" }
                throw IllegalStateException("TTS HTTP ${response.status.value}: ${errorBody.take(160)}")
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
            val base = normalizeUrl(baseUrl)
            val url = "$base/api/asr_llm_tts/chat"
            logger.i { "ASR_LLM_TTS request -> $url, file=$fileName, bytes=${fileBytes.size}, memoryId=${memoryId ?: "null"}" }
            val response = client.post(url) {
                header(HttpHeaders.Accept, "*/*")
                setBody(MultiPartFormDataContent(formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, fileContentType(fileName))
                    })
                    append("memoryId", memoryId ?: "")
                }))
            }
            val contentType = response.contentType()?.toString() ?: "unknown"
            logger.i { "ASR_LLM_TTS response status=${response.status.value}, contentType=$contentType" }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.e { "ASR_LLM_TTS non-success status=${response.status.value}, body=${errorBody.take(220)}" }
                throw IllegalStateException("ASR_LLM_TTS HTTP ${response.status.value}: ${errorBody.take(160)}")
            }
            when {
                contentType.contains("application/json") || contentType.contains("text/plain") -> {
                    val body = response.bodyAsText()
                    logger.i { "ASR_LLM_TTS response text length=${body.length}" }
                    resolveAudioFromText(base, body)
                }
                else -> {
                    val bytes = response.body<ByteArray>()
                    logger.i { "ASR_LLM_TTS audio bytes=${bytes.size}" }
                    bytes
                }
            }
        }.onFailure { logger.e { "ASR_LLM_TTS chat failed: ${it.message}" } }
    }

    private suspend fun resolveAudioFromText(baseUrl: String, rawBody: String): ByteArray {
        val trimmed = rawBody.trim().trim('"')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            logger.i { "ASR_LLM_TTS downloading audio from absolute url: $trimmed" }
            return client.get(trimmed).body()
        }
        if (trimmed.startsWith("/")) {
            val full = "$baseUrl$trimmed"
            logger.i { "ASR_LLM_TTS downloading audio from relative url: $full" }
            return client.get(full).body()
        }
        if (trimmed.startsWith("{")) {
            val json = Json.parseToJsonElement(trimmed).jsonObject
            val candidate = listOf("url", "audioUrl", "audio_url")
                .mapNotNull { key ->
                    runCatching { json[key]?.jsonPrimitive?.content }.getOrNull()
                }
                .firstOrNull()
                ?.trim()
            if (!candidate.isNullOrBlank()) {
                val full = if (candidate.startsWith("http")) candidate else "$baseUrl${if (candidate.startsWith("/")) "" else "/"}$candidate"
                logger.i { "ASR_LLM_TTS downloading audio from json url: $full" }
                return client.get(full).body()
            }
        }
        throw IllegalStateException("ASR_LLM_TTS did not return audio or audio url: ${trimmed.take(160)}")
    }

    private fun parseChatResponse(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return body
        return runCatching {
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return body // Plain text
            Json.decodeFromString<ChatResponse>(body).let { resp ->
                resp.message ?: resp.content ?: resp.response ?: resp.text ?: body
            }
        }.getOrElse { body }
    }
}
