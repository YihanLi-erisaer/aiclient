package com.ikkoaudio.aiclient.feature.chat

import com.ikkoaudio.aiclient.data.repository.AiRepository
import com.ikkoaudio.aiclient.core.audio.AudioPlayer
import com.ikkoaudio.aiclient.core.audio.AudioRecorder
import com.ikkoaudio.aiclient.core.audio.PlatformAudioPlayer
import com.ikkoaudio.aiclient.core.audio.PlatformAudioRecorder
import com.ikkoaudio.aiclient.data.local.SettingsStore
import com.ikkoaudio.aiclient.core.time.currentTimeMillis
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class ChatViewModel(
    private val repository: AiRepository,
    private val settingsStore: SettingsStore,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val audioPlayer: AudioPlayer = PlatformAudioPlayer(),
    private val audioRecorder: AudioRecorder = PlatformAudioRecorder()
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var streamJob: Job? = null

    init {
        scope.launch {
            settingsStore.getApiBaseUrl().collect { url ->
                _state.update { it.copy(apiBaseUrl = url) }
            }
        }
        scope.launch {
            settingsStore.getMemoryId().collect { id ->
                _state.update { it.copy(memoryId = id) }
            }
        }
        scope.launch {
            ensureMemoryId()
        }
    }

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.UpdateInput -> _state.update { it.copy(inputText = intent.text) }
            ChatIntent.LoadModels -> loadModels()
            is ChatIntent.SelectModel -> _state.update { it.copy(selectedModel = intent.model) }
            is ChatIntent.SetMemoryId -> scope.launch {
                settingsStore.setMemoryId(intent.id)
                _state.update { it.copy(memoryId = intent.id) }
            }
            is ChatIntent.SendAudioFile -> sendAudioFile(intent.bytes, intent.fileName)
            is ChatIntent.SendImage -> sendImage(intent.bytes, intent.fileName, intent.message)
            ChatIntent.StartRecording -> startRecording()
            ChatIntent.StopRecording -> stopRecordingAndTranscribe()
            ChatIntent.StartVoiceChat -> startVoiceChat()
            ChatIntent.StopVoiceChat -> stopVoiceChat()
            ChatIntent.TextToSpeech -> textToSpeech()
            ChatIntent.ClearError -> _state.update { it.copy(error = null) }
            is ChatIntent.SetError -> _state.update { it.copy(error = intent.message) }
            ChatIntent.OpenDrawer -> _state.update { it.copy(drawerOpen = true) }
            ChatIntent.CloseDrawer -> _state.update { it.copy(drawerOpen = false) }
            is ChatIntent.SelectPage -> _state.update {
                it.copy(selectedPage = intent.page, drawerOpen = false)
            }
        }
    }

    private fun sendMessage(text: String) {
        if (text.isBlank()) return
        val msg = text.trim()
        logger.i { "sendMessage triggered, textLength=${msg.length}, page=${_state.value.selectedPage}" }
        _state.update {
            it.copy(
                inputText = "",
                messages = it.messages + ChatMessageUi("u-${currentTimeMillis()}", "user", msg),
                isLoading = true,
                error = null
            )
        }
        streamJob?.cancel()
        streamJob = scope.launch {
            val baseUrl = _state.value.apiBaseUrl
            val memoryId = ensureMemoryId()
            val assistantId = "a-${currentTimeMillis()}"
            logger.i { "LLM stream start, baseUrl=$baseUrl, memoryId=$memoryId" }
            _state.update { it.copy(isStreaming = true, isLoading = false) }
            _state.update {
                it.copy(messages = it.messages + ChatMessageUi(assistantId, "assistant", "", true))
            }
            var fullResponse = ""
            repository.chatStream(baseUrl, memoryId, msg).collect { result ->
                result.onSuccess { chunk ->
                    if (fullResponse.isNotEmpty() && chunk.isNotEmpty()) {
                        when {
                            shouldAddNewlineBetweenChunks(fullResponse, chunk) -> fullResponse += "\n"
                            shouldAddSpaceBetweenChunks(fullResponse, chunk) -> fullResponse += " "
                        }
                    }
                    fullResponse += chunk
                    _state.update { state ->
                        state.copy(
                            messages = state.messages.map {
                                if (it.id == assistantId) it.copy(content = fullResponse, isStreaming = true)
                                else it
                            }
                        )
                    }
                }.onFailure { err ->
                    logger.e { "Stream error: ${err.message}" }
                    _state.update { it.copy(error = err.message, isStreaming = false) }
                }
            }
            _state.update { state ->
                state.copy(
                    messages = state.messages.map {
                        if (it.id == assistantId) it.copy(content = fullResponse, isStreaming = false)
                        else it
                    },
                    isStreaming = false
                )
            }
        }
    }

    /**
     * Returns true when a newline should be inserted between two chunks during streaming.
     * SSE readUTF8Line() consumes newlines between events, so we restore them when the
     * chunk appears to be a new line (table row, header, code block, etc.).
     */
    private fun shouldAddNewlineBetweenChunks(prev: String, next: String): Boolean {
        val nextTrim = next.trimStart()
        return when {
            // Table row boundary: prev ends with | and next starts with | (handles single "|" causing "||")
            prev.endsWith("|") && (next.startsWith("|") || nextTrim.startsWith("|")) -> true
            // Table row or separator: next looks like table content
            (next.startsWith("|") && next.length >= 2) || (nextTrim.startsWith("|") && nextTrim.length >= 2) -> true
            // New section after table: prev is full row (has multiple |), next starts new paragraph (e.g. 总结:)
            prev.endsWith("|") && prev.indexOf('|', 1) >= 0 &&
                (next.startsWith("总") || next.trimStart().startsWith("总")) -> true
            // Header, code block, horizontal rule
            next.startsWith("#") -> true
            nextTrim.startsWith("```") -> true
            next.matches(Regex("^[-*_]{3,}\\s*$")) -> true
            // List items
            next.startsWith("- ") || next.startsWith("* ") -> true
            nextTrim.startsWith("- ") || nextTrim.startsWith("* ") -> true
            else -> false
        }
    }

    /**
     * Returns true when a space should be inserted between two chunks during streaming.
     * Adds space between English words but NOT in acronyms (NEON, AArch64) or URLs.
     */
    private fun shouldAddSpaceBetweenChunks(prev: String, next: String): Boolean {
        if (prev.length < 3 || next.length < 3) return false // Avoid "NE ON", "A Arch64"
        val last = prev.last()
        val first = next.first()
        val isLatinLetter = { c: Char -> c in 'a'..'z' || c in 'A'..'Z' }
        if (!isLatinLetter(last) || !isLatinLetter(first)) return false
        val inUrlContext = prev.contains("http://") || prev.contains("https://") || prev.contains("www.")
        return !inUrlContext
    }

    private fun loadModels() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getModels(_state.value.apiBaseUrl)
                .onSuccess { models ->
                    _state.update {
                        it.copy(
                            models = models,
                            selectedModel = models.firstOrNull()?.name ?: it.selectedModel,
                            isLoading = false
                        )
                    }
                }
                .onFailure { err ->
                    logger.e { "Load models failed: ${err.message}" }
                    _state.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    private fun sendAudioFile(bytes: ByteArray, fileName: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.asrLlmTtsChat(_state.value.apiBaseUrl, bytes, fileName, _state.value.memoryId)
                .onSuccess { audioBytes ->
                    _state.update { it.copy(isLoading = false) }
                    audioPlayer.play(audioBytes)
                }
                .onFailure { err ->
                    logger.e { "ASR+LLM+TTS failed: ${err.message}" }
                    _state.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    private fun sendImage(bytes: ByteArray, fileName: String, message: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.imageChat(_state.value.apiBaseUrl, bytes, fileName, message)
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            messages = it.messages + ChatMessageUi(
                                "a-${currentTimeMillis()}",
                                "assistant",
                                response
                            ),
                            isLoading = false
                        )
                    }
                }
                .onFailure { err ->
                    logger.e { "Image chat failed: ${err.message}" }
                    _state.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    private fun startRecording() {
        audioRecorder.startRecording()
        _state.update { it.copy(isRecording = true, isVoiceChat = false) }
    }

    private fun startVoiceChat() {
        audioRecorder.startRecording()
        _state.update { it.copy(isRecording = true, isVoiceChat = true) }
    }

    private fun stopVoiceChat() {
        scope.launch {
            val bytes = audioRecorder.stopRecording()
            _state.update { it.copy(isRecording = false) }
            if (bytes.isEmpty()) {
                _state.update { it.copy(error = "No audio recorded") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null) }
            val memoryId = ensureMemoryId()
            val combinedResult =
                repository.asrLlmTtsChat(_state.value.apiBaseUrl, bytes, "recording.wav", memoryId)
            if (combinedResult.isSuccess) {
                val audioBytes = combinedResult.getOrThrow()
                _state.update { it.copy(isLoading = false) }
                audioPlayer.play(audioBytes)
                return@launch
            }

            val err = combinedResult.exceptionOrNull()
            logger.e { "Voice chat failed: ${err?.message}" }
            _state.update { it.copy(error = err?.message ?: "Voice chat failed", isLoading = false) }
        }
    }

    private fun stopRecordingAndTranscribe() {
        if (_state.value.isVoiceChat) {
            stopVoiceChat()
            return
        }
        scope.launch {
            val bytes = audioRecorder.stopRecording()
            _state.update { it.copy(isRecording = false) }
            if (bytes.isEmpty()) {
                _state.update { it.copy(error = "No audio recorded") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null) }
            repository.transcribeAudio(_state.value.apiBaseUrl, bytes, "recording.wav")
                .onSuccess { text ->
                    _state.update {
                        it.copy(
                            inputText = it.inputText + text,
                            isLoading = false
                        )
                    }
                }
                .onFailure { err ->
                    logger.e { "Transcribe failed: ${err.message}" }
                    _state.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    private fun textToSpeech() {
        val text = _state.value.inputText
        if (text.isBlank()) return
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.textToSpeech(_state.value.apiBaseUrl, text)
                .onSuccess { audioBytes ->
                    _state.update { it.copy(isLoading = false) }
                    audioPlayer.play(audioBytes)
                }
                .onFailure { err ->
                    logger.e { "TTS failed: ${err.message}" }
                    _state.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    private suspend fun ensureMemoryId(): String {
        val inState = _state.value.memoryId?.trim().orEmpty()
        if (inState.isNotEmpty()) return inState

        val generated = "mem-${currentTimeMillis()}-${Random.nextInt(100000, 999999)}"
        settingsStore.setMemoryId(generated)
        _state.update { it.copy(memoryId = generated) }
        logger.i { "Generated new memoryId: $generated" }
        return generated
    }

}
