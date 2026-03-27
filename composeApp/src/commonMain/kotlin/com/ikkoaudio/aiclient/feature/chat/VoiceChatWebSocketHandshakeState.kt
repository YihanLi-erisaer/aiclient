package com.ikkoaudio.aiclient.feature.chat

/**
 * Result of probing [ChatState.voiceChatWebSocketUrl] for a successful WebSocket upgrade (HTTP 101).
 */
sealed class VoiceChatWebSocketHandshakeState {
    data object Unknown : VoiceChatWebSocketHandshakeState()
    data object Checking : VoiceChatWebSocketHandshakeState()
    /** Upgrade succeeded; [detail] is a short message (e.g. from probe). */
    data class Ok(val detail: String) : VoiceChatWebSocketHandshakeState()
    data class Failed(val message: String) : VoiceChatWebSocketHandshakeState()
}
