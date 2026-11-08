package app.lychee.typing

import kotlin.math.abs

/**
 * Where this person's finger lands when they mean a key. Everyone presses a little off the
 * keys' centres, the same way each time (lower than the centre, towards the hand they type
 * with, further off near the edges): the mainstream keyboards learn it and move each key's
 * aim to where its presses land. Learnt from presses that stayed typed (not deleted), in key
 * widths and heights from the key's centre; each key starts from the average of all keys and
 * goes its own way as its presses add up.
 */
class TouchModel {
    /** per letter a-z, and [ALL] for every key together: presses, mean x and y offset */
    private val n = FloatArray(SIZE)
    private val mx = FloatArray(SIZE)
    private val my = FloatArray(SIZE)
    /** how far presses land from their key's own mean (squared, x and y): the spread */
    private var vx = DEFAULT_SPREAD * DEFAULT_SPREAD
    private var vy = DEFAULT_SPREAD * DEFAULT_SPREAD

    /** How far presses meaning [letter] land from its centre (x, y in key sizes). */
    @Synchronized
    fun offset(letter: Char): FloatArray {
        val all = ALL
        val gn = n[all]
        val gx = mx[all] * gn / (gn + SHRINK_ALL)
        val gy = my[all] * gn / (gn + SHRINK_ALL)
        val i = index(letter)
        if (i < 0) return floatArrayOf(gx, gy)
        val k = n[i]
        return floatArrayOf(
            ((k * mx[i] + SHRINK * gx) / (k + SHRINK)).coerceIn(-MAX, MAX),
            ((k * my[i] + SHRINK * gy) / (k + SHRINK)).coerceIn(-MAX, MAX),
        )
    }

    /** A press meaning [letter] landed ([dx], [dy]) from its centre, in key sizes. */
    @Synchronized
    fun learn(letter: Char, dx: Float, dy: Float) {
        val i = index(letter)
        // a press that far off was more likely meant for another key and fixed later
        if (i < 0 || abs(dx) > OUTLIER || abs(dy) > OUTLIER) return
        // the spread around where this key's presses usually land (once it has some)
        if (n[i] >= 5) {
            val rx = dx - mx[i]
            val ry = dy - my[i]
            val a = 1f / minOf(n[ALL] + 1f, MEMORY)
            vx += (rx * rx - vx) * a
            vy += (ry * ry - vy) * a
        }
        add(i, dx, dy)
        add(ALL, dx, dy)
    }

    /**
     * How spread out this person's presses are (x, y in key sizes): the touch model's bell
     * curve. The usual spread until enough presses are known; never tighter or looser than
     * keys allow.
     */
    @Synchronized
    fun spread(): FloatArray {
        val k = n[ALL]
        if (k < SPREAD_AFTER) return floatArrayOf(DEFAULT_SPREAD, DEFAULT_SPREAD)
        return floatArrayOf(
            kotlin.math.sqrt(vx).coerceIn(MIN_SPREAD, MAX_SPREAD),
            kotlin.math.sqrt(vy).coerceIn(MIN_SPREAD, MAX_SPREAD),
        )
    }

    private fun add(i: Int, dx: Float, dy: Float) {
        // an average of every press until there are many, then of the recent ones (habits,
        // phones and keyboard sizes change)
        n[i] = minOf(n[i] + 1f, MEMORY)
        val a = 1f / n[i]
        mx[i] += (dx - mx[i]) * a
        my[i] += (dy - my[i]) * a
    }

    @Synchronized
    fun presses(): Int = n[ALL].toInt()

    @Synchronized
    fun toArray(): FloatArray = n + mx + my + floatArrayOf(vx, vy)

    companion object {
        private const val SIZE = 27
        private const val ALL = 26
        /** presses a key needs before its own offset counts as much as everyone's */
        private const val SHRINK = 15f
        private const val SHRINK_ALL = 30f
        private const val MEMORY = 300f
        private const val MAX = 0.35f
        private const val OUTLIER = 0.75f

        fun fromArray(a: FloatArray): TouchModel? {
            // the first files had no spread
            if (a.size != SIZE * 3 && a.size != SIZE * 3 + 2) return null
            return TouchModel().also { t ->
                a.copyInto(t.n, 0, 0, SIZE)
                a.copyInto(t.mx, 0, SIZE, SIZE * 2)
                a.copyInto(t.my, 0, SIZE * 2, SIZE * 3)
                if (a.size == SIZE * 3 + 2) {
                    t.vx = a[SIZE * 3]
                    t.vy = a[SIZE * 3 + 1]
                }
            }
        }

        /** The usual spread of presses for a key (key sizes), and the limits of a learnt one. */
        const val DEFAULT_SPREAD = 0.3f
        private const val MIN_SPREAD = 0.22f
        private const val MAX_SPREAD = 0.4f
        /** presses before the learnt spread counts */
        private const val SPREAD_AFTER = 60f

        private fun index(ch: Char): Int {
            val c = ch.lowercaseChar()
            return if (c in 'a'..'z') c - 'a' else -1
        }
    }
}
