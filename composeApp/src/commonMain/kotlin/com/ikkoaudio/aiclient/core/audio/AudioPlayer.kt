package com.ikkoaudio.aiclient.core.audio

/**
 * Platform-specific audio playback.
 * Plays PCM/audio bytes through the device speaker.
 */
interface AudioPlayer {
    suspend fun play(audioData: ByteArray, format: PcmAudioFormat = PcmAudioFormat.DefaultTts)
    fun stop()
}

/**
 * Same pipeline as HTTP TTS: if [audioData] is a WAV container, strip the header and play
 * with the file's sample rate/channels; otherwise treat bytes as raw PCM with [PcmAudioFormat.DefaultTts].
 */
suspend fun AudioPlayer.playPossiblyWavOrDefaultPcm(audioData: ByteArray) {
    if (audioData.isEmpty()) return
    WavPcmExtractor.tryExtract(audioData)?.let { play(it.pcm, it.format) } ?: play(audioData)
}

expect class PlatformAudioPlayer() : AudioPlayer
