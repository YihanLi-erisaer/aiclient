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
        val bytesPerFrame = 2 * channels
        if (audioData.size < bytesPerFrame) return

        val frameCount = audioData.size / bytesPerFrame
        val audioContext = createAudioContext(sampleRate)
        val buffer = audioContext.createBuffer(channels, frameCount, sampleRate.toDouble())
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
        val source = audioContext.createBufferSource()
        source.buffer = buffer
        source.connect(audioContext.destination)
        source.start()
        delay((frameCount * 1000L / sampleRate) + 50L)
    }

    override fun stop() {}
}
