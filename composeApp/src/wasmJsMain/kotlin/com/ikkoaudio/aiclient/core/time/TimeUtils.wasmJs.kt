package com.ikkoaudio.aiclient.core.time

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

@OptIn(ExperimentalWasmJsInterop::class)
actual fun currentTimeMillis(): Long = js("Date.now()")
