package com.ikkoaudio.aiclient.core.audio

import kotlinx.coroutines.delay

@Suppress("unused")
private fun createAudioContext(sampleRate: Int): dynamic {
    val sr = sampleRate
    return js("new (window['AudioContext'] || window['webkitAudioContext'])({sampleRate: sr})")
}

actual class PlatformAudioPlayer : AudioPlayer {

    override suspend fun play(audioData: ByteArray, format: PcmAudioFormat) {
        val sampleRate = format.sampleRate
        val channels = format.channels.coerceIn(1, 2)
        val isFloat = format.isPcmFloatLe()
        val isS16Be = format.format.contains("S16BE", ignoreCase = true)
        val bytesPerFrame = format.pcmBytesPerFrame().coerceAtLeast(1)
        if (audioData.size < bytesPerFrame) return

        val frameCount = audioData.size / bytesPerFrame
        val audioContext = createAudioContext(sampleRate)
        runCatching { js("(function(c){ return c.resume && c.resume(); })")(audioContext) }
        val buffer = audioContext.createBuffer(channels, frameCount, sampleRate.toDouble())
        when {
            isFloat -> fillBufferFloat32Le(buffer, audioData, channels, frameCount)
            isS16Be -> fillBufferS16Be(buffer, audioData, channels, frameCount)
            else -> fillBufferS16Le(buffer, audioData, channels, frameCount)
        }
        val source = audioContext.createBufferSource()
        source.buffer = buffer
        source.connect(audioContext.destination)
        source.start()
        delay((frameCount * 1000L / sampleRate) + 50L)
    }

    override fun stop() {}
}

private fun fillBufferS16Le(
    buffer: dynamic,
    audioData: ByteArray,
    channels: Int,
    frameCount: Int,
) {
    for (ch in 0 until channels) {
        val channelData = buffer.getChannelData(ch)
        var i = 0
        while (i < frameCount) {
            val idx = (i * channels + ch) * 2
            val lo = audioData[idx].toInt() and 0xFF
            val hi = audioData[idx + 1].toInt() and 0xFF
            var s = lo or (hi shl 8)
            if (s >= 0x8000) s -= 0x10000
            channelData[i] = s / 32768.0f
            i++
        }
    }
}

private fun fillBufferS16Be(
    buffer: dynamic,
    audioData: ByteArray,
    channels: Int,
    frameCount: Int,
) {
    for (ch in 0 until channels) {
        val channelData = buffer.getChannelData(ch)
        var i = 0
        while (i < frameCount) {
            val idx = (i * channels + ch) * 2
            val hi = audioData[idx].toInt() and 0xFF
            val lo = audioData[idx + 1].toInt() and 0xFF
            var s = (hi shl 8) or lo
            if (s >= 0x8000) s -= 0x10000
            channelData[i] = s / 32768.0f
            i++
        }
    }
}

private fun fillBufferFloat32Le(
    buffer: dynamic,
    audioData: ByteArray,
    channels: Int,
    frameCount: Int,
) {
    for (ch in 0 until channels) {
        val channelData = buffer.getChannelData(ch)
        var i = 0
        while (i < frameCount) {
            val idx = (i * channels + ch) * 4
            val b0 = audioData[idx].toInt() and 0xFF
            val b1 = audioData[idx + 1].toInt() and 0xFF
            val b2 = audioData[idx + 2].toInt() and 0xFF
            val b3 = audioData[idx + 3].toInt() and 0xFF
            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            channelData[i] = Float.fromBits(bits).coerceIn(-1f, 1f)
            i++
        }
    }
}
