package com.ikkoaudio.aiclient.core.audio

actual class PlatformAudioPlayer : AudioPlayer {

    override suspend fun play(audioData: ByteArray, format: PcmAudioFormat) {
        println(
            "PCM playback on Wasm target is not implemented yet " +
                "(${format.sampleRate} Hz, ${format.channels} ch, ${audioData.size} bytes)"
        )
    }

    override fun stop() {}
}
