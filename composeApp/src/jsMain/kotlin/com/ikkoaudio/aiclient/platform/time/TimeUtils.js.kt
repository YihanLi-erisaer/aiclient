package com.ikkoaudio.aiclient.platform.time

actual fun currentTimeMillis(): Long = kotlin.js.Date().getTime().toLong()
