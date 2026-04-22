package com.ikkoaudio.aiclient.core.audio

import kotlinx.coroutines.CoroutineScope

/**
 * Wasm browser target: mic graph interop is not wired yet; use Android or JS for VAD voice chat.
 */
actual class PlatformVoiceChatRecorder {
    actual fun start(scope: CoroutineScope, onUtteranceWav: suspend (ByteArray) -> Unit) {
        println("Voice chat VAD recording is not implemented for Wasm yet.")
    }

    actual fun startWithFrameCallback(
        scope: CoroutineScope,
        onUtteranceWav: suspend (ByteArray) -> Unit,
        onAudioFrame: (FloatArray) -> Unit,
    ) {
        start(scope, onUtteranceWav)
    }

    actual fun pause() {}

    actual fun resume() {}

    actual suspend fun stop() {
    }
}
