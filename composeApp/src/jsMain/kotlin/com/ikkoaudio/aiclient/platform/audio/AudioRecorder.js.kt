package com.ikkoaudio.aiclient.platform.audio

actual class PlatformAudioRecorder : AudioRecorder {

    override fun startRecording() {
        println("Audio recording not fully implemented on JS target")
    }

    override suspend fun stopRecording(): ByteArray {
        return ByteArray(0)
    }
}
