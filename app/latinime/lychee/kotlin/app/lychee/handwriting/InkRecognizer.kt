package app.lychee.handwriting

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.FloatBuffer

/** What the handwriting pad asks of a recogniser: Lychee's own ([InkRecognizer]) or PP-OCR. */
interface Recognizer {
    /**
     * The best characters for [strokes] (each x, y pairs in screen coordinates), best first,
     * with their scores (0..1); only those [allowed] lets through.
     */
    fun recognise(strokes: List<FloatArray>, limit: Int = 12, allowed: (String) -> Boolean = { true }): List<Pair<String, Float>>

    fun close()
}

/**
 * Lychee's own handwriting recogniser (the "handwriting" pack, trained by packs/handwriting):
 * it reads the pen's path, stroke by stroke in the order written, not a picture of it, so
 * joined-up and hurried writing still reads. One model knows Chinese (both scripts), Japanese
 * (kana and kanji) and Korean; the pad lets through only the keyboard's own.
 *
 * Opened while the pad is open and closed with it. Not thread-safe: use it from one thread.
 */
class InkRecognizer private constructor(
    private val session: OrtSession,
    private val chars: List<String>,
) : Recognizer {

    override fun recognise(strokes: List<FloatArray>, limit: Int, allowed: (String) -> Boolean): List<Pair<String, Float>> {
        val (features, points) = InkFeatures.of(strokes) ?: return emptyList()
        val env = OrtEnvironment.getEnvironment()
        val logits = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, points.toLong(), InkFeatures.FEATURES.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                (result[0].value as Array<FloatArray>)[0]
            }
        }
        // softmax over every class, then only the allowed ones
        var top = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > top) top = v
        var sum = 0.0
        val probs = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = kotlin.math.exp((logits[i] - top).toDouble())
            probs[i] = e.toFloat()
            sum += e
        }
        return probs.indices.asSequence()
            .filter { it < chars.size }
            .sortedByDescending { probs[it] }
            .map { chars[it] to (probs[it] / sum).toFloat() }
            .filter { (ch, _) -> allowed(ch) }
            .take(limit)
            .toList()
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        const val PACK = "handwriting"

        private fun files(context: Context): Pair<File, File> {
            val dir = File(app.lychee.packs.Packs.dir(context, PACK), "data")
            return File(dir, "handwriting.onnx") to File(dir, "handwriting.txt")
        }

        /** Whether the handwriting pack is installed. */
        fun available(context: Context): Boolean = files(context).let { (m, c) -> m.isFile && c.isFile }

        /** Opens the recogniser from the handwriting pack; null when it isn't installed. */
        fun open(context: Context): InkRecognizer? = runCatching {
            val (model, list) = files(context)
            if (!model.isFile || !list.isFile) return null
            val chars = list.readText(Charsets.UTF_8).split('\n').let { if (it.lastOrNull()?.isEmpty() == true) it.dropLast(1) else it }
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            InkRecognizer(OrtEnvironment.getEnvironment().createSession(model.path, options), chars)
        }.onFailure { app.lychee.DiagLog.log("handwriting model failed to open", it) }.getOrNull()
    }
}
