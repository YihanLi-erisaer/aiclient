package com.ikkoaudio.aiclient.asr

/**
 * Platform [LocalAsrEngine], or null when not available (browser targets).
 */
expect object LocalAsrProvider {
    fun get(): LocalAsrEngine?
}
