package app.lychee.glide

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The words glide typing can produce, packed small: all letters in one CharArray, with offsets,
 * a 0..255 frequency each, and lists by first and last letter (a-z). Words with letters that are
 * not on the keys are left out. A 60 000-word list takes under 2 MB.
 */
class GlideWords private constructor(
    private val chars: CharArray,
    private val starts: IntArray,
    private val frequencies: ByteArray,
    /** Word indices for each (first, last) pair, first * 26 + last. */
    private val buckets: Array<IntArray>,
) {
    val size get() = frequencies.size

    fun word(i: Int) = String(chars, starts[i], starts[i + 1] - starts[i])
    fun frequency(i: Int) = frequencies[i].toInt() and 0xFF
    fun bucket(first: Char, last: Char): IntArray =
        if (first in 'a'..'z' && last in 'a'..'z') buckets[(first - 'a') * 26 + (last - 'a')] else EMPTY

    /** The word's letters on the keys, lower-cased, apostrophes dropped. */
    fun letters(i: Int): String {
        val sb = StringBuilder(starts[i + 1] - starts[i])
        for (k in starts[i] until starts[i + 1]) {
            val c = chars[k].lowercaseChar()
            if (c in 'a'..'z') sb.append(c)
        }
        return sb.toString()
    }

    class Builder(private val maxWords: Int = 60_000) {
        private val entries = ArrayList<Pair<String, Int>>()

        /** Adds [word] with [probability] (LatinIME's 0..255, or negative when unknown). */
        fun add(word: String, probability: Int) {
            if (word.length < 1 || word.length > 24 || probability < 0) return
            var letters = 0
            for (c in word) {
                val l = c.lowercaseChar()
                if (l in 'a'..'z') letters++ else if (c != '\'' && c != '-') return
            }
            if (letters == 0) return
            entries += word to probability.coerceIn(0, 255)
        }

        fun build(): GlideWords {
            val kept = entries.sortedByDescending { it.second }.take(maxWords)
            val total = kept.sumOf { it.first.length }
            val chars = CharArray(total)
            val starts = IntArray(kept.size + 1)
            val freqs = ByteArray(kept.size)
            val lists = Array(26 * 26) { ArrayList<Int>() }
            var at = 0
            kept.forEachIndexed { i, (w, p) ->
                starts[i] = at
                w.toCharArray(chars, at)
                at += w.length
                freqs[i] = p.toByte()
                val letters = w.lowercase().filter { it in 'a'..'z' }
                lists[(letters.first() - 'a') * 26 + (letters.last() - 'a')] += i
            }
            starts[kept.size] = at
            return GlideWords(chars, starts, freqs, Array(lists.size) { lists[it].toIntArray() })
        }
    }

    private companion object {
        val EMPTY = IntArray(0)
    }
}

/** A key's centre, in the same coordinates as the glide path. */
data class KeyCenter(val x: Float, val y: Float)

data class GlideMatch(val word: String, val score: Float)

/**
 * Decodes a glide path into words, SHARK²-style: candidates must start and end near the path's
 * ends and pass every letter's key in order; survivors are ranked by how close the path is to
 * each word's ideal path (position and shape) and by how common the word is.
 */
class GlideDecoder(private val words: GlideWords) {

    /**
     * [xs]/[ys] hold [count] path points; [keys] are the letter keys' centres; [keyWidth] is the
     * usual key width. Returns at most [limit] matches, best first (score: higher is better).
     */
    fun decode(xs: IntArray, ys: IntArray, count: Int, keys: Map<Char, KeyCenter>, keyWidth: Float, limit: Int = 18): List<GlideMatch> {
        val strict = decode(xs, ys, count, keys, keyWidth, limit, 1f)
        // a sloppy glide: rather a looser guess than nothing at all
        return strict.ifEmpty { decode(xs, ys, count, keys, keyWidth, limit, LOOSE) }
    }

