package app.lychee.typing

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * What is typed next, letter by letter: the chance of each letter (or of the word ending)
 * after what has been typed of the word so far, from the keyboard's own word list.
 *
 * Two levels, like the mainstream keyboards' key models:
 *  - the word's beginning itself (its first letters, up to [MAX_PREFIX]): after "qu" the next
 *    letter is among those that real words starting "qu" go on with;
 *  - for longer words and beginnings no word has, the two letters before (a letter trigram).
 * Each backs off to the level below it, so nothing typed is ever impossible.
 */
class LetterModel private constructor(
    /** trigram counts: (two letters before, a-z or the start) x (next: a-z or the end) */
    private val counts: FloatArray,
    /** the words' beginnings, sorted, and after each the counts of what comes next */
    private val prefixes: Array<String>,
    private val prefixCounts: FloatArray,
    /** an average word's weight: how much the smoothing below counts for */
    private val unit: Float,
) {
    private val bigram = FloatArray(N * N)
    private val unigram = FloatArray(N)
    private val triTotal = FloatArray(N * N)
    private val biTotal = FloatArray(N)
    private var uniTotal = 0f
    private val prefixTotal = FloatArray(prefixes.size)

    init {
        for (a in 0 until N) for (b in 0 until N) for (x in 0 until N) {
            val c = counts[(a * N + b) * N + x]
            if (c == 0f) continue
            triTotal[a * N + b] += c
            bigram[b * N + x] += c
            biTotal[b] += c
            unigram[x] += c
            uniTotal += c
        }
        for (i in prefixes.indices) for (x in 0 until N) prefixTotal[i] += prefixCounts[i * N + x]
    }

    /**
     * ln P([next] | [word]): [next] a letter, or a space for the end of the word. [word] is what
     * has been typed of the word; [whole] says it is all of it (not just its last letters).
     */
    fun logProb(word: CharSequence, next: Char, whole: Boolean = true): Double {
        val x = if (next == ' ') END else index(next)
        if (x < 0) return ln(FLOOR)
        val k = K * unit
        // a letter trigram, backing off to the letter pair and the letter alone
        val b = if (word.isNotEmpty()) index(word[word.length - 1]).let { if (it < 0) START else it } else START
        val a = if (word.length > 1) index(word[word.length - 2]).let { if (it < 0) START else it } else START
        val pUni = (unigram[x] + unit) / (uniTotal + N * unit)
        val pBi = (bigram[b * N + x] + k * pUni) / (biTotal[b] + k)
        var p = (counts[(a * N + b) * N + x] + k * pBi) / (triTotal[a * N + b] + k)
        // the word's own beginning, when real words begin like it
        if (whole && word.length <= MAX_PREFIX) {
            val i = prefixes.binarySearch(lower(word))
            if (i >= 0) p = (prefixCounts[i * N + x] + k * p) / (prefixTotal[i] + k)
        }
        // an unlikely letter is just unlikely: never so unlikely that a press well inside its
        // key goes to a neighbour
        return ln(p.coerceAtLeast(FLOOR))
    }

    fun write(out: DataOutputStream) {
        out.writeInt(FORMAT)
        out.writeFloat(unit)
        out.writeInt(counts.size)
        counts.forEach { out.writeFloat(it) }
        out.writeInt(prefixes.size)
        prefixes.forEachIndexed { i, p ->
            out.writeUTF(p)
            for (x in 0 until N) out.writeFloat(prefixCounts[i * N + x])
        }
    }

    /**
     * Collects words (with how common each is) and makes a model of them. With [prefixes] the
     * words' beginnings count too (for a language typed word by word; not for romanisation
     * typed as one run of syllables).
     */
    class Builder(private val prefixes: Boolean = true) {
        private val counts = FloatArray(N * N * N)
        private val words = ArrayList<String>()
        private val weights = ArrayList<Float>()
        private var total = 0.0
        private var number = 0

        /** Adds [word] (letters a-z; anything else is skipped) seen with [weight]. */
        fun add(word: CharSequence, weight: Double) {
            if (weight <= 0) return
            val letters = lower(word)
            if (letters.isEmpty()) return
            var a = START
            var b = START
            val w = weight.toFloat()
            for (ch in letters) {
                val x = ch - 'a'
                counts[(a * N + b) * N + x] += w
                a = b
                b = x
            }
            counts[(a * N + b) * N + END] += w
            total += weight
            number++
            if (prefixes) {
                words += letters
                weights += w
            }
        }

        fun build(): LetterModel {
            val unit = if (number > 0) (total / number).toFloat() else 1f
            if (!prefixes || words.isEmpty()) return LetterModel(counts, emptyArray(), FloatArray(0), unit)
            // in order, words sharing a beginning are together: each beginning's counts are
            // gathered in one pass, and kept only when enough words share it
            val order = words.indices.sortedWith { i, j -> words[i].compareTo(words[j]) }
            val keep = (total * MIN_SHARE).toFloat()
            val kept = ArrayList<Pair<String, FloatArray>>()
            val open = arrayOfNulls<String>(MAX_PREFIX + 1)
            val openCounts = Array(MAX_PREFIX + 1) { FloatArray(N) }
            fun flush(length: Int) {
                val p = open[length] ?: return
                val c = openCounts[length]
                if (c.sum() >= keep) kept += p to c.copyOf()
                c.fill(0f)
                open[length] = null
            }
            for (i in order) {
                val word = words[i]
                val w = weights[i]
                for (length in 0..minOf(MAX_PREFIX, word.length)) {
                    val p = word.substring(0, length)
                    if (open[length] != p) {
                        flush(length)
                        open[length] = p
                    }
                    val next = if (length < word.length) word[length] - 'a' else END
                    openCounts[length][next] += w
                }
            }
            for (length in 0..MAX_PREFIX) flush(length)
            val best = kept.sortedByDescending { it.second.sum() }.take(MAX_PREFIXES).sortedBy { it.first }
            val counts2 = FloatArray(best.size * N)
            best.forEachIndexed { i, (_, c) -> c.copyInto(counts2, i * N) }
            return LetterModel(counts, Array(best.size) { best[it].first }, counts2, unit)
        }
    }

    companion object {
        private const val N = 27
        /** as what came before: the start of the word; as what comes next: its end */
        private const val START = 26
        private const val END = 26
        private const val K = 1.5f
        private const val FLOOR = 0.02
        /** how long a word's beginning may be to count on its own */
        const val MAX_PREFIX = 6
        /** the most beginnings kept, and the least share of all words one needs */
        private const val MAX_PREFIXES = 20000
        private const val MIN_SHARE = 2e-6
        private const val FORMAT = 2

        /** A model kept with [write]; null when it isn't one. */
        fun read(input: DataInputStream): LetterModel? = runCatching {
            if (input.readInt() != FORMAT) return null
            val unit = input.readFloat()
            val n = input.readInt()
            if (n != N * N * N) return null
            val counts = FloatArray(n) { input.readFloat() }
            val size = input.readInt()
            val prefixes = arrayOfNulls<String>(size)
            val prefixCounts = FloatArray(size * N)
            for (i in 0 until size) {
                prefixes[i] = input.readUTF()
                for (x in 0 until N) prefixCounts[i * N + x] = input.readFloat()
            }
            @Suppress("UNCHECKED_CAST")
            LetterModel(counts, prefixes as Array<String>, prefixCounts, unit)
        }.getOrNull()

        private fun index(ch: Char): Int {
            val c = ch.lowercaseChar()
            return if (c in 'a'..'z') c - 'a' else -1
        }

        private fun lower(word: CharSequence): String {
            val sb = StringBuilder(word.length)
            for (ch in word) {
                val c = ch.lowercaseChar()
                if (c in 'a'..'z') sb.append(c)
            }
            return sb.toString()
        }

        /** A word list's frequency (probability, count ...) as a counting weight. */
        fun weightOf(frequency: Double): Double = sqrt(frequency.coerceAtLeast(0.0))
    }
}
