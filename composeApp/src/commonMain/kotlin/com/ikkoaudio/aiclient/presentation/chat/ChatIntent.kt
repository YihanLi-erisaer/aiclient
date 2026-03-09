package com.ikkoaudio.aiclient.presentation.chat

 sealed class ChatIntent {
    data class SendMessage(val text: String) : ChatIntent()
    data class UpdateInput(val text: String) : ChatIntent()
    object LoadModels : ChatIntent()
    data class SelectModel(val model: String) : ChatIntent()
    data class SetApiBaseUrl(val url: String) : ChatIntent()
    data class SetMemoryId(val id: String?) : ChatIntent()
    data class SendAudioFile(val bytes: ByteArray, val fileName: String) : ChatIntent()
    data class SendImage(val bytes: ByteArray, val fileName: String, val message: String) : ChatIntent()
    object StartRecording : ChatIntent()
    object StopRecording : ChatIntent()
    object StartVoiceChat : ChatIntent()  // Record -> ASR+LLM+TTS -> Play
    object StopVoiceChat : ChatIntent()
    object TextToSpeech : ChatIntent()
    object ClearError : ChatIntent()
    object OpenDrawer : ChatIntent()
    object CloseDrawer : ChatIntent()
    data class SelectPage(val page: AppPage) : ChatIntent()
}
