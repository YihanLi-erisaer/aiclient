package com.ikkoaudio.aiclient.presentation.chat

import com.ikkoaudio.aiclient.data.repository.AiRepository
import com.ikkoaudio.aiclient.platform.audio.AudioPlayer
import com.ikkoaudio.aiclient.platform.audio.AudioRecorder
import com.ikkoaudio.aiclient.platform.audio.PlatformAudioPlayer
import com.ikkoaudio.aiclient.platform.audio.PlatformAudioRecorder
import com.ikkoaudio.aiclient.data.local.SettingsStore
import com.ikkoaudio.aiclient.platform.time.currentTimeMillis
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    }

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.UpdateInput -> _state.update { it.copy(inputText = intent.text) }
            ChatIntent.LoadModels -> loadModels()
            is ChatIntent.SelectModel -> _state.update { it.copy(selectedModel = intent.model) }
            is ChatIntent.SetApiBaseUrl -> scope.launch {
                settingsStore.setApiBaseUrl(intent.url)
                _state.update { it.copy(apiBaseUrl = intent.url) }
            }
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
            val memoryId = _state.value.memoryId
            val assistantId = "a-${currentTimeMillis()}"
            _state.update { it.copy(isStreaming = true, isLoading = false) }
            _state.update {
                it.copy(messages = it.messages + ChatMessageUi(assistantId, "assistant", "", true))
            }
            var fullResponse = ""
            repository.chatStream(baseUrl, memoryId, msg).collect { result ->
                result.onSuccess { chunk ->
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
            repository.asrLlmTtsChat(_state.value.apiBaseUrl, bytes, "recording.m4a", _state.value.memoryId)
                .onSuccess { audioBytes ->
                    _state.update { it.copy(isLoading = false) }
                    audioPlayer.play(audioBytes)
                }
                .onFailure { err ->
                    logger.e { "Voice chat failed: ${err.message}" }
                    _state.update { it.copy(error = err.message, isLoading = false) }
                }
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
            repository.transcribeAudio(_state.value.apiBaseUrl, bytes, "recording.m4a")
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

}