    private fun decode(
        xs: IntArray, ys: IntArray, count: Int, keys: Map<Char, KeyCenter>, keyWidth: Float, limit: Int, slack: Float,
    ): List<GlideMatch> {
        if (count < 2 || keys.isEmpty() || keyWidth <= 0f) return emptyList()
        val path = simplify(xs, ys, count, keyWidth * 0.15f)
        if (path.size < 2) return emptyList()
        val reach = keyWidth * REACH * slack
        // how close the path comes to each letter key, and where
        val nearest = HashMap<Char, Float>(keys.size)
        for ((c, k) in keys) nearest[c] = path.minOf { hypot(it.x - k.x, it.y - k.y) }
        val starts = closestKeys(path.first(), keys, keyWidth * END_REACH * slack)
        val ends = closestKeys(path.last(), keys, keyWidth * END_REACH * slack)
        val pathLength = length(path)
        val sampledPath = resample(path, SAMPLES)
        val normalizedPath = normalize(sampledPath)

        val results = ArrayList<GlideMatch>()
        for (first in starts) for (last in ends) {
            for (i in words.bucket(first, last)) {
                val letters = words.letters(i)
                if (letters.isEmpty()) continue
                // every key on the way must be near the path
                if (letters.any { (nearest[it] ?: Float.MAX_VALUE) > reach }) continue
                val ideal = idealPath(letters, keys) ?: continue
                // a word much shorter or longer than the glide is unlikely
                val idealLength = length(ideal)
                val ratio = 1.8f * slack
                if (idealLength > pathLength * ratio + keyWidth || idealLength * ratio + keyWidth < pathLength) continue
                if (!inOrder(letters, keys, path, reach)) continue
                val sampledIdeal = resample(ideal, SAMPLES)
                val location = meanDistance(sampledPath, sampledIdeal) / keyWidth
                val shape = if (pathLength > keyWidth) meanDistance(normalizedPath, normalize(sampledIdeal)) else 0f
                val cost = location + SHAPE_WEIGHT * shape
                val prior = words.frequency(i) / 255f
                val score = exp(-cost * 3f) * (0.25f + prior)
                results += GlideMatch(words.word(i), score)
            }
        }
        results.sortByDescending { it.score }
        val seen = HashSet<String>()
        return results.filter { seen.add(it.word.lowercase()) }.take(limit)
    }

    private data class P(val x: Float, val y: Float)

    /** Drops points closer than [minStep] to the previous one. */
    private fun simplify(xs: IntArray, ys: IntArray, count: Int, minStep: Float): List<P> {
        val out = ArrayList<P>(count)
        for (i in 0 until min(count, min(xs.size, ys.size))) {
            val p = P(xs[i].toFloat(), ys[i].toFloat())
            val last = out.lastOrNull()
            if (last == null || hypot(p.x - last.x, p.y - last.y) >= minStep || i == count - 1) out += p
        }
        return out
    }

    private fun closestKeys(p: P, keys: Map<Char, KeyCenter>, radius: Float): List<Char> =
        keys.entries.map { it.key to hypot(p.x - it.value.x, p.y - it.value.y) }
            .filter { it.second <= radius }
            .sortedBy { it.second }
            .take(3)
            .map { it.first }

    /** The key centres the word passes through, with repeated letters (ll, ee) once. */
    private fun idealPath(letters: String, keys: Map<Char, KeyCenter>): List<P>? {
        val out = ArrayList<P>(letters.length)
        var previous = ' '
        for (c in letters) {
            if (c == previous) continue
            val k = keys[c] ?: return null
            out += P(k.x, k.y)
            previous = c
        }
        return out
    }

    /** Whether the path visits the word's keys one after another (not just anywhere). */
    private fun inOrder(letters: String, keys: Map<Char, KeyCenter>, path: List<P>, reach: Float): Boolean {
        var at = 0
        for (c in letters) {
            val k = keys[c] ?: return false
            var found = -1
            var i = at
            while (i < path.size) {
                if (hypot(path[i].x - k.x, path[i].y - k.y) <= reach) {
                    found = i
                    break
                }
                i++
            }
            if (found < 0) return false
            at = found
        }
        return true
    }

    private fun length(points: List<P>): Float {
        var total = 0f
        for (i in 1 until points.size) total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return total
    }

    /** [n] points evenly spaced along [points] (all the same point for a single key). */
    private fun resample(points: List<P>, n: Int): List<P> {
        if (points.size == 1) return List(n) { points[0] }
        val total = length(points)
        if (total == 0f) return List(n) { points[0] }
        val step = total / (n - 1)
        val out = ArrayList<P>(n)
        out += points[0]
        var carried = 0f
        var i = 1
        var prev = points[0]
        while (i < points.size && out.size < n) {
            val cur = points[i]
            val d = hypot(cur.x - prev.x, cur.y - prev.y)
            if (carried + d >= step && d > 0f) {
                val t = (step - carried) / d
                val q = P(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out += q
                prev = q
                carried = 0f
            } else {
                carried += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out += points.last()
        return out
    }

    /** Centred on the centroid and scaled to a unit box, for comparing shapes. */
    private fun normalize(points: List<P>): List<P> {
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        var w = 0f
        var h = 0f
        for (p in points) {
            w = max(w, abs(p.x - cx))
            h = max(h, abs(p.y - cy))
        }
        val s = max(w, h).takeIf { it > 0f } ?: 1f
        return points.map { P((it.x - cx) / s, (it.y - cy) / s) }
    }

    private fun meanDistance(a: List<P>, b: List<P>): Float {
        var total = 0f
        for (i in a.indices) total += hypot(a[i].x - b[i].x, a[i].y - b[i].y)
        return total / a.size
    }

    private companion object {
        const val SAMPLES = 32
        /** How far (in key widths) the path may pass from a letter's key centre. */
        const val REACH = 0.85f
        /** How far the glide may start or end from the first or last letter's key. */
        const val END_REACH = 1.25f
        const val SHAPE_WEIGHT = 0.6f
        /** How much further a second, looser pass reaches when the first finds nothing. */
        const val LOOSE = 1.6f
    }
}
