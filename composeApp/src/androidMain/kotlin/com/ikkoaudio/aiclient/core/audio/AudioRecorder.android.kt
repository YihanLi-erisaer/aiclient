package com.ikkoaudio.aiclient.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

actual class PlatformAudioRecorder : AudioRecorder {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var pcmBuffer = ByteArrayOutputStream()

    override fun startRecording() {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) return

        pcmBuffer = ByteArrayOutputStream()
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuffer * 2
        )
        audioRecord = recorder
        isRecording.set(true)
        recorder.startRecording()

        recordingThread = Thread {
            val chunk = ByteArray(minBuffer)
            while (isRecording.get()) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    pcmBuffer.write(chunk, 0, read)
                }
            }
        }.apply {
            name = "AudioRecorderThread"
            start()
        }
    }

    override suspend fun stopRecording(): ByteArray = withContext(Dispatchers.IO) {
        val recorder = audioRecord ?: return@withContext ByteArray(0)
        return@withContext try {
            isRecording.set(false)
            runCatching { recorder.stop() }
            recordingThread?.join(300)
            recorder.release()
            audioRecord = null
            recordingThread = null

            val pcmData = pcmBuffer.toByteArray()
            if (pcmData.isEmpty()) {
                ByteArray(0)
            } else {
                createWav(pcmData, sampleRate, channels = 1, bitsPerSample = 16)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun createWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".encodeToByteArray())
            putInt(totalDataLen)
            put("WAVE".encodeToByteArray())
            put("fmt ".encodeToByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample)
            put("data".encodeToByteArray())
            putInt(pcmData.size)
        }.array()

        return ByteArray(header.size + pcmData.size).also {
            header.copyInto(it, destinationOffset = 0)
            pcmData.copyInto(it, destinationOffset = header.size)
        }
    }
}
