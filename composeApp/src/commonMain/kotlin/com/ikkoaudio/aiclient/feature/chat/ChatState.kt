package com.ikkoaudio.aiclient.feature.chat

enum class AppPage {
    VOICECHAT,
    LLM,
    TTS,
    ASR
}

data class ChatState(
    val messages: List<ChatMessageUi> = emptyList(),
    val inputText: String = "",
    val outputText: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val memoryId: String? = null,
    val apiBaseUrl: String = Defaults.API_BASE_URL,
    val voiceChatWebSocketUrl: String = Defaults.VOICE_CHAT_WS_URL,
    val voiceChatWebSocketHandshake: VoiceChatWebSocketHandshakeState = VoiceChatWebSocketHandshakeState.Unknown,
    val isRecording: Boolean = false,
    val isVoiceChat: Boolean = false,  // true = full ASR->LLM->TTS pipeline
    val useLocalAsr: Boolean = false,
    val localAsrAvailable: Boolean = false,
    val streamingAsrAvailable: Boolean = false,
    /** Partial recognition text from the streaming ASR session (updated in real-time). */
    val partialAsrText: String = "",
    /** Server status text during voice chat WebSocket (e.g. “thinking”), cleared when final audio arrives. */
    val voiceChatInterimText: String? = null,
    val selectedPage: AppPage = AppPage.VOICECHAT,
    val settingsScreenVisible: Boolean = false,
    /** When set, [LlmChatBody] scrolls to this message then clears (sidebar history tap). */
    val scrollToMessageId: String? = null
) {
    /**
     * Single place to change default HTTP / WS endpoints. [SettingsStore.getApiBaseUrl] should use
     * [API_BASE_URL] so [ChatViewModel] state matches after the settings flow emits.
     */
    object Defaults {
        const val API_BASE_URL = "http://192.168.100.115:8080"
        const val VOICE_CHAT_WS_URL = "ws://192.168.100.115:8080/ws/chat"
    }
}

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)
