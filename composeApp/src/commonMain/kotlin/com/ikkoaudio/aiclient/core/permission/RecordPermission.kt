package com.ikkoaudio.aiclient.core.permission

/**
 * Requests RECORD_AUDIO permission if needed. On Android, shows the system permission dialog.
 * On other platforms, calls onResult(true) immediately.
 * When permission is granted, onResult(true) is invoked. When denied, onResult(false).
 */
expect fun requestRecordPermissionIfNeeded(onResult: (Boolean) -> Unit)
