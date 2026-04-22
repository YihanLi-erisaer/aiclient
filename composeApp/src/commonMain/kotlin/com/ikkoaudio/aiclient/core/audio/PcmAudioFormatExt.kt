package com.ikkoaudio.aiclient.core.audio

/** Bytes per interleaved frame (all channels) for playback. */
fun PcmAudioFormat.pcmBytesPerFrame(): Int {
    val ch = channels.coerceIn(1, 32)
    return when {
        format.contains("F32", ignoreCase = true) ||
            format.contains("FLOAT", ignoreCase = true) -> 4 * ch
        format.contains("S16BE", ignoreCase = true) ||
            format.contains("S16LE", ignoreCase = true) ||
            format.contains("PCM_S16", ignoreCase = true) -> 2 * ch
        else -> 2 * ch
    }
}

fun PcmAudioFormat.isPcmFloatLe(): Boolean =
    format.contains("F32", ignoreCase = true) || format.contains("FLOAT",
        ignoreCase = true)
