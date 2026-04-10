package com.ikkoaudio.aiclient.asr

import android.content.Context
import android.util.Log
import com.ikkoaudio.aiclient.core.audio.WavPcmExtractor
import com.ikkoaudio.aiclient.core.audio.pcmBytesPerFrame
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local ASR via [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (JNI + ONNX Runtime inside the AAR).
 *
 * Expects a **Zipformer** sherpa-onnx export under [DEFAULT_MODEL_DIR] in assets, in one of these layouts:
 *
 * **1) Zipformer transducer (Icefall)** — `encoder*.onnx`, `decoder*.onnx`, `joiner*.onnx`, `tokens.txt`
 * (e.g. [zipformer transducer models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html)).
 *
 * **2) Zipformer CTC** — `model.int8.onnx` or `model.onnx`, plus `tokens.txt`
 * (e.g. `sherpa-onnx-zipformer-ctc-*` releases).
 *
 * See [pretrained models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html).
 */
open class AndroidSherpaOnnxLocalAsrEngine(
    private val context: Context,
    private val modelAssetDir: String = DEFAULT_MODEL_DIR,
) : LocalAsrEngine {

    companion object {
        private const val TAG = "SherpaLocalAsr"

        /** Assets folder for Zipformer (transducer or CTC) + `tokens.txt`. */
        const val DEFAULT_MODEL_DIR = "models/sherpa-asr"
    }

    private val decodeMutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    init {
        runCatching { initRecognizer() }
            .onFailure { Log.e(TAG, "Sherpa-ONNX init failed", it) }
    }

    private fun listAssetFileNames(): Array<String> =
        context.assets.list(modelAssetDir) ?: emptyArray()

    /**
     * Icefall-style Zipformer **transducer**: three ONNX files whose names contain
     * `encoder`, `decoder`, and `joiner`.
     */
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

    /** Single-file **Zipformer CTC** (e.g. `model.int8.onnx`). */
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

    private fun initRecognizer() {
        val modelConfig = buildOfflineModelConfig()
        val feat = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f)
        val config = OfflineRecognizerConfig(
            featConfig = feat,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(context.assets, config)
    }

    override val isReady: Boolean get() = recognizer != null

    override suspend fun transcribeWav(wavBytes: ByteArray): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val rec = recognizer ?: error("Sherpa-ONNX not initialized. Add model under assets/$modelAssetDir.")
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
            var sampleRate = fmt.sampleRate
            val samplesForSherpa = if (sampleRate != 16000) {
                sampleRate = 16000
                resampleTo16kHz(mono, fmt.sampleRate)
            } else {
                mono
            }
            decodeMutex.withLock {
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(samplesForSherpa, sampleRate)
                    rec.decode(stream)
                    rec.getResult(stream).text
                } finally {
                    stream.release()
                }
            }
        }
    }

    private fun resampleTo16kHz(samples: FloatArray, srcRate: Int): FloatArray {
        if (srcRate == 16000) return samples
        val outLen = (samples.size * 16000L / srcRate).toInt().coerceAtLeast(1)
        return FloatArray(outLen) { i ->
            val srcPos = ((i.toLong() * srcRate) / 16000).toInt().coerceIn(0, samples.size - 1)
            samples[srcPos]
        }
    }
}
