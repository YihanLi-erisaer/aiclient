package com.ikkoaudio.aiclient.platform.audio

actual class PlatformAudioRecorder : AudioRecorder {

    override fun startRecording() {
        println("Audio recording not fully implemented on Wasm target")
    }

    override suspend fun stopRecording(): ByteArray = ByteArray(0)
}
