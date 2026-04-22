package com.ikkoaudio.aiclient.asr

import android.content.Context
import android.util.Log
import com.ikkoaudio.aiclient.core.audio.WavPcmExtractor
import com.ikkoaudio.aiclient.core.audio.pcmBytesPerFrame
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local ASR via [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (JNI + ONNX Runtime inside the AAR).
 *
 * Supports two recognizer modes:
 *
 * **Offline** (`OfflineRecognizer`) — processes a complete utterance at once.
 * **Online / streaming** (`OnlineRecognizer`) — processes audio chunk-by-chunk with partial results.
 *
 * At init time both modes are attempted; [supportsStreaming] reflects whether
 * the model files are compatible with the online recognizer.
 *
 * Expects model files under [DEFAULT_MODEL_DIR] in assets.
 *
 * See [pretrained models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html).
 */
open class AndroidSherpaOnnxLocalAsrEngine(
    private val context: Context,
    private val modelAssetDir: String = DEFAULT_MODEL_DIR,
) : LocalAsrEngine {

    companion object {
        private const val TAG = "SherpaLocalAsr"
        const val DEFAULT_MODEL_DIR = "models/sherpa-asr"
    }

    private val decodeMutex = Mutex()
    private var offlineRecognizer: OfflineRecognizer? = null
    private var onlineRecognizer: OnlineRecognizer? = null

    init {
        runCatching { initOfflineRecognizer() }
            .onFailure { Log.e(TAG, "Sherpa-ONNX offline init failed", it) }
        if (encoderHasOnlineMetadata()) {
            runCatching { initOnlineRecognizer() }
                .onSuccess { Log.i(TAG, "Sherpa-ONNX online (streaming) recognizer ready") }
                .onFailure { Log.i(TAG, "Online recognizer init failed: ${it.message}") }
        } else {
            Log.i(TAG, "Skipping online recognizer: encoder lacks 'encoder_dims' metadata (offline-only model)")
        }
    }

    private fun listAssetFileNames(): Array<String> =
        context.assets.list(modelAssetDir) ?: emptyArray()

    /**
     * Scans the encoder ONNX file for the "encoder_dims" metadata key.
     * Online (streaming) Zipformer2 models require this; offline-only models lack it.
     * Without this check, the native sherpa-onnx code calls exit() on incompatible
     * models, which kills the process and cannot be caught by runCatching.
     */
    private fun encoderHasOnlineMetadata(): Boolean {
        val names = listAssetFileNames()
        val enc = names.firstOrNull {
            it.contains("encoder", ignoreCase = true) && it.endsWith(".onnx", ignoreCase = true)
        } ?: return false
        return try {
            context.assets.open("$modelAssetDir/$enc").use { input ->
                streamContainsBytes(input, "encoder_dims".encodeToByteArray())
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun streamContainsBytes(input: java.io.InputStream, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        val bufSize = maxOf(8192, needle.size * 2)
        val buf = ByteArray(bufSize)
        var carried = 0
        while (true) {
            val read = input.read(buf, carried, buf.size - carried)
            if (read <= 0) return false
            val end = carried + read
            for (i in 0..end - needle.size) {
                var ok = true
                for (j in needle.indices) {
                    if (buf[i + j] != needle[j]) { ok = false; break }
                }
                if (ok) return true
            }
            carried = (needle.size - 1).coerceAtMost(end)
            System.arraycopy(buf, end - carried, buf, 0, carried)
        }
    }

    // ── Offline config builders ──────────────────────────────────────────

    private fun buildZipformerTransducerConfig(): OfflineModelConfig? {
        val names = listAssetFileNames()
        if (!names.contains("tokens.txt")) return null
        val onnx = names.filter { it.endsWith(".onnx", ignoreCase = true) }
        val enc = onnx.firstOrNull { it.contains("encoder", ignoreCase = true) } ?: return null
        val dec = onnx.filter { it.contains("decoder", ignoreCase = true) }
            .firstOrNull {
                !it.contains("uncached", ignoreCase = true) && !it.contains("cached", ignoreCase = true)
            }
            ?: onnx.firstOrNull { it.contains("decoder", ignoreCase = true) }
            ?: return null
        val joi = onnx.firstOrNull { it.contains("joiner", ignoreCase = true) } ?: return null
        return OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = "$modelAssetDir/$enc",
                decoder = "$modelAssetDir/$dec",
                joiner = "$modelAssetDir/$joi",
            ),
            tokens = "$modelAssetDir/tokens.txt",
            modelType = "transducer",
            numThreads = 2,
            provider = "cpu",
        )
    }

    private fun buildZipformerCtcConfig(): OfflineModelConfig? {
        val names = listAssetFileNames()
        if (!names.contains("tokens.txt")) return null
        val modelName = when {
            names.contains("model.int8.onnx") -> "model.int8.onnx"
            names.contains("model.onnx") -> "model.onnx"
            else -> return null
        }
        return OfflineModelConfig(
            zipformerCtc = OfflineZipformerCtcModelConfig(model = "$modelAssetDir/$modelName"),
            tokens = "$modelAssetDir/tokens.txt",
            numThreads = 2,
            provider = "cpu",
        )
    }

    private fun buildOfflineModelConfig(): OfflineModelConfig {
        buildZipformerTransducerConfig()?.let { return it }
        buildZipformerCtcConfig()?.let { return it }
        error(
            "No Zipformer model in assets/$modelAssetDir. Add either:\n" +
                "  • transducer: encoder*.onnx + decoder*.onnx + joiner*.onnx + tokens.txt, or\n" +
                "  • Zipformer CTC: model.int8.onnx (or model.onnx) + tokens.txt"
        )
    }

    private fun initOfflineRecognizer() {
        val modelConfig = buildOfflineModelConfig()
        val feat = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f)
        val config = OfflineRecognizerConfig(
            featConfig = feat,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
        offlineRecognizer = OfflineRecognizer(context.assets, config)
    }

    // ── Online (streaming) config builders ───────────────────────────────

    /**
     * Try to build an [OnlineModelConfig] from the same assets directory.
     * Transducer models that were trained for streaming work with [OnlineRecognizer];
     * offline-only models will cause JNI init to fail, which is caught by the caller.
     */
    private fun buildOnlineTransducerConfig(): OnlineModelConfig? {
        val names = listAssetFileNames()
        if (!names.contains("tokens.txt")) return null
        val onnx = names.filter { it.endsWith(".onnx", ignoreCase = true) }
        val enc = onnx.firstOrNull { it.contains("encoder", ignoreCase = true) } ?: return null
        val dec = onnx.filter { it.contains("decoder", ignoreCase = true) }
            .firstOrNull {
                !it.contains("uncached", ignoreCase = true) && !it.contains("cached", ignoreCase = true)
            }
            ?: onnx.firstOrNull { it.contains("decoder", ignoreCase = true) }
            ?: return null
        val joi = onnx.firstOrNull { it.contains("joiner", ignoreCase = true) } ?: return null
        return OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "$modelAssetDir/$enc",
                decoder = "$modelAssetDir/$dec",
                joiner = "$modelAssetDir/$joi",
            ),
            tokens = "$modelAssetDir/tokens.txt",
            numThreads = 2,
            provider = "cpu",
        )
    }

    private fun buildOnlineZipformer2CtcConfig(): OnlineModelConfig? {
        val names = listAssetFileNames()
        if (!names.contains("tokens.txt")) return null
        val modelName = when {
            names.contains("model.int8.onnx") -> "model.int8.onnx"
            names.contains("model.onnx") -> "model.onnx"
            else -> return null
        }
        return OnlineModelConfig(
            zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = "$modelAssetDir/$modelName"),
            tokens = "$modelAssetDir/tokens.txt",
            numThreads = 2,
            provider = "cpu",
        )
    }

    private fun initOnlineRecognizer() {
        val modelConfig = buildOnlineTransducerConfig()
            ?: buildOnlineZipformer2CtcConfig()
            ?: error("No transducer or CTC model files found for online recognizer")
        val feat = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f)
        val endpoint = EndpointConfig(
            rule1 = EndpointRule(false, 2.4f, 0.0f),
            rule2 = EndpointRule(true, 1.4f, 0.0f),
            rule3 = EndpointRule(false, 0.0f, 20.0f),
        )
        val config = OnlineRecognizerConfig(
            featConfig = feat,
            modelConfig = modelConfig,
            endpointConfig = endpoint,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        onlineRecognizer = OnlineRecognizer(assetManager = context.assets, config = config)
    }

    // ── LocalAsrEngine implementation ────────────────────────────────────

    override val isReady: Boolean get() = offlineRecognizer != null || onlineRecognizer != null

    override val supportsStreaming: Boolean get() = onlineRecognizer != null

    override fun createStreamingSession(): StreamingAsrSession? {
        val rec = onlineRecognizer ?: return null
        return SherpaStreamingSession(rec)
    }

    override suspend fun transcribeWav(wavBytes: ByteArray): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val rec = offlineRecognizer
                ?: error("Sherpa-ONNX offline not initialized. Add model under assets/$modelAssetDir.")
            val samples = wavToMonoFloat(wavBytes)
            decodeMutex.withLock {
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(samples, 16000)
                    rec.decode(stream)
                    rec.getResult(stream).text
                } finally {
                    stream.release()
                }
            }
        }
    }

    // ── Shared WAV → float conversion ────────────────────────────────────

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

    // ── Streaming session ────────────────────────────────────────────────

    private class SherpaStreamingSession(
        private val recognizer: OnlineRecognizer,
    ) : StreamingAsrSession {
        private val stream = recognizer.createStream()

        override fun feedSamples(samples: FloatArray, sampleRate: Int) {
            stream.acceptWaveform(samples, sampleRate)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
        }

        override fun getCurrentText(): String = recognizer.getResult(stream).text

        override fun reset() {
            recognizer.reset(stream)
        }

        override fun release() {
            stream.release()
        }
    }
}
