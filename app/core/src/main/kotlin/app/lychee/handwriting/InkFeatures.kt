package app.lychee.handwriting

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The ink as Lychee's handwriting recogniser reads it: exactly the steps of the training side
 * (packs/handwriting/ink.py), in the same order and precision, so the model sees on the phone
 * what it learnt from (InkFeaturesTest checks this against numbers from ink.py).
 *
 *  1. The strokes are centred on their bounding box and scaled so its longer side is 1.
 *  2. They are resampled along their length to [pointCount] points in all; each stroke gets its
 *     share by length (a dot one point, any other stroke at least two), ends included.
 *  3. Each point is 6 numbers: x, y, the step from the point before times 10 (across a pen lift
 *     too), whether it starts a stroke and whether it ends one.
 */
object InkFeatures {
    const val FEATURES = 6
    private const val STEP = 0.04
    private const val MIN_POINTS = 32
    private const val MAX_POINTS = 256

    fun pointCount(totalLength: Double, strokes: Int): Int {
        var n = ceil(totalLength / STEP / 16.0).toInt() * 16
        n = min(MAX_POINTS, max(MIN_POINTS, n))
        return max(n, 2 * strokes)
    }

    /**
     * The features of [strokes] (each x, y pairs; y down), [points] x 6 floats row by row, or
     * null when there is no ink.
     */
    fun of(strokes: List<FloatArray>): Pair<FloatArray, Int>? {
        val ink = strokes.filter { it.size >= 2 }.map { s -> DoubleArray(s.size / 2 * 2) { s[it].toDouble() } }
        if (ink.isEmpty()) return null
        var loX = Double.MAX_VALUE; var loY = Double.MAX_VALUE
        var hiX = -Double.MAX_VALUE; var hiY = -Double.MAX_VALUE
        for (s in ink) {
            var k = 0
            while (k < s.size) {
                loX = min(loX, s[k]); hiX = max(hiX, s[k])
                loY = min(loY, s[k + 1]); hiY = max(hiY, s[k + 1])
                k += 2
            }
        }
        var side = max(hiX - loX, hiY - loY)
        if (side <= 0) side = 1.0
        val cx = (loX + hiX) / 2.0
        val cy = (loY + hiY) / 2.0
        val norm = ink.map { s -> DoubleArray(s.size) { i -> (s[i] - (if (i % 2 == 0) cx else cy)) / side } }
        val lengths = DoubleArray(norm.size) { i ->
            val s = norm[i]
            var sum = 0.0
            var k = 2
            while (k < s.size) {
                val dx = s[k] - s[k - 2]
                val dy = s[k + 1] - s[k - 1]
                sum += sqrt(dx * dx + dy * dy)
                k += 2
            }
            sum
        }
        var total = 0.0
        for (l in lengths) total += l
        val n = pointCount(total, norm.size)
        val counts = IntArray(norm.size) { i -> if (norm[i].size > 2 && lengths[i] > 0) 2 else 1 }
        val rest = n - counts.sum()
        if (rest > 0 && total > 0) {
            val shares = DoubleArray(norm.size) { rest * lengths[it] / total }
            val whole = IntArray(norm.size) { floor(shares[it]).toInt() }
            for (i in counts.indices) counts[i] += whole[i]
            val left = rest - whole.sum()
            val order = norm.indices.sortedWith(compareBy<Int>({ -(shares[it] - whole[it]) }, { it }))
            for (i in order.take(left)) counts[i]++
        } else if (rest > 0) {
            counts[0] += rest
        }
        val points = counts.sum()
        val out = FloatArray(points * FEATURES)
        var row = 0
        var px = 0.0; var py = 0.0
        var first = true
        val xy = DoubleArray(2)
        for ((si, s) in norm.withIndex()) {
            val c = counts[si]
            val pts = s.size / 2
            val seg = DoubleArray(max(0, pts - 1)) { j ->
                val dx = s[2 * j + 2] - s[2 * j]
                val dy = s[2 * j + 3] - s[2 * j + 1]
                sqrt(dx * dx + dy * dy)
            }
            val cum = DoubleArray(pts)
            for (j in seg.indices) cum[j + 1] = cum[j] + seg[j]
            var j = 0
            for (m in 0 until c) {
                if (c == 1 || pts == 1) {
                    xy[0] = s[0]; xy[1] = s[1]
                } else {
                    val t = cum[pts - 1] * m / (c - 1)
                    // the first segment whose far end is at least t along
                    while (j < seg.size - 1 && cum[j + 1] < t) j++
                    val sj = seg[j]
                    val f = if (sj > 0) min(1.0, max(0.0, (t - cum[j]) / sj)) else 0.0
                    xy[0] = s[2 * j] + f * (s[2 * j + 2] - s[2 * j])
                    xy[1] = s[2 * j + 1] + f * (s[2 * j + 3] - s[2 * j + 1])
                }
                val o = row * FEATURES
                out[o] = xy[0].toFloat()
                out[o + 1] = xy[1].toFloat()
                if (!first) {
                    out[o + 2] = ((xy[0] - px) * 10.0).toFloat()
                    out[o + 3] = ((xy[1] - py) * 10.0).toFloat()
                }
                out[o + 4] = if (m == 0) 1f else 0f
                out[o + 5] = if (m == c - 1) 1f else 0f
                px = xy[0]; py = xy[1]
                first = false
                row++
            }
        }
        return out to points
    }
}
