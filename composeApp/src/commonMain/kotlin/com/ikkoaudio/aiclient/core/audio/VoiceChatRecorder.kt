package com.ikkoaudio.aiclient.core.audio

import kotlinx.coroutines.CoroutineScope

/**
 * Continuous microphone capture with end-of-utterance detection (WebRTC VAD on Android).
 * Each completed utterance is delivered as a WAV file (RIFF + PCM16 mono).
 */
expect class PlatformVoiceChatRecorder() {
    /**
     * Starts capture until [stop]. For each VAD-detected utterance, invokes [onUtteranceWav] with WAV bytes.
     */
    fun start(scope: CoroutineScope, onUtteranceWav: suspend (ByteArray) -> Unit)

    /** Stops capture and releases resources; waits until the worker has finished. */
    suspend fun stop()
}
