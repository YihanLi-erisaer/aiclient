package com.ikkoaudio.aiclient.platform.audio

actual class PlatformAudioPlayer : AudioPlayer {

    override suspend fun play(audioData: ByteArray) {
        // Web: Would use Web Audio API / AudioContext to decode and play
        // For now, create silent placeholder - full implementation requires JS interop
        println("Audio playback not fully implemented on JS target. Data size: ${audioData.size} bytes")
    }

    override fun stop() {}
}
