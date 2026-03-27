package com.ikkoaudio.aiclient

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** True for Kotlin/JS and Wasm/JS browser targets (Skiko canvas); emoji needs [FontFamily.Resolver.preload]. */
expect val isSkikoWebTarget: Boolean