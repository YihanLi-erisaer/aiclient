package com.ikkoaudio.aiclient.core.audio

import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

actual class PlatformAudioRecorder : AudioRecorder {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun startRecording() {
        outputFile = File.createTempFile("record_", ".m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }
    }

    override suspend fun stopRecording(): ByteArray = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val recorder = mediaRecorder
            val file = outputFile
            if (recorder != null && file != null) {
                try {
                    recorder.stop()
                    recorder.release()
                    val bytes = file.readBytes()
                    file.delete()
                    cont.resume(bytes)
                } catch (e: Exception) {
                    cont.resume(ByteArray(0))
                }
            } else {
                cont.resume(ByteArray(0))
            }
            mediaRecorder = null
            outputFile = null
        }
    }
}
