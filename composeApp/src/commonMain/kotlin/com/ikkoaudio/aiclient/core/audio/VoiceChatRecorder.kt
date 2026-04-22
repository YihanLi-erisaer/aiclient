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

    /**
     * Like [start] but also invokes [onAudioFrame] for every PCM frame read from the
     * microphone (mono float samples, normalized −1..1, at 16 kHz on Android).
     * Use this to feed a streaming ASR engine in real-time.
     * On platforms without streaming ASR (JS / Wasm), this simply delegates to [start].
     */
    fun startWithFrameCallback(
        scope: CoroutineScope,
        onUtteranceWav: suspend (ByteArray) -> Unit,
        onAudioFrame: (FloatArray) -> Unit,
    )

    /**
     * Temporarily suspends VAD processing and utterance delivery.
     * The microphone stays open but incoming audio is discarded.
     * Call [resume] to continue.
     */
    fun pause()

    /** Resumes VAD processing after a [pause]. */
    fun resume()

    /** Stops capture and releases resources; waits until the worker has finished. */
    suspend fun stop()
}
