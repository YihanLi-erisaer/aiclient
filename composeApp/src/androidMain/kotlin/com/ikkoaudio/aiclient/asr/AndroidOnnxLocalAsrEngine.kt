package com.ikkoaudio.aiclient.asr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.ikkoaudio.aiclient.core.audio.WavPcmExtractor
import com.ikkoaudio.aiclient.core.audio.pcmBytesPerFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Loads an ASR ONNX model from assets and runs inference on-device.
 *
 * Place your model at [DEFAULT_MODEL_ASSET_PATH] (`src/androidMain/assets/models/asr.onnx`).
 * Optional [DEFAULT_VOCAB_ASSET_PATH]: one token per line (index = line number), line 0 = CTC blank.
 * If the vocab file is absent, a small English-oriented default alphabet is used.
 *
 * Default preprocessing: WAV → PCM S16LE → float32 `[-1,1]`, input tensor shape inferred from the
 * model’s first input (dynamic dims `-1` are filled from `N` samples). If your model expects
 * mel spectrograms or other features, subclass and override [buildInputTensor] / [decodePredictions].
 */
open class AndroidOnnxLocalAsrEngine(
    private val context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
    private val vocabAssetPath: String = DEFAULT_VOCAB_ASSET_PATH,
) : LocalAsrEngine {

    companion object {
        private const val TAG = "OnnxLocalAsr"

        /** Default asset path for the ONNX ASR model. */
        const val DEFAULT_MODEL_ASSET_PATH = "models/asr.onnx"

        /** Optional CTC vocabulary: line index = token id, line 0 = blank. */
        const val DEFAULT_VOCAB_ASSET_PATH = "models/asr_vocab.txt"

        /** CTC blank index (must match line 0 of vocab). */
        const val DEFAULT_CTC_BLANK_INDEX = 0
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val vocab: List<String> by lazy { loadVocab() }

    init {
        runCatching { loadModel() }
            .onFailure { Log.e(TAG, "Failed to load ONNX model from assets/$modelAssetPath", it) }
    }

    private fun loadModel() {
        val bytes = context.assets.open(modelAssetPath).readBytes()
        val opts = OrtSession.SessionOptions()
        runCatching { opts.addNnapi() }
            .onFailure { Log.d(TAG, "NNAPI disabled: ${it.message}") }
        session = environment.createSession(bytes, opts)
    }

    override val isReady: Boolean get() = session != null

    private fun loadVocab(): List<String> {
        return runCatching {
            context.assets.open(vocabAssetPath).bufferedReader().use { r ->
                r.readLines().map { it.trimEnd() }
            }
        }.getOrElse { ex ->
            Log.i(TAG, "No $vocabAssetPath (${ex.message}); using built-in English vocab")
            builtInEnglishVocab()
        }
    }

    private fun builtInEnglishVocab(): List<String> {
        val list = ArrayList<String>(256)
        list.add("<blank>")
        list.add(" ")
        for (c in 'a'..'z') list.add(c.toString())
        for (c in 'A'..'Z') list.add(c.toString())
        for (c in '0'..'9') list.add(c.toString())
        list.add("'")
        return list
    }

    override suspend fun transcribeWav(wavBytes: ByteArray): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val session = session
                ?: error("ONNX session not loaded. Add assets/$modelAssetPath (see AndroidOnnxLocalAsrEngine).")
            val pcmPlayback = WavPcmExtractor.tryExtract(wavBytes)
                ?: error("Not a valid WAV PCM file")
            val pcm = pcmPlayback.pcm
            val fmt = pcmPlayback.format
            if (!fmt.format.equals("PCM_S16LE", ignoreCase = true)) {
                error("Default pipeline expects PCM_S16LE; got ${fmt.format}")
            }
            val bpf = fmt.pcmBytesPerFrame()
            if (bpf <= 0 || pcm.size % bpf != 0) {
                error("Invalid PCM frame alignment")
            }
            val frames = pcm.size / bpf
            val floats = FloatArray(frames)
            var base = 0
            for (i in 0 until frames) {
                val lo = pcm[base].toInt() and 0xFF
                val hi = pcm[base + 1].toInt() and 0xFF
                var s = lo or (hi shl 8)
                if (s >= 0x8000) s -= 0x10000
                floats[i] = s / 32768f
                base += bpf
            }
            val inputName = session.inputNames.first()
            val tensor = buildInputTensor(session, inputName, floats)
            try {
                session.run(mapOf(inputName to tensor)).use { results ->
                    val onnxValue: OnnxValue = results[0]
                    val value = onnxValue.value
                        ?: error("Empty ONNX output")
                    decodePredictions(value)
                        ?: error(
                            "decodePredictions() returned null — check output shape/type for your model " +
                                "(got ${value::class.simpleName})."
                        )
                }
            } finally {
                tensor.close()
            }
        }
    }

    /**
     * Builds the ONNX input tensor for the first sample. Override if your model expects mel / features.
     */
    protected open fun buildInputTensor(session: OrtSession, inputName: String, floats: FloatArray): OnnxTensor {
        val nodeInfo = session.inputInfo[inputName]
        val tensorInfo = nodeInfo?.info as? TensorInfo
        val shape = if (tensorInfo != null) {
            inferShape(tensorInfo.shape, floats.size)
        } else {
            longArrayOf(1, floats.size.toLong())
        }
        val expected = shape.fold(1L) { a, b -> a * b }
        val actual = floats.size.toLong()
        if (expected != actual) {
            Log.w(TAG, "Input shape product=$expected but float count=$actual; using [1, N] fallback")
            val shape1d = longArrayOf(1, floats.size.toLong())
            return OnnxTensor.createTensor(environment, FloatBuffer.wrap(floats), shape1d)
        }
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(floats), shape)
    }

    private fun inferShape(shape: LongArray, elementCount: Int): LongArray {
        var negCount = 0
        var negIdx = -1
        for (i in shape.indices) {
            if (shape[i] <= 0) {
                negCount++
                negIdx = i
            }
        }
        if (negCount == 1 && negIdx >= 0) {
            var prodKnown = 1L
            for (i in shape.indices) {
                if (i != negIdx && shape[i] > 0) prodKnown *= shape[i]
            }
            val inferred = elementCount / prodKnown
            if (inferred * prodKnown != elementCount.toLong()) {
                Log.w(TAG, "Cannot infer shape for elementCount=$elementCount shape=${shape.contentToString()}")
                return longArrayOf(1, elementCount.toLong())
            }
            val out = shape.copyOf()
            out[negIdx] = inferred
            return out
        }
        if (negCount == 0) return shape
        return longArrayOf(1, elementCount.toLong())
    }

    /**
     * Map ONNX output to text. Default: CTC greedy on [T, V] logits (float).
     */
    protected open fun decodePredictions(value: Any): String? {
        val logits2d = unwrapLogits(value) ?: return null
        return ctcGreedyDecode(logits2d, DEFAULT_CTC_BLANK_INDEX, vocab)
    }

    private fun unwrapLogits(value: Any): Array<FloatArray>? {
        return when (value) {
            is Array<*> -> {
                if (value.isEmpty()) return null
                when (val first = value[0]) {
                    is FloatArray -> {
                        @Suppress("UNCHECKED_CAST")
                        value as Array<FloatArray>
                    }
                    is Array<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val batch = value as Array<Array<FloatArray>>
                        if (batch.isEmpty()) return null
                        batch[0]
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * CTC greedy: argmax per time step, then skip blanks and collapse repeats (standard PyTorch-style).
     */
    protected fun ctcGreedyDecode(logits: Array<FloatArray>, blankIndex: Int, vocab: List<String>): String {
        if (logits.isEmpty()) return ""
        val path = IntArray(logits.size) { t -> argmax(logits[t]) }
        val sb = StringBuilder()
        for (t in path.indices) {
            val p = path[t]
            if (p == blankIndex) continue
            if (t > 0 && path[t - 1] == p) continue
            val token = vocab.getOrNull(p) ?: continue
            if (token.isNotEmpty() && token != "<blank>") sb.append(token)
        }
        return sb.toString()
    }

    private fun argmax(row: FloatArray): Int {
        var maxIdx = 0
        var maxVal = row[0]
        for (i in 1 until row.size) {
            if (row[i] > maxVal) {
                maxVal = row[i]
                maxIdx = i
            }
        }
        return maxIdx
    }
}
