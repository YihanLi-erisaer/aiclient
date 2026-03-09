package com.ikkoaudio.aiclient

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform