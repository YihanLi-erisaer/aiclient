package com.ikkoaudio.aiclient.core.audio

/**
 * Platform-specific audio playback.
 * Plays PCM/audio bytes through the device speaker.
 */
interface AudioPlayer {
    suspend fun play(audioData: ByteArray)
    fun stop()
}

expect class PlatformAudioPlayer() : AudioPlayer
