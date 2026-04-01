package com.ikkoaudio.aiclient.di

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.websocket.WebSockets

/**
 * Installs the WebSockets plugin for voice chat and handshake probes.
 *
 * **Do not set [WebSockets.maxFrameSize] here:** the Android [OkHttp] engine throws
 * `"Max frame size switch is not supported in OkHttp engine."` — use default limits.
 * Voice chat: outbound Binary (8 KiB) + Text "END"; inbound Binary = WAV (same as TTS) + Text "[DONE]" when complete.
 */
fun HttpClientConfig<*>.installAppWebSockets() {
    install(WebSockets)
}
