package com.ikkoaudio.aiclient.platform.audio

actual class PlatformAudioPlayer : AudioPlayer {

    override suspend fun play(audioData: ByteArray) {
        println("Audio playback not fully implemented on Wasm target. Data size: ${audioData.size} bytes")
    }

    override fun stop() {}
}
