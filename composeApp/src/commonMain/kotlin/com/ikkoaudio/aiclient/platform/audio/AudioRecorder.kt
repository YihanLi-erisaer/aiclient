package com.ikkoaudio.aiclient.platform.audio

/**
 * Platform-specific audio recording.
 * Records from the device microphone and provides audio bytes.
 */
interface AudioRecorder {
    fun startRecording()
    suspend fun stopRecording(): ByteArray
}

expect class PlatformAudioRecorder() : AudioRecorder
