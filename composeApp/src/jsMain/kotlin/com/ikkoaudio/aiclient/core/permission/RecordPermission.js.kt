package com.ikkoaudio.aiclient.core.permission

actual fun requestRecordPermissionIfNeeded(onResult: (Boolean) -> Unit) {
    // Web: would use MediaDevices.getUserMedia - for now assume granted
    onResult(true)
}
