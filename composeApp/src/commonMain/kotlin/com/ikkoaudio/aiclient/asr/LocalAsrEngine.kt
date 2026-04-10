package com.ikkoaudio.aiclient.asr

/**
 * Optional on-device ASR (sherpa-onnx on Android).
 * JS/Wasm targets do not ship an engine; [LocalAsrProvider.get] returns null there.
 */
interface LocalAsrEngine {
    /** True when a model is loaded and [transcribeWav] can be attempted. */
    val isReady: Boolean

    /**
     * Transcribe a WAV container (same bytes as from the recorder).
     * Implementations decode WAV, run inference, and map outputs to text.
     */
    suspend fun transcribeWav(wavBytes: ByteArray): Result<String>
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
