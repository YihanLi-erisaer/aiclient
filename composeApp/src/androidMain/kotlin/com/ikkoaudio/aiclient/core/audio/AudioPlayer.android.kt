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
        val isFloat = format.isPcmFloatLe()
        val encoding = if (isFloat) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
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
        if (isFloat) {
            val totalFloats = audioData.size / 4
            val floats = FloatArray(totalFloats)
            var o = 0
            var fi = 0
            while (fi < totalFloats) {
                val b0 = audioData[o].toInt() and 0xFF
                val b1 = audioData[o + 1].toInt() and 0xFF
                val b2 = audioData[o + 2].toInt() and 0xFF
                val b3 = audioData[o + 3].toInt() and 0xFF
                val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                floats[fi] = Float.fromBits(bits)
                o += 4
                fi++
            }
            var floatOffset = 0
            while (floatOffset < floats.size) {
                val writtenFrames = track.write(
                    floats,
                    floatOffset,
                    floats.size - floatOffset,
                    AudioTrack.WRITE_BLOCKING,
                )
                if (writtenFrames <= 0) break
                floatOffset += writtenFrames * format.channels
            }
        } else {
            var offset = 0
            while (offset < audioData.size) {
                val written =
                    track.write(audioData, offset, (audioData.size - offset).coerceAtMost(bufferSize))
                if (written <= 0) break
                offset += written
            }
        }
        track.flush()
        val bytesPerFrame = format.pcmBytesPerFrame().coerceAtLeast(1)
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
