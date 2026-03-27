package com.ikkoaudio.aiclient.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

actual class PlatformAudioPlayer : AudioPlayer {

    private var audioTrack: AudioTrack? = null

    override suspend fun play(audioData: ByteArray, format: PcmAudioFormat) = withContext(Dispatchers.IO) {
        val sampleRate = format.sampleRate
        val channelConfig = when (format.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> error("Unsupported channel count: ${format.channels}")
        }
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
        val bytesPerFrame = 2 * format.channels
        val frames = audioData.size / bytesPerFrame
        delay((frames * 1000L) / sampleRate)
        track.stop()
        track.release()
        audioTrack = null
        Unit
    }

    override fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
