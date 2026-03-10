package com.ikkoaudio.aiclient.feature.chat

import com.ikkoaudio.aiclient.domain.model.LlmModel

enum class AppPage {
    VOICECHAT,
    LLM,
    TTS,
    ASR
}

data class ChatState(
    val messages: List<ChatMessageUi> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val models: List<LlmModel> = emptyList(),
    val selectedModel: String? = null,
    val memoryId: String? = null,
    val apiBaseUrl: String = "http://prod-cn.your-api-server.com",
    val isRecording: Boolean = false,
    val isVoiceChat: Boolean = false,  // true = full ASR->LLM->TTS pipeline
    val selectedPage: AppPage = AppPage.VOICECHAT,
    val drawerOpen: Boolean = false
)

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)
