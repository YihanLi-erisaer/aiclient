package com.ikkoaudio.aiclient.asr

/**
 * Optional on-device ASR (sherpa-onnx on Android).
 * JS/Wasm targets do not ship an engine; [LocalAsrProvider.get] returns null there.
 */
interface LocalAsrEngine {
    /** True when a model is loaded and [transcribeWav] can be attempted. */
    val isReady: Boolean

    /** True when an online (streaming) recognizer is available for real-time partial results. */
    val supportsStreaming: Boolean get() = false

    /**
     * Transcribe a WAV container (same bytes as from the recorder).
     * Implementations decode WAV, run inference, and map outputs to text.
     */
    suspend fun transcribeWav(wavBytes: ByteArray): Result<String>

    /**
     * Create a streaming ASR session for real-time recognition.
     * Returns null if streaming is not supported (offline model only).
     */
    fun createStreamingSession(): StreamingAsrSession? = null
}

/**
 * A live streaming ASR session backed by an online recognizer.
 * Feed audio samples incrementally and read partial text as it becomes available.
 */
interface StreamingAsrSession {
    /** Feed PCM mono float samples (normalized −1..1) at the given sample rate. */
    fun feedSamples(samples: FloatArray, sampleRate: Int)

    /** Current partial transcription text. */
    fun getCurrentText(): String

    /** Reset internal state for the next utterance (call after reading the final text). */
    fun reset()

    /** Release underlying native resources. */
    fun release()
}

/**
 * When true (default), [com.ikkoaudio.aiclient.data.repository.AiRepository.transcribeAudio]
 * uses [LocalAsrEngine] if [LocalAsrEngine.isReady]. Set to false to always use the backend API.
 */
object LocalAsrPreferences {
    /**
     * When true and [LocalAsrEngine.isReady], [AiRepository.transcribeAudio] uses the local sherpa-onnx engine.
     */
    var preferLocalWhenReady: Boolean = true

    /**
     * When true, ASR never calls the remote `/api/asr/transcribe` API.
     * Fails if no local engine or the sherpa-onnx model is not in assets (see Android `models/sherpa-asr/`).
     */
    var requireLocalAsr: Boolean = false
}
