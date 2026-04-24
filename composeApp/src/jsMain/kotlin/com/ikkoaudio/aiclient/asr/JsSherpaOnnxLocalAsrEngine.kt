package com.ikkoaudio.aiclient.asr

import com.ikkoaudio.aiclient.core.audio.WavPcmExtractor
import com.ikkoaudio.aiclient.core.audio.pcmBytesPerFrame
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.khronos.webgl.Float32Array
import org.w3c.fetch.RequestInit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsModule
import kotlin.js.JsNonModule

/**
 * Browser local ASR using the same [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) stack as Android
 * (npm package + WebAssembly), with models served as static files (default base `models/sherpa-asr/`,
 * same layout as Android `assets/models/sherpa-asr/`).
 *
 * Optional override: set `window.__SHERPA_MODEL_BASE__` to a URL prefix (no trailing slash), e.g.
 * `"https://cdn.example.com/my-asr"`.
 *
 * ONNX + WASM files must be fetchable from the browser (same origin or CORS). If the app fails to load
 * `sherpa-onnx-wasm-nodejs.wasm` at runtime, copy that file from `node_modules/sherpa-onnx/` (after a JS build)
 * next to the deployed `composeApp.js` so Emscripten’s `locateFile` can resolve it.
 */
class JsSherpaOnnxLocalAsrEngine : LocalAsrEngine {

    private val prepMutex = Mutex()
    private val decodeMutex = Mutex()

    private var prepareFinished: Boolean = false
    private var offlineRecognizer: dynamic = null
    private var onlineRecognizer: dynamic = null

    override val isReady: Boolean get() = offlineRecognizer != null

    override val supportsStreaming: Boolean get() = onlineRecognizer != null

    override suspend fun prepare() {
        prepMutex.withLock {
            if (prepareFinished) return
            prepareFinished = true
            withContext(Dispatchers.Default) {
                val base = modelBasePrefix()
                val layout = resolveModelLayout(base) ?: return@withContext
                offlineRecognizer = SherpaOnnxNpm.createOfflineRecognizer(buildOfflineRecognizerConfig(layout, base))
                onlineRecognizer = runCatching {
                    SherpaOnnxNpm.createOnlineRecognizer(buildOnlineRecognizerConfig(layout, base))
                }.getOrNull()
            }
        }
    }

