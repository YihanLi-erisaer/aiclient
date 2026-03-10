package com.ikkoaudio.aiclient.core.time

actual fun currentTimeMillis(): Long = kotlin.js.Date().getTime().toLong()
