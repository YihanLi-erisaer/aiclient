package com.ikkoaudio.aiclient.asr

import android.content.Context
import com.ikkoaudio.aiclient.di.getAppContext

private var cached: LocalAsrEngine? = null

actual object LocalAsrProvider {
    actual fun get(): LocalAsrEngine? {
        val ctx = getAppContext() as? Context ?: return null
        if (cached == null) {
            cached = AndroidSherpaOnnxLocalAsrEngine(ctx.applicationContext)
        }
        return cached
    }
}