    override suspend fun transcribeWav(wavBytes: ByteArray): Result<String> = withContext(Dispatchers.Default) {
        prepare()
        runCatching {
            val rec = offlineRecognizer ?: error("Sherpa-ONNX offline not initialized. Deploy models under $MODEL_DIR_REL.")
            val samples = wavToMonoFloat(wavBytes)
            decodeMutex.withLock {
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(16000, samples.toFloat32Array())
                    rec.decode(stream)
                    sherpaOfflineText(rec, stream)
                } finally {
                    sherpaStreamFree(stream)
                }
            }
        }
    }

    override fun createStreamingSession(): StreamingAsrSession? {
        val rec = onlineRecognizer ?: return null
        val stream = rec.createStream()
        return JsSherpaStreamingSession(rec, stream)
    }

    private class JsSherpaStreamingSession(
        private val recognizer: dynamic,
        private val stream: dynamic,
    ) : StreamingAsrSession {
        override fun feedSamples(samples: FloatArray, sampleRate: Int) {
            stream.acceptWaveform(sampleRate, samples.toFloat32Array())
            while (sherpaOnlineIsReady(recognizer, stream)) {
                recognizer.decode(stream)
            }
        }

        override fun getCurrentText(): String = sherpaOnlineText(recognizer, stream)

        override fun reset() {
            recognizer.reset(stream)
        }

        override fun release() {
            sherpaStreamFree(stream)
        }
    }

    private sealed class Layout {
        data class Transducer(val encoderFile: String, val decoderFile: String, val joinerFile: String) : Layout()
        data class ZipformerCtc(val modelFile: String) : Layout()
    }

    private fun modelBasePrefix(): String {
        val custom = js(
            "(typeof __SHERPA_MODEL_BASE__ !== 'undefined' && __SHERPA_MODEL_BASE__) ? String(__SHERPA_MODEL_BASE__) : ''"
        ).unsafeCast<String>().trim().trimEnd('/')
        return if (custom.isNotEmpty()) custom else MODEL_DIR_REL
    }

    private suspend fun resolveModelLayout(base: String): Layout? {
        val tokensUrl = "$base/tokens.txt"
        if (!fetchResourceExists(tokensUrl)) return null
        val ctcInt8 = "$base/model.int8.onnx"
        val ctcFp = "$base/model.onnx"
        when {
            fetchHeadOk(ctcInt8) -> return Layout.ZipformerCtc("model.int8.onnx")
            fetchHeadOk(ctcFp) -> return Layout.ZipformerCtc("model.onnx")
        }
        for (enc in ENCODER_CANDIDATES) {
            if (!fetchHeadOk("$base/$enc")) continue
            val dec = DECODER_CANDIDATES.firstOrNull {
                !it.contains("uncached", ignoreCase = true) &&
                    !it.contains("cached", ignoreCase = true) &&
                    fetchHeadOk("$base/$it")
            } ?: DECODER_CANDIDATES.firstOrNull { fetchHeadOk("$base/$it") } ?: continue
            val joi = JOINER_CANDIDATES.firstOrNull { fetchHeadOk("$base/$it") } ?: continue
            return Layout.Transducer(enc, dec, joi)
        }
        return null
    }

    private suspend fun fetchHeadOk(url: String): Boolean = suspendCoroutine { cont ->
        val init = js("({ method: 'HEAD', cache: 'no-cache' })").unsafeCast<RequestInit>()
        window.fetch(url, init).then(
            { r -> cont.resume(r.ok) },
            { cont.resume(false) },
        )
    }

    /** HEAD first (cheap); fall back to GET for hosts that do not implement HEAD (small files only). */
    private suspend fun fetchResourceExists(url: String): Boolean {
        if (fetchHeadOk(url)) return true
        return suspendCoroutine { cont ->
            val init = js("({ method: 'GET', cache: 'no-cache' })").unsafeCast<RequestInit>()
            window.fetch(url, init).then(
                { r -> cont.resume(r.ok) },
                { cont.resume(false) },
            )
        }
    }

    private fun buildOfflineRecognizerConfig(layout: Layout, base: String): dynamic {
        val cfg = js("{}")
        cfg.decodingMethod = "greedy_search"
        cfg.featConfig = js("{}")
        cfg.featConfig.sampleRate = 16000
        cfg.featConfig.featureDim = 80
        cfg.featConfig.dither = 0.0
        cfg.modelConfig = js("{}")
        cfg.modelConfig.tokens = "$base/tokens.txt"
        cfg.modelConfig.numThreads = 2
        cfg.modelConfig.provider = "cpu"
        when (layout) {
            is Layout.Transducer -> {
                cfg.modelConfig.transducer = js("{}")
                cfg.modelConfig.transducer.encoder = "$base/${layout.encoderFile}"
                cfg.modelConfig.transducer.decoder = "$base/${layout.decoderFile}"
                cfg.modelConfig.transducer.joiner = "$base/${layout.joinerFile}"
                cfg.modelConfig.modelType = "transducer"
            }
            is Layout.ZipformerCtc -> {
                cfg.modelConfig.zipformerCtc = js("{}")
                cfg.modelConfig.zipformerCtc.model = "$base/${layout.modelFile}"
            }
        }
        return cfg
    }

    private fun buildOnlineRecognizerConfig(layout: Layout, base: String): dynamic {
        val cfg = js("{}")
        cfg.decodingMethod = "greedy_search"
        cfg.maxActivePaths = 4
        cfg.enableEndpoint = 1
        cfg.rule1MinTrailingSilence = 2.4
        cfg.rule2MinTrailingSilence = 1.4
        cfg.rule3MinUtteranceLength = 20.0
        cfg.hotwordsFile = ""
        cfg.hotwordsScore = 1.5
        cfg.ctcFstDecoderConfig = js("{}")
        cfg.ctcFstDecoderConfig.graph = ""
        cfg.ctcFstDecoderConfig.maxActive = 3000
        cfg.ruleFsts = ""
        cfg.ruleFars = ""
        cfg.featConfig = js("{}")
        cfg.featConfig.sampleRate = 16000
        cfg.featConfig.featureDim = 80
        cfg.modelConfig = js("{}")
        cfg.modelConfig.tokens = "$base/tokens.txt"
        cfg.modelConfig.numThreads = 2
        cfg.modelConfig.provider = "cpu"
        cfg.modelConfig.debug = 0
        cfg.modelConfig.modelType = ""
        cfg.modelConfig.modelingUnit = "cjkchar"
        cfg.modelConfig.bpeVocab = ""
        when (layout) {
            is Layout.Transducer -> {
                cfg.modelConfig.transducer = js("{}")
                cfg.modelConfig.transducer.encoder = "$base/${layout.encoderFile}"
                cfg.modelConfig.transducer.decoder = "$base/${layout.decoderFile}"
                cfg.modelConfig.transducer.joiner = "$base/${layout.joinerFile}"
            }
            is Layout.ZipformerCtc -> {
                cfg.modelConfig.zipformer2Ctc = js("{}")
                cfg.modelConfig.zipformer2Ctc.model = "$base/${layout.modelFile}"
            }
        }
        return cfg
    }

    private fun wavToMonoFloat(wavBytes: ByteArray): FloatArray {
        val pcmPlayback = WavPcmExtractor.tryExtract(wavBytes)
            ?: error("Not a valid WAV PCM file")
        val pcm = pcmPlayback.pcm
        val fmt = pcmPlayback.format
        if (!fmt.format.equals("PCM_S16LE", ignoreCase = true)) {
            error("Expected PCM_S16LE WAV; got ${fmt.format}")
        }
        val bpf = fmt.pcmBytesPerFrame()
        val ch = fmt.channels.coerceAtLeast(1)
        val frames = pcm.size / bpf
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until ch) {
                val o = i * bpf + c * 2
                val lo = pcm[o].toInt() and 0xFF
                val hi = pcm[o + 1].toInt() and 0xFF
                var s = lo or (hi shl 8)
                if (s >= 0x8000) s -= 0x10000
                sum += s / 32768f
            }
            mono[i] = sum / ch
        }
        return if (fmt.sampleRate != 16000) resampleTo16kHz(mono, fmt.sampleRate) else mono
    }

    private fun resampleTo16kHz(samples: FloatArray, srcRate: Int): FloatArray {
        if (srcRate == 16000) return samples
        val outLen = (samples.size * 16000L / srcRate).toInt().coerceAtLeast(1)
        return FloatArray(outLen) { i ->
            val srcPos = ((i.toLong() * srcRate) / 16000).toInt().coerceIn(0, samples.size - 1)
            samples[srcPos]
        }
    }

    private companion object {
        const val MODEL_DIR_REL = "models/sherpa-asr"

        private val ENCODER_CANDIDATES = listOf(
            "encoder.onnx",
            "encoder.int8.onnx",
            "encoder-epoch-99-avg-1.onnx",
            "encoder-epoch-99-avg-1.int8.onnx",
        )

        private val DECODER_CANDIDATES = listOf(
            "decoder.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "decoder-epoch-99-avg-1.int8.onnx",
        )

        private val JOINER_CANDIDATES = listOf(
            "joiner.onnx",
            "joiner.int8.onnx",
            "joiner-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
        )
    }
}

