package com.ikkoaudio.aiclient.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Browser voice chat: energy + hangover VAD on mic PCM (no WebRTC native VAD in WASM/JS). */
actual class PlatformVoiceChatRecorder {

    private var running = false
    private var audioContext: dynamic = null
    private var mediaStream: dynamic = null
    private var processor: dynamic = null
    private var sourceNode: dynamic = null

    actual fun start(scope: CoroutineScope, onUtteranceWav: suspend (ByteArray) -> Unit) {
        if (running) return
        running = true
        val md = js("typeof navigator !== 'undefined' ? navigator.mediaDevices : null")
        if (md == null) {
            running = false
            return
        }
        val promise = md.asDynamic().getUserMedia(js("({ audio: true })"))
        promise.then(
            { stream: dynamic ->
                if (!running) {
                    js("(function(s){ s.getTracks().forEach(function(t){ t.stop(); }); })")(stream)
                    return@then null
                }
                mediaStream = stream
                val AC = js("(window['AudioContext'] || window['webkitAudioContext'])")
                val ctx = js("(function(AC){ try { return new AC({ sampleRate: 16000 }); } catch(e) { return new AC(); } })(AC)")
                audioContext = ctx
                val sampleRate = (ctx.sampleRate as Number).toInt().coerceAtLeast(8000)
                val frameSamples = sampleRate / 50
                val vad = EnergyHangoverVad(sampleRate = sampleRate, frameSamples = frameSamples)
                val frameBytes = vad.frameBytes
                val minUtteranceBytes = frameBytes * 2

                var carry = ByteArray(0)
                var utterance = ByteArray(0)

                fun flushUtterance() {
                    if (utterance.size < minUtteranceBytes) {
                        utterance = ByteArray(0)
                        return
                    }
                    val pcmForWav =
                        if (sampleRate == TARGET_EXPORT_RATE) utterance
                        else resamplePcm16MonoLinear(utterance, sampleRate, TARGET_EXPORT_RATE)
                    val wav = pcm16MonoLeToWav(pcmForWav, TARGET_EXPORT_RATE)
                    utterance = ByteArray(0)
                    scope.launch(Dispatchers.Default) {
                        runCatching { onUtteranceWav(wav) }
                    }
                }

                val source = ctx.createMediaStreamSource(stream)
                sourceNode = source
                val proc = ctx.createScriptProcessor(4096, 1, 1)
                processor = proc
                proc.onaudioprocess = { ev: dynamic ->
                    if (running) {
                        val input = ev.inputBuffer.getChannelData(0)
                        val pcmChunk = floatsToPcm16LeMono(input)
                        carry = carry + pcmChunk
                        var offset = 0
                        while (offset + frameBytes <= carry.size) {
                            val frame = carry.copyOfRange(offset, offset + frameBytes)
                            offset += frameBytes
                            when (vad.processFramePcm16LeMono(frame)) {
                                EnergyHangoverVad.FrameLabel.SPEECH -> {
                                    utterance = utterance + frame
                                }
                                EnergyHangoverVad.FrameLabel.END_UTTERANCE -> flushUtterance()
                                EnergyHangoverVad.FrameLabel.NO_SPEECH -> {}
                            }
                        }
                        carry =
                            if (offset < carry.size) carry.copyOfRange(offset, carry.size)
                            else ByteArray(0)
                    }
                    undefined
                }
                source.connect(proc)
                proc.connect(ctx.destination)
                null
            },
            { _: dynamic ->
                running = false
                null
            }
        )
    }

    actual suspend fun stop() {
        running = false
        val proc = processor
        val src = sourceNode
        val ctx = audioContext
        processor = null
        sourceNode = null
        audioContext = null
        runCatching {
            proc.asDynamic().onaudioprocess = null
            proc.disconnect()
        }
        runCatching { src.disconnect() }
        runCatching { ctx.close() }
        mediaStream?.let { s ->
            js("(function(s){ s.getTracks().forEach(function(t){ t.stop(); }); })")(s)
        }
        mediaStream = null
        delay(50)
    }

    private companion object {
        const val TARGET_EXPORT_RATE = 16_000
    }
}

private fun floatsToPcm16LeMono(input: dynamic): ByteArray {
    val len = js("input.length") as Int
    if (len <= 0) return ByteArray(0)
    val d = input.asDynamic()
    val out = ByteArray(len * 2)
    var o = 0
    var i = 0
    while (i < len) {
        val f = d[i].unsafeCast<Double>()
        val s = (f * 32767.0).toInt().coerceIn(-32768, 32767)
        out[o++] = (s and 0xFF).toByte()
        out[o++] = ((s shr 8) and 0xFF).toByte()
        i++
    }
    return out
}
