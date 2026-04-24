package com.ikkoaudio.aiclient.asr

private var cached: LocalAsrEngine? = null

actual object LocalAsrProvider {
    actual fun get(): LocalAsrEngine? {
        if (cached == null) {
            cached = JsSherpaOnnxLocalAsrEngine()
        }
        return cached
    }
}
