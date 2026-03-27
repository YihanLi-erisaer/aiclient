package com.ikkoaudio.aiclient.di

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.websocket.WebSockets

/**
 * Installs the WebSockets plugin for voice chat and handshake probes.
 *
 * **Do not set [WebSockets.maxFrameSize] here:** the Android [OkHttp] engine throws
 * `"Max frame size switch is not supported in OkHttp engine."` — use default limits.
 * Voice chat uses a single [io.ktor.websocket.Frame.Binary] send; typical recordings stay under defaults.
 */
fun HttpClientConfig<*>.installAppWebSockets() {
    install(WebSockets)
}
