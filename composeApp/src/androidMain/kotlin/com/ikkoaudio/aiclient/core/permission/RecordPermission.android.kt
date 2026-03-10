package com.ikkoaudio.aiclient.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ikkoaudio.aiclient.di.getAppContext

object RecordPermissionRequester {
    var requestRecordAudio: ((onResult: (Boolean) -> Unit) -> Unit)? = null
    var pendingCallback: ((Boolean) -> Unit)? = null
}

actual fun requestRecordPermissionIfNeeded(onResult: (Boolean) -> Unit) {
    val context = getAppContext() as? Context ?: run {
        onResult(false)
        return
    }
    when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
        PackageManager.PERMISSION_GRANTED -> onResult(true)
        else -> RecordPermissionRequester.requestRecordAudio?.invoke(onResult) ?: onResult(false)
    }
}
