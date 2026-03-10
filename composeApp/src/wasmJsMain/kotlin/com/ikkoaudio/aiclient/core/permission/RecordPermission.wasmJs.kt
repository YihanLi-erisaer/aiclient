package com.ikkoaudio.aiclient.core.permission

actual fun requestRecordPermissionIfNeeded(onResult: (Boolean) -> Unit) {
    onResult(true)
}
