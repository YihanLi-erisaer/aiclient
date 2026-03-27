package com.ikkoaudio.aiclient.core.audio

/**
 * Describes raw PCM playback parameters (e.g. L16 from ASR→LLM→TTS).
 */
data class PcmAudioFormat(
    val sampleRate: Int,
    val channels: Int = 1,
    val format: String = "PCM_S16LE",
) {
    companion object {
        /** Legacy TTS / generic 16 kHz mono PCM. */
        val DefaultTts = PcmAudioFormat(sampleRate = 16000, channels = 1)

        /**
         * When the server returns only raw L16 bytes (no header), voice chat uses this
         * unless an [audio_meta] line is parsed first.
         */
        val VoiceChatPcm = PcmAudioFormat(sampleRate = 22050, channels = 1, format = "PCM_S16LE")
    }
}

/**
 * PCM payload plus format for [AudioPlayer].
 */
data class PcmPlayback(
    val pcm: ByteArray,
    val format: PcmAudioFormat,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as PcmPlayback
        if (!pcm.contentEquals(other.pcm)) return false
        if (format != other.format) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pcm.contentHashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}
