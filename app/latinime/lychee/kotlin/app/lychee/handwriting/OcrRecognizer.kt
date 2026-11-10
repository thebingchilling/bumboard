package app.lychee.handwriting

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.nio.FloatBuffer

/**
 * Handwriting recognition by PaddleOCR's PP-OCRv5 mobile text recogniser (Apache-2.0), run
 * with ONNX Runtime: the ink is drawn as a picture (black strokes on white, the character
 * centred with a margin, the way the model reads text) and the model scores every character it
 * knows. [lang] "zh" reads Chinese (both scripts) and Japanese, "ko" Korean.
 *
 * Opened while the handwriting pad is open (the model, 13-16 MB, is read from the APK's
 * assets) and closed with it. Not thread-safe: use it from one thread.
 */
class OcrRecognizer private constructor(
    private val session: OrtSession,
    private val chars: List<String>,
) {
    /**
     * The best characters for [strokes] (each x, y pairs in screen coordinates), best first,
     * with their scores (0..1); only those [allowed] lets through.
     */
    fun recognise(strokes: List<FloatArray>, limit: Int = 12, allowed: (String) -> Boolean = { true }): List<Pair<String, Float>> {
        // the ink smoothed (a shaky hand's wobble taken out), then read as a few pictures,
        // with a thinner and a thicker pen and another margin, and the scores averaged: a
        // character written only roughly right still comes out on top
        val ink = smooth(strokes)
        var total: FloatArray? = null
        var reads = 0
        for ((pen, margin) in VARIANTS) {
            val scores = scores(picture(ink, pen, margin) ?: continue, WIDTH) ?: continue
            val t = total ?: FloatArray(scores.size).also { total = it }
            if (t.size != scores.size) continue
            for (c in scores.indices) t[c] += scores[c]
            reads++
        }
        val best = total ?: return emptyList()
        if (reads == 0) return emptyList()
        // class 0 is CTC's blank; class i is chars[i - 1]
        return (1 until best.size).asSequence()
            .filter { it - 1 < chars.size }
            .map { chars[it - 1] to best[it] / reads }
            .filter { (ch, _) -> ch.isNotBlank() && allowed(ch) }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    /** Each character's best probability anywhere in one picture. */
    private fun scores(input: FloatArray, width: Int): FloatArray? {
        val env = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), width.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<Array<FloatArray>>)[0]  // time x classes
                val classes = out.firstOrNull()?.size ?: return null
                // probabilities already (the models end in softmax); softmax them if not
                val probs = out.all { row -> row.all { it in 0f..1.0001f } }
                val best = FloatArray(classes)
                for (row in out) {
                    val scale = if (probs) 1.0 else row.sumOf { kotlin.math.exp(it.toDouble()) }
                    for (c in 1 until classes) {
                        val p = if (probs) row[c] else (kotlin.math.exp(row[c].toDouble()) / scale).toFloat()
                        if (p > best[c]) best[c] = p
                    }
                }
                return best
            }
        }
    }

    fun close() = runCatching { session.close() }

    companion object {
        /** The model reads pictures 48 pixels tall; a character is drawn in a square that size. */
        private const val SIZE = 48
        /**
         * The picture's width: the character's square in the middle of white 2.5 times as wide,
         * like a word on a line of text, which is what the model was trained on (a bare square
         * reads much worse: about 87% right first time against 72% on simulated handwriting).
         */
        private const val WIDTH = 120
        /** Drawn larger first, then scaled down smoothly, like a photographed line of text. */
        private const val CANVAS = 192
        /** The pictures each character is read as: pen width (of the square) and margin. */
        private val VARIANTS = listOf(0.05f to 1.25f, 0.065f to 1.25f, 0.045f to 1.4f)

        /**
         * The strokes with each point averaged with its neighbours (a shaky hand's wobble
         * taken out; corners stay, the ends stay put).
         */
        fun smooth(strokes: List<FloatArray>): List<FloatArray> = strokes.map { s ->
            val n = s.size / 2
            if (n < 5) return@map s
            val out = s.copyOf()
            for (i in 1 until n - 1) {
                out[2 * i] = (s[2 * (i - 1)] + 2 * s[2 * i] + s[2 * (i + 1)]) / 4
                out[2 * i + 1] = (s[2 * (i - 1) + 1] + 2 * s[2 * i + 1] + s[2 * (i + 1) + 1]) / 4
            }
            out
        }

        /** Opens the [lang] model ("zh" or "ko") from the APK; null when it isn't there. */
        fun open(context: Context, lang: String): OcrRecognizer? = runCatching {
            val assets = context.assets
            val model = assets.open("lychee/ocr/$lang.onnx").use { it.readBytes() }
            val chars = assets.open("lychee/ocr/$lang.txt").bufferedReader(Charsets.UTF_8).use { r ->
                r.readText().split('\n').let { if (it.lastOrNull()?.isEmpty() == true) it.dropLast(1) else it }
            } + " "
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            OcrRecognizer(OrtEnvironment.getEnvironment().createSession(model, options), chars)
        }.onFailure { app.lychee.DiagLog.log("handwriting model $lang failed to open", it) }.getOrNull()

        /**
         * The ink as the model's input: the strokes in a square a quarter larger than the
         * character (centred), drawn with a round pen about 5.5% of the square wide, scaled to
         * [SIZE], set in the middle of a white strip [width] wide and normalised to -1..1, as
         * channels of red, green and blue.
         */
        fun picture(strokes: List<FloatArray>, penWidth: Float = 0.05f, margin: Float = 1.25f, width: Int = WIDTH): FloatArray? {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (s in strokes) {
                var k = 0
                while (k + 1 < s.size) {
                    minX = minOf(minX, s[k]); maxX = maxOf(maxX, s[k])
                    minY = minOf(minY, s[k + 1]); maxY = maxOf(maxY, s[k + 1])
                    k += 2
                }
            }
            if (minX > maxX) return null
            val side = maxOf(maxX - minX, maxY - minY, 1f) * margin
            val scale = CANVAS / side
            val offX = (side - (maxX - minX)) / 2 - minX
            val offY = (side - (maxY - minY)) / 2 - minY
            val big = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(big)
            canvas.drawColor(Color.WHITE)
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = maxOf(2f, CANVAS * penWidth)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (s in strokes) {
                if (s.size < 2) continue
                val path = Path()
                path.moveTo((s[0] + offX) * scale, (s[1] + offY) * scale)
                var k = 2
                while (k + 1 < s.size) {
                    path.lineTo((s[k] + offX) * scale, (s[k + 1] + offY) * scale)
                    k += 2
                }
                // a dot (one point) still shows
                if (s.size == 2) path.lineTo((s[0] + offX) * scale + 0.5f, (s[1] + offY) * scale)
                canvas.drawPath(path, pen)
            }
            // halved twice rather than at once: a smoother picture, like a photographed line
            val half = Bitmap.createScaledBitmap(big, CANVAS / 2, CANVAS / 2, true)
            big.recycle()
            val small = Bitmap.createScaledBitmap(half, SIZE, SIZE, true)
            half.recycle()
            val pixels = IntArray(SIZE * SIZE)
            small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            small.recycle()
            // the square in the middle of a white strip [WIDTH] wide (white is 1 after normalising)
            val plane = SIZE * width
            val out = FloatArray(3 * plane) { 1f }
            val left = (width - SIZE) / 2
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val p = pixels[y * SIZE + x]
                val i = y * width + left + x
                out[i] = ((p shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
                out[plane + i] = ((p shr 8 and 0xFF) / 255f - 0.5f) / 0.5f
                out[2 * plane + i] = ((p and 0xFF) / 255f - 0.5f) / 0.5f
            }
            return out
        }
    }
}
