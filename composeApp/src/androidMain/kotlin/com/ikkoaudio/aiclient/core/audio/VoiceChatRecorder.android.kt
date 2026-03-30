package com.ikkoaudio.aiclient.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.ikkoaudio.aiclient.di.getAppContext
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

actual class PlatformVoiceChatRecorder {

    private val pcmSampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    /** 16-bit mono, WebRTC frame 320 samples @ 16 kHz */
    private val frameBytes = FrameSize.FRAME_SIZE_320.value * 2

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null

    actual fun start(scope: CoroutineScope, onUtteranceWav: suspend (ByteArray) -> Unit) {
        if (!running.compareAndSet(false, true)) return

        val appContext = getAppContext() as? Context
        if (appContext == null ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            running.set(false)
            return
        }

        val vadInstance = VadWebRTC(
            SampleRate.SAMPLE_RATE_16K,
            FrameSize.FRAME_SIZE_320,
            Mode.VERY_AGGRESSIVE,
            300,
            20
        )

        val minBuffer = AudioRecord.getMinBufferSize(pcmSampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) {
            running.set(false)
            vadInstance.close()
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                pcmSampleRate,
                channelConfig,
                audioFormat,
                minBuffer * 2
            ).also { it.startRecording() }
        } catch (_: SecurityException) {
            running.set(false)
            vadInstance.close()
            return
        }
        audioRecord = recorder

        worker = Thread(
            {
                val readBuf = ByteArray(minBuffer.coerceAtLeast(frameBytes * 2))
                val carry = ByteArrayOutputStream()
                val utterance = ByteArrayOutputStream()
                try {
                    while (running.get()) {
                        val n = recorder.read(readBuf, 0, readBuf.size)
                        if (n <= 0) continue
                        carry.write(readBuf, 0, n)
                        val blob = carry.toByteArray()
                        carry.reset()
                        var offset = 0
                        while (offset + frameBytes <= blob.size) {
                            val frame = blob.copyOfRange(offset, offset + frameBytes)
                            offset += frameBytes
                            if (vadInstance.isSpeech(frame)) {
                                utterance.write(frame)
                            } else if (utterance.size() > 0) {
                                val pcm = utterance.toByteArray()
                                utterance.reset()
                                if (pcm.size >= frameBytes * 2) {
                                    val wav = pcm16MonoLeToWav(pcm, pcmSampleRate)
                                    scope.launch(Dispatchers.Default) {
                                        runCatching { onUtteranceWav(wav) }
                                    }
                                }
                            }
                        }
                        if (offset < blob.size) {
                            carry.write(blob, offset, blob.size - offset)
                        }
                    }
                    if (utterance.size() > 0) {
                        val pcm = utterance.toByteArray()
                        if (pcm.size >= frameBytes * 2) {
                            val wav = pcm16MonoLeToWav(pcm, pcmSampleRate)
                            scope.launch(Dispatchers.Default) {
                                runCatching { onUtteranceWav(wav) }
                            }
                        }
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                } finally {
                    runCatching { vadInstance.close() }
                }
            },
            "VoiceChatVadRecorder"
        ).also { it.start() }
    }

    actual suspend fun stop() = withContext(Dispatchers.IO) {
        running.set(false)
        val rec = audioRecord
        audioRecord = null
        runCatching {
            rec?.stop()
            rec?.release()
        }
        worker?.join(2000)
        worker = null
    }
}
