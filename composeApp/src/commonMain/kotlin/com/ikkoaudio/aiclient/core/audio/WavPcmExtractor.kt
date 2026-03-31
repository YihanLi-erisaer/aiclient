package com.ikkoaudio.aiclient.core.audio

/**
 * If [bytes] is a WAV (RIFF/WAVE) with PCM (format 1) or float (format 3) in the data chunk,
 * returns raw sample bytes and format. Otherwise null.
 */
object WavPcmExtractor {

    fun tryExtract(bytes: ByteArray): PcmPlayback? {
        if (bytes.size < 12 || !isRiffWave(bytes)) return null
        var offset = 12
        var formatTag = 0
        var channels = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var dataChunk: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val id = bytes.decodeToString(offset, offset + 4)
            val chunkSize = readLeU32(bytes, offset + 4).toLong() and 0xFFFFFFFFL
            val size = chunkSize.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val dataOffset = offset + 8
            if (dataOffset > bytes.size || dataOffset + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    if (size >= 16) {
                        formatTag = readLeU16(bytes, dataOffset)
                        channels = readLeU16(bytes, dataOffset + 2).coerceIn(1, 32)
                        sampleRate =
                            readLeU32(bytes, dataOffset + 4).toInt().coerceIn(1, Int.MAX_VALUE)
                        bitsPerSample = readLeU16(bytes, dataOffset + 14)
                    }
                }
                "data" -> {
                    dataChunk = bytes.copyOfRange(dataOffset, dataOffset + size)
                }
            }
            val padded = size + (size % 2)
            offset = dataOffset + padded
        }
        val pcm = dataChunk ?: return null
        val formatStr = when (formatTag) {
            1 -> when (bitsPerSample) {
                16 -> "PCM_S16LE"
                else -> return null
            }
            3 -> when (bitsPerSample) {
                32 -> "PCM_F32LE"
                else -> return null
            }
            else -> return null
        }
        val fmt = PcmAudioFormat(sampleRate = sampleRate, channels = channels, format = formatStr)
        val trimmed = trimToFrameBoundary(pcm, fmt)
        return PcmPlayback(trimmed, fmt)
    }

    private fun isRiffWave(b: ByteArray): Boolean {
        if (b.size < 12) return false
        if (b[0] != 'R'.code.toByte() || b[1] != 'I'.code.toByte() ||
            b[2] != 'F'.code.toByte() || b[3] != 'F'.code.toByte()
        ) {
            return false
        }
        return b[8] == 'W'.code.toByte() && b[9] == 'A'.code.toByte() &&
            b[10] == 'V'.code.toByte() && b[11] == 'E'.code.toByte()
    }

    private fun readLeU16(b: ByteArray, o: Int): Int {
        require(o + 2 <= b.size)
        return (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLeU32(b: ByteArray, o: Int): Long {
        require(o + 4 <= b.size)
        return (
            (b[o].toLong() and 0xFF) or
                ((b[o + 1].toLong() and 0xFF) shl 8) or
                ((b[o + 2].toLong() and 0xFF) shl 16) or
                ((b[o + 3].toLong() and 0xFF) shl 24)
            ) and 0xFFFFFFFFL
    }

    private fun trimToFrameBoundary(pcm: ByteArray, fmt: PcmAudioFormat): ByteArray {
        val bpf = fmt.pcmBytesPerFrame()
        if (bpf <= 0 || pcm.isEmpty() || pcm.size % bpf == 0) return pcm
        val n = (pcm.size / bpf) * bpf
        return pcm.copyOf(n)
    }
}
