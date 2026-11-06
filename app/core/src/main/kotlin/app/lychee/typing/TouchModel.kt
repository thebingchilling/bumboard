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
        add(i, dx, dy)
        add(ALL, dx, dy)
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
    fun toArray(): FloatArray = n + mx + my

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
            if (a.size != SIZE * 3) return null
            return TouchModel().also { t ->
                a.copyInto(t.n, 0, 0, SIZE)
                a.copyInto(t.mx, 0, SIZE, SIZE * 2)
                a.copyInto(t.my, 0, SIZE * 2, SIZE * 3)
            }
        }

        private fun index(ch: Char): Int {
            val c = ch.lowercaseChar()
            return if (c in 'a'..'z') c - 'a' else -1
        }
    }
}
