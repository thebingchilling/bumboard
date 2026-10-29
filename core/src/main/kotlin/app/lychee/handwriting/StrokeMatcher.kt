package app.lychee.handwriting

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Recognises a handwritten character by comparing its strokes with every character's stroke
 * shapes (Make Me a Hanzi's medians, from the bundled stroke-order data), in the spirit of
 * HanziLookup: each stroke is reduced to a few evenly spaced points in the character's own
 * box, every written stroke is paired with the reference stroke it most resembles (so a
 * stroke written out of order or backwards still counts, at a small cost), and a character
 * scores by how far apart its paired strokes are and how many strokes go unpaired.
 * It complements Zinnia's learnt models: it knows every character the stroke data has, in
 * both scripts, and it tolerates messy proportions.
 */
class StrokeMatcher(references: List<Pair<String, List<FloatArray>>>) {

    private class Ref(val char: String, val strokes: Array<FloatArray>)

    /** References by stroke count. */
    private val byCount: Map<Int, List<Ref>> = references.mapNotNull { (char, strokes) ->
        val shaped = shape(strokes) ?: return@mapNotNull null
        Ref(char, shaped)
    }.groupBy { it.strokes.size }

    val size: Int = byCount.values.sumOf { it.size }

    /**
     * The best characters for [strokes] (each x, y pairs in screen coordinates, y down), best
     * first, at most [limit]; lower scores are better.
     */
    fun match(strokes: List<FloatArray>, limit: Int = 20): List<Pair<String, Float>> {
        val input = shape(strokes) ?: return emptyList()
        val n = input.size
        val slack = if (n <= 4) 1 else 2 + n / 8
        val results = ArrayList<Pair<String, Float>>()
        for (m in max(1, n - slack)..(n + slack)) {
            val refs = byCount[m] ?: continue
            for (ref in refs) results += ref.char to score(input, ref.strokes)
        }
        results.sortBy { it.second }
        return results.take(limit)
    }

    /** How unlike [a] (written) and [b] (reference) are: 0 for the same shape. */
    private fun score(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        val n = a.size
        val m = b.size
        // every pair's cost: how far apart their points are, a little more when backwards, and
        // a little more the further apart they come in the writing order
        val costs = ArrayList<Triple<Int, Int, Float>>(n * m)
        for (i in 0 until n) for (j in 0 until m) {
            val forward = distance(a[i], b[j], false)
            val backward = distance(a[i], b[j], true) + BACKWARDS
            val order = ORDER * abs(i.toFloat() / max(1, n - 1) - j.toFloat() / max(1, m - 1))
            costs += Triple(i, j, min(forward, backward) + order)
        }
        costs.sortBy { it.third }
        val usedA = BooleanArray(n)
        val usedB = BooleanArray(m)
        var total = 0f
        var paired = 0
        for ((i, j, c) in costs) {
            if (usedA[i] || usedB[j]) continue
            usedA[i] = true
            usedB[j] = true
            total += c
            if (++paired == min(n, m)) break
        }
        total += UNPAIRED * (max(n, m) - paired)
        return total / max(n, m)
    }

    private fun distance(a: FloatArray, b: FloatArray, reversed: Boolean): Float {
        var sum = 0f
        for (k in 0 until POINTS) {
            val kb = if (reversed) POINTS - 1 - k else k
            sum += hypot(a[2 * k] - b[2 * kb], a[2 * k + 1] - b[2 * kb + 1])
        }
        return sum / POINTS
    }

    companion object {
        /** Points each stroke is reduced to. */
        const val POINTS = 8
        private const val BACKWARDS = 0.08f
        private const val ORDER = 0.06f
        private const val UNPAIRED = 0.45f

        /**
         * Strokes scaled into a unit box (keeping their proportions, centred) and resampled to
         * [POINTS] evenly spaced points each; null when there is nothing to see.
         */
        fun shape(strokes: List<FloatArray>): Array<FloatArray>? {
            val kept = strokes.filter { it.size >= 2 }
            if (kept.isEmpty()) return null
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (s in kept) {
                var k = 0
                while (k + 1 < s.size) {
                    minX = min(minX, s[k]); maxX = max(maxX, s[k])
                    minY = min(minY, s[k + 1]); maxY = max(maxY, s[k + 1])
                    k += 2
                }
            }
            val side = max(max(maxX - minX, maxY - minY), 1e-3f)
            val offX = (side - (maxX - minX)) / 2 - minX
            val offY = (side - (maxY - minY)) / 2 - minY
            return Array(kept.size) { i ->
                val s = kept[i]
                val pts = FloatArray(s.size)
                var k = 0
                while (k + 1 < s.size) {
                    pts[k] = (s[k] + offX) / side
                    pts[k + 1] = (s[k + 1] + offY) / side
                    k += 2
                }
                resample(pts)
            }
        }

        /** [POINTS] points spaced evenly along the stroke [pts] (x, y pairs). */
        private fun resample(pts: FloatArray): FloatArray {
            val count = pts.size / 2
            val out = FloatArray(POINTS * 2)
            if (count == 1) {
                for (k in 0 until POINTS) { out[2 * k] = pts[0]; out[2 * k + 1] = pts[1] }
                return out
            }
            val cumulative = FloatArray(count)
            for (k in 1 until count) {
                cumulative[k] = cumulative[k - 1] +
                    hypot(pts[2 * k] - pts[2 * k - 2], pts[2 * k + 1] - pts[2 * k - 1])
            }
            val length = cumulative[count - 1]
            var seg = 1
            for (k in 0 until POINTS) {
                val target = if (length == 0f) 0f else length * k / (POINTS - 1)
                while (seg < count - 1 && cumulative[seg] < target) seg++
                val span = cumulative[seg] - cumulative[seg - 1]
                val t = if (span <= 0f) 0f else ((target - cumulative[seg - 1]) / span).coerceIn(0f, 1f)
                out[2 * k] = pts[2 * seg - 2] + (pts[2 * seg] - pts[2 * seg - 2]) * t
                out[2 * k + 1] = pts[2 * seg - 1] + (pts[2 * seg + 1] - pts[2 * seg - 1]) * t
            }
            return out
        }

        /**
         * Make Me a Hanzi's medians ("[[[x,y],[x,y],...],...]", y up from 900) as strokes in
         * screen coordinates (y down).
         */
        fun parseMedians(json: String): List<FloatArray> {
            val strokes = ArrayList<FloatArray>()
            var current: ArrayList<Float>? = null
            var depth = 0
            var number = StringBuilder()
            val pair = ArrayList<Float>(2)
            fun flushNumber() {
                if (number.isNotEmpty()) {
                    pair += number.toString().toFloat()
                    number = StringBuilder()
                }
            }
            for (ch in json) {
                when (ch) {
                    '[' -> {
                        depth++
                        if (depth == 2) current = ArrayList()
                    }
                    ']' -> {
                        flushNumber()
                        if (depth == 3 && pair.size == 2) {
                            current?.add(pair[0])
                            current?.add(900f - pair[1])
                        }
                        if (depth == 3) pair.clear()
                        if (depth == 2) current?.let { strokes += it.toFloatArray() }
                        depth--
                    }
                    ',' -> flushNumber()
                    else -> if (ch.isDigit() || ch == '-' || ch == '.') number.append(ch)
                }
            }
            return strokes
        }
    }
}
