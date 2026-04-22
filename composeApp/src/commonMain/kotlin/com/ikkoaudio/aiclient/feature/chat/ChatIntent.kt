package com.ikkoaudio.aiclient.feature.chat

sealed class ChatIntent {
    data class SendMessage(val text: String) : ChatIntent()
    data class UpdateInput(val text: String) : ChatIntent()
    data class UpdateOutput(val text: String) : ChatIntent()
    data class SendAudioFile(val bytes: ByteArray, val fileName: String) : ChatIntent()
    data class SendImage(val bytes: ByteArray, val fileName: String, val message: String) : ChatIntent()
    object StartRecording : ChatIntent()
    object StopRecording : ChatIntent()
    object StartVoiceChat : ChatIntent()  // Record -> ASR+LLM+TTS -> Play
    object StopVoiceChat : ChatIntent()
    object StartAsrListening : ChatIntent()  // Continuous VAD -> local ASR -> outputText
    /** Probe [ChatState.voiceChatWebSocketUrl] for HTTP 101 WebSocket handshake. */
    object CheckVoiceChatWebSocketHandshake : ChatIntent()
    object ToggleLocalAsr : ChatIntent()
    object TextToSpeech : ChatIntent()
    object ClearError : ChatIntent()
    data class SetError(val message: String) : ChatIntent()
    data class SelectPage(val page: AppPage) : ChatIntent()
    object OpenSettingsScreen : ChatIntent()
    object CloseSettingsScreen : ChatIntent()
    data class ScrollChatToMessage(val messageId: String) : ChatIntent()
    object ClearScrollToMessage : ChatIntent()
}
