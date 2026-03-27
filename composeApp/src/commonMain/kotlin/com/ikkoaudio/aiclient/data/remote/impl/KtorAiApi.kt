package com.ikkoaudio.aiclient.data.remote.impl

import com.ikkoaudio.aiclient.core.audio.PcmAudioFormat
import com.ikkoaudio.aiclient.core.audio.PcmPlayback
import com.ikkoaudio.aiclient.data.remote.api.AiApi
import com.ikkoaudio.aiclient.data.remote.dto.ChatResponse
import com.ikkoaudio.aiclient.data.remote.dto.ModelItem
import com.ikkoaudio.aiclient.data.remote.dto.ModelsResponse
import com.ikkoaudio.aiclient.data.remote.dto.TranscribeResponse
import com.ikkoaudio.aiclient.domain.model.LlmModel
import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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
                            val content = extractStreamContent(data)
                            if (content.isNotEmpty()) {
                                trySend(Result.success(content))
                            }
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
    ): Result<PcmPlayback> {
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
                    parseBinaryPcmWithOptionalMetaPrefix(bytes)
                }
            }
        }.onFailure { logger.e { "ASR_LLM_TTS chat failed: ${it.message}" } }
    }

    override suspend fun asrLlmTtsChatWebSocket(
        wsUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        memoryId: String?
    ): Result<PcmPlayback> {
        return runCatching {
            val url = wsUrl.trim()
            logger.i {
                "ASR_LLM_TTS WebSocket -> $url, bytes=${fileBytes.size}, file=$fileName"
            }
            val textChunks = mutableListOf<String>()
            val binaryChunks = mutableListOf<ByteArray>()
            var closeCode: Int? = null
            var closeReason: String? = null
            withTimeout(120_000L) {
                client.webSocket(urlString = url) {
                    // Aligned with browser reference:
                    //   ws.binaryType = "arraybuffer"  -> only affects onmessage shape (Blob vs ArrayBuffer), not send().
                    //   onopen -> await file.arrayBuffer(); ws.send(arrayBuffer)  -> one binary frame, full payload.
                    // Ktor: webSocket{} runs after HTTP 101 (= onopen). fileBytes == arrayBuffer bytes.
                    send(Frame.Binary(true, fileBytes))
                    try {
                        while (true) {
                            when (val frame = incoming.receive()) {
                                is Frame.Binary -> binaryChunks.add(frame.readBytes())
                                is Frame.Text -> textChunks.add(frame.readText())
                                is Frame.Ping -> send(Frame.Pong(frame.readBytes()))
                                is Frame.Close -> {
                                    val payload = frame.readBytes()
                                    if (payload.size >= 2) {
                                        closeCode =
                                            ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                                    }
                                    if (payload.size > 2) {
                                        closeReason = payload.decodeToString(2, payload.size)
                                    }
                                    break
                                }
                                else -> {}
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        logger.d { "ASR_LLM_TTS WebSocket incoming closed (no more frames): ${e.message}" }
                    }
                }
            }
            if (textChunks.isEmpty() && binaryChunks.isEmpty()) {
                val detail = buildString {
                    append("no Text/Binary frames before close")
                    if (closeCode != null) append("; close code=").append(closeCode)
                    if (!closeReason.isNullOrBlank()) append(" reason=").append(closeReason)
                }
                logger.e { "ASR_LLM_TTS WebSocket empty response: $detail" }
                throw IllegalStateException(
                    "WebSocket voice chat returned empty response ($detail). " +
                        "Handshake likely succeeded but the server sent no Text/Binary before closing — " +
                        "check backend sends PCM (or audio_meta) before disconnecting; also verify server logs."
                )
            }
            logger.i {
                "ASR_LLM_TTS WebSocket merged textFrames=${textChunks.size}, binFrames=${binaryChunks.size}"
            }
            val merged = mergeWsResponse(textChunks, binaryChunks)
            logger.i { "ASR_LLM_TTS WebSocket merged bytes=${merged.size}" }
            parseBinaryPcmWithOptionalMetaPrefix(merged)
        }.onFailure { logger.e { "ASR_LLM_TTS WebSocket failed: ${it.message}" } }
    }

    override suspend fun checkVoiceChatWebSocketHandshake(wsUrl: String): Result<String> {
        return runCatching {
            val url = wsUrl.trim()
            logger.i { "WS handshake probe -> $url" }
            client.webSocket(urlString = url) {
                close(
                    CloseReason(
                        code = CloseReason.Codes.NORMAL,
                        message = "handshake_probe"
                    )
                )
            }
            "HTTP 101 Switching Protocols"
        }.onFailure { logger.e { "WS handshake probe failed: ${it.message}" } }
    }

    private fun mergeWsResponse(textParts: List<String>, binaryParts: List<ByteArray>): ByteArray {
        val text = textParts.joinToString("\n").trim()
        if (text.isEmpty()) {
            return binaryParts.fold(ByteArray(0)) { acc, b -> acc + b }
        }
        if (!text.startsWith("{")) {
            val textBytes = text.encodeToByteArray()
            val tail = binaryParts.fold(ByteArray(0)) { acc, b -> acc + b }
            return if (tail.isEmpty()) textBytes else textBytes + "\n".encodeToByteArray() + tail
        }
        val head = text.encodeToByteArray()
        val nl = "\n".encodeToByteArray()
        val tail = binaryParts.fold(ByteArray(0)) { acc, b -> acc + b }
        return head + nl + tail
    }

    /**
     * Raw body: optional first line `{"type":"audio_meta",...}` then newline, remainder is PCM_S16LE.
     * If there is no prefix, assumes [PcmAudioFormat.VoiceChatPcm] (e.g. 22050 Hz mono).
     */
    private fun parseBinaryPcmWithOptionalMetaPrefix(bytes: ByteArray): PcmPlayback {
        if (bytes.isEmpty() || bytes[0] != '{'.code.toByte()) {
            return PcmPlayback(bytes, PcmAudioFormat.VoiceChatPcm)
        }
        val nl = bytes.indexOf('\n'.code.toByte())
        if (nl <= 0) {
            return PcmPlayback(bytes, PcmAudioFormat.VoiceChatPcm)
        }
        val line = bytes.decodeToString(0, nl).trimEnd('\r')
        if (!line.startsWith("{")) {
            return PcmPlayback(bytes, PcmAudioFormat.VoiceChatPcm)
        }
        return runCatching {
            val obj = Json.parseToJsonElement(line).jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "audio_meta") {
                return PcmPlayback(bytes, PcmAudioFormat.VoiceChatPcm)
            }
            val pcm = bytes.copyOfRange(nl + 1, bytes.size)
            PcmPlayback(pcm, pcmFormatFromAudioMeta(obj))
        }.getOrElse { PcmPlayback(bytes, PcmAudioFormat.VoiceChatPcm) }
    }

    private fun pcmFormatFromAudioMeta(obj: JsonObject): PcmAudioFormat {
        val sr = jsonInt(obj, "sampleRate", 22050)
        val ch = jsonInt(obj, "channels", 1)
        val fmt = obj["format"]?.jsonPrimitive?.content ?: "PCM_S16LE"
        return PcmAudioFormat(sampleRate = sr, channels = ch, format = fmt)
    }

    private fun jsonInt(obj: JsonObject, key: String, default: Int): Int {
        val prim = obj[key]?.jsonPrimitive ?: return default
        val content = prim.content
        content.toIntOrNull()?.let { return it }
        content.toDoubleOrNull()?.toInt()?.let { return it }
        return default
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun resolveAudioFromText(baseUrl: String, rawBody: String): PcmPlayback {
        val trimmed = rawBody.trim().trim('"')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            logger.i { "ASR_LLM_TTS downloading audio from absolute url: $trimmed" }
            val bytes = client.get(trimmed).body<ByteArray>()
            return PcmPlayback(bytes, PcmAudioFormat.DefaultTts)
        }
        if (trimmed.startsWith("/")) {
            val full = "$baseUrl$trimmed"
            logger.i { "ASR_LLM_TTS downloading audio from relative url: $full" }
            val bytes = client.get(full).body<ByteArray>()
            return PcmPlayback(bytes, PcmAudioFormat.DefaultTts)
        }
        if (trimmed.startsWith("{")) {
            val json = Json.parseToJsonElement(trimmed).jsonObject
            if (json["type"]?.jsonPrimitive?.content == "audio_meta") {
                val b64 = listOf("audio", "pcm", "pcmBase64", "audio_base64")
                    .mapNotNull { key ->
                        json[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    }
                    .firstOrNull()
                if (b64 != null) {
                    val pcm = Base64.decode(b64)
                    logger.i { "ASR_LLM_TTS decoded base64 PCM bytes=${pcm.size}" }
                    return PcmPlayback(pcm, pcmFormatFromAudioMeta(json))
                }
            }
            val candidate = listOf("url", "audioUrl", "audio_url")
                .mapNotNull { key ->
                    runCatching { json[key]?.jsonPrimitive?.content }.getOrNull()
                }
                .firstOrNull()
                ?.trim()
            if (!candidate.isNullOrBlank()) {
                val full = if (candidate.startsWith("http")) candidate else "$baseUrl${if (candidate.startsWith("/")) "" else "/"}$candidate"
                logger.i { "ASR_LLM_TTS downloading audio from json url: $full" }
                val bytes = client.get(full).body<ByteArray>()
                return PcmPlayback(bytes, PcmAudioFormat.DefaultTts)
            }
        }
        throw IllegalStateException("ASR_LLM_TTS did not return audio or audio url: ${trimmed.take(160)}")
    }

    /**
     * Extracts text content from SSE stream data. Handles both plain text and JSON
     * (OpenAI-style: {"choices":[{"delta":{"content":"..."}}]}). Preserves newlines
     * that may be in the content.
     */
    private fun extractStreamContent(data: String): String {
        if (!data.startsWith("{")) return data
        return runCatching {
            val root = Json.parseToJsonElement(data).jsonObject
            val content = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: root["content"]?.jsonPrimitive?.content
            content ?: data
        }.getOrElse { data }
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
