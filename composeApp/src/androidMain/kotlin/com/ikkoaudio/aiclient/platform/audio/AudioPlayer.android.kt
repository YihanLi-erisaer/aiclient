package com.ikkoaudio.aiclient.platform.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

actual class PlatformAudioPlayer : AudioPlayer {

    private var audioTrack: AudioTrack? = null

    override suspend fun play(audioData: ByteArray) = withContext(Dispatchers.IO) {
        // Try MediaRecorder-compatible format (m4a) first - write to temp file and play via MediaPlayer
        val tempFile = File.createTempFile("audio_", ".pcm").apply { deleteOnExit() }
        FileOutputStream(tempFile).use { it.write(audioData) }

        // Assume PCM 16-bit 16kHz mono (common TTS format) or try raw playback
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(audioData.size))
            .build()

        audioTrack = track
        track.play()
        var offset = 0
        while (offset < audioData.size) {
            val written = track.write(audioData, offset, (audioData.size - offset).coerceAtMost(bufferSize))
            if (written <= 0) break
            offset += written
        }
        track.flush()
        delay((audioData.size * 1000L) / (sampleRate * 2)) // Approximate play duration
        track.stop()
        track.release()
        audioTrack = null
        tempFile.delete()
        Unit
    }

    override fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
