package com.ikkoaudio.aiclient.platform.time

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

@OptIn(ExperimentalWasmJsInterop::class)
actual fun currentTimeMillis(): Long = js("Date.now()")
