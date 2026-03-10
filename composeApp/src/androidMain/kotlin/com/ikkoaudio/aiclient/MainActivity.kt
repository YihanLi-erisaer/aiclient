package com.ikkoaudio.aiclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.ikkoaudio.aiclient.core.permission.RecordPermissionRequester
import com.ikkoaudio.aiclient.di.initAppContext

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        RecordPermissionRequester.pendingCallback?.invoke(isGranted)
        RecordPermissionRequester.pendingCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAppContext(this)

        RecordPermissionRequester.requestRecordAudio = { callback ->
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                PackageManager.PERMISSION_GRANTED -> callback(true)
                else -> {
                    RecordPermissionRequester.pendingCallback = callback
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}