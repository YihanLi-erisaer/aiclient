package com.ikkoaudio.aiclient.di

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.websocket.WebSockets

/**
 * Installs the WebSockets plugin for voice chat and handshake probes.
 *
 * **Do not set [WebSockets.maxFrameSize] here:** the Android [OkHttp] engine throws
 * `"Max frame size switch is not supported in OkHttp engine."` — use default limits.
 * Voice chat sends audio as multiple [io.ktor.websocket.Frame.Binary] chunks (8 KiB) plus a Text "END".
 */
fun HttpClientConfig<*>.installAppWebSockets() {
    install(WebSockets)
}
