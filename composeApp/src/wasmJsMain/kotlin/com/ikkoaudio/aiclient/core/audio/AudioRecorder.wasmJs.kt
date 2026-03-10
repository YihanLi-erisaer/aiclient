package com.ikkoaudio.aiclient.core.audio

actual class PlatformAudioRecorder : AudioRecorder {

    override fun startRecording() {
        println("Audio recording not fully implemented on Wasm target")
    }

    override suspend fun stopRecording(): ByteArray = ByteArray(0)
}