@JsModule("sherpa-onnx")
@JsNonModule
internal external object SherpaOnnxNpm {
    fun createOfflineRecognizer(config: dynamic): dynamic
    fun createOnlineRecognizer(config: dynamic): dynamic
}

private fun sherpaOfflineText(rec: dynamic, stream: dynamic): String =
    js("(function(r,s){ var j=r.getResult(s); return (j && j.text) ? String(j.text) : ''; })")(rec, stream)
        .unsafeCast<String>()

private fun sherpaOnlineText(recognizer: dynamic, stream: dynamic): String =
    js("(function(r,s){ var j=r.getResult(s); return (j && j.text) ? String(j.text) : ''; })")(recognizer, stream)
        .unsafeCast<String>()

private fun sherpaOnlineIsReady(recognizer: dynamic, stream: dynamic): Boolean =
    js("(function(r,s){ var v=r.isReady(s); return v===1||v===true; })")(recognizer, stream).unsafeCast<Boolean>()

private fun sherpaStreamFree(stream: dynamic) {
    js("(function(s){ if (s && s.free) s.free(); })")(stream)
}

private fun FloatArray.toFloat32Array(): Float32Array {
    val n = size
    val arr = Float32Array(n)
    val ad = arr.asDynamic()
    var i = 0
    while (i < n) {
        ad[i] = this[i]
        i++
    }
    return arr
}
