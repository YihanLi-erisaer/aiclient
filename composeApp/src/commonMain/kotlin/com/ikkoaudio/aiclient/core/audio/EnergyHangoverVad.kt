package com.ikkoaudio.aiclient.core.audio

/**
 * Frame-wise VAD without native WebRTC (used on JS/Wasm; Android may use [com.konovalov.vad.webrtc.VadWebRTC] instead).
 * PCM16 little-endian mono; each frame is [frameSamples] samples (e.g. 320 @ 16 kHz = 20 ms).
 *
 * Mirrors the usual “speech duration / silence duration” behaviour: speech must hold for [minSpeechMs],
 * then end of utterance is declared after [silenceMs] of quiet frames (RMS below [energyThreshold]).
 */
class EnergyHangoverVad(
    private val sampleRate: Int = 16_000,
    private val frameSamples: Int = 320,
    /** RMS on int16 samples; raise in noisy environments. */
    private val energyThreshold: Double = 1200.0,
    private val minSpeechMs: Int = 50,
    private val silenceMs: Int = 300,
) {
    val frameBytes: Int = frameSamples * 2

    private val minSpeechFrames = (minSpeechMs * sampleRate / 1000 / frameSamples).coerceAtLeast(1)
    private val silenceFrames = (silenceMs * sampleRate / 1000 / frameSamples).coerceAtLeast(1)

    private var loudStreak = 0
    private var quietStreak = 0
    private var utteranceConfirmed = false

    enum class FrameLabel {
        /** Outside an utterance; do not append this frame. */
        NO_SPEECH,

        /** Inside an utterance; append this frame to the WAV buffer. */
        SPEECH,

        /** Utterance just ended (silence exceeded); do not append; caller should flush buffer. */
        END_UTTERANCE,
    }

    fun reset() {
        loudStreak = 0
        quietStreak = 0
        utteranceConfirmed = false
    }

    fun processFramePcm16LeMono(frame: ByteArray): FrameLabel {
        require(frame.size == frameBytes) { "expected $frameBytes bytes, got ${frame.size}" }
        val loud = rmsPcm16Le(frame) >= energyThreshold
        if (loud) {
            quietStreak = 0
            loudStreak++
            if (!utteranceConfirmed && loudStreak >= minSpeechFrames) {
                utteranceConfirmed = true
            }
            return if (utteranceConfirmed) FrameLabel.SPEECH else FrameLabel.NO_SPEECH
        }
        loudStreak = 0
        if (utteranceConfirmed) {
            quietStreak++
            if (quietStreak >= silenceFrames) {
                utteranceConfirmed = false
                quietStreak = 0
                return FrameLabel.END_UTTERANCE
            }
            return FrameLabel.SPEECH
        }
        return FrameLabel.NO_SPEECH
    }

    companion object {
        fun rmsPcm16Le(frame: ByteArray): Double {
            var sumSq = 0.0
            var i = 0
            while (i < frame.size) {
                val lo = frame[i].toInt() and 0xFF
                val hi = frame[i + 1].toInt() and 0xFF
                var s = lo or (hi shl 8)
                if (s >= 0x8000) s -= 0x10000
                val d = s.toDouble()
                sumSq += d * d
                i += 2
            }
            val n = frame.size / 2
            return kotlin.math.sqrt(sumSq / n)
        }
    }
}
