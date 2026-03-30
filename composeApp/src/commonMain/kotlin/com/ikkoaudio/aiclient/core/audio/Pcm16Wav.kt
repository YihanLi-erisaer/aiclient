package com.ikkoaudio.aiclient.core.audio

/** Wraps raw PCM S16LE mono in a minimal RIFF WAV container. */
fun pcm16MonoLeToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val chunkSize = pcmData.size
    val riffChunkSize = 36 + chunkSize
    val out = ByteArray(44 + chunkSize)
    var o = 0
    fun w4(s: String) {
        for (c in s) out[o++] = c.code.toByte()
    }
    fun u32le(v: Int) {
        out[o++] = (v and 0xFF).toByte()
        out[o++] = ((v shr 8) and 0xFF).toByte()
        out[o++] = ((v shr 16) and 0xFF).toByte()
        out[o++] = ((v shr 24) and 0xFF).toByte()
    }
    fun u16le(v: Int) {
        out[o++] = (v and 0xFF).toByte()
        out[o++] = ((v shr 8) and 0xFF).toByte()
    }
    w4("RIFF")
    u32le(riffChunkSize)
    w4("WAVE")
    w4("fmt ")
    u32le(16)
    u16le(1)
    u16le(channels)
    u32le(sampleRate)
    u32le(byteRate)
    u16le(channels * bitsPerSample / 8)
    u16le(bitsPerSample)
    w4("data")
    u32le(chunkSize)
    pcmData.copyInto(out, o)
    return out
}

/** Linear resample mono PCM S16LE (e.g. 48 kHz mic → 16 kHz for backend). */
fun resamplePcm16MonoLinear(input: ByteArray, fromRate: Int, toRate: Int): ByteArray {
    if (fromRate == toRate || input.isEmpty()) return input
    val inSamples = input.size / 2
    if (inSamples < 2) return input
    val outSamples = kotlin.math.max(1, (inSamples.toLong() * toRate / fromRate).toInt())
    val out = ByteArray(outSamples * 2)
    fun getS16(buf: ByteArray, idx: Int): Int {
        val i = idx * 2
        val lo = buf[i].toInt() and 0xFF
        val hi = buf[i + 1].toInt() and 0xFF
        var s = lo or (hi shl 8)
        if (s >= 0x8000) s -= 0x10000
        return s
    }
    fun putS16(buf: ByteArray, idx: Int, s: Int) {
        val v = s.coerceIn(-32768, 32767)
        val i = idx * 2
        buf[i] = (v and 0xFF).toByte()
        buf[i + 1] = ((v shr 8) and 0xFF).toByte()
    }
    for (j in 0 until outSamples) {
        val srcPos = j * fromRate.toDouble() / toRate
        val i0 = srcPos.toInt().coerceIn(0, inSamples - 1)
        val i1 = (i0 + 1).coerceAtMost(inSamples - 1)
        val frac = srcPos - i0
        val s0 = getS16(input, i0)
        val s1 = getS16(input, i1)
        val s = ((1.0 - frac) * s0 + frac * s1).toInt()
        putS16(out, j, s)
    }
    return out
}
