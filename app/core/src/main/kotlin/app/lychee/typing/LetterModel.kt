package app.lychee.typing

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Which letter usually comes next: counts of each letter after the two before it in a
 * language's words (a, b ... z, and the start of a word), from the keyboard's own word list.
 * The keys use it to tell a sloppy press near the edge of two keys (see [choose]).
 */
class LetterModel private constructor(private val counts: FloatArray) {

    private val bigram = FloatArray(N * N)
    private val unigram = FloatArray(N)
    private val triTotal = FloatArray(N * N)
    private val biTotal = FloatArray(N)
    private var uniTotal = 0f

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
    }

    /** ln P([letter] | the last two letters of [context]); the start of a word counts as one. */
    fun logProb(context: CharSequence, letter: Char): Double {
        val x = index(letter)
        if (x < 0 || x == START) return ln(1e-6)
        val b = if (context.isNotEmpty()) index(context[context.length - 1]).let { if (it < 0) START else it } else START
        val a = if (context.length > 1) index(context[context.length - 2]).let { if (it < 0) START else it } else START
        // each level backs off to the one below it (add-k smoothing)
        val pUni = (unigram[x] + 1.0) / (uniTotal + N)
        val pBi = (bigram[b * N + x] + K * pUni) / (biTotal[b] + K)
        val pTri = (counts[(a * N + b) * N + x] + K * pBi) / (triTotal[a * N + b] + K)
        // an unlikely letter is just unlikely: never so unlikely that a press well inside its
        // key goes to a neighbour
        return ln(pTri.coerceAtLeast(FLOOR))
    }

    /** The counts, for keeping the model in a file. */
    fun toArray(): FloatArray = counts.copyOf()

    class Builder {
        private val counts = FloatArray(N * N * N)

        /** Adds [word] (letters a-z; anything else is skipped) seen with [weight]. */
        fun add(word: CharSequence, weight: Double) {
            if (weight <= 0) return
            var a = START
            var b = START
            val w = weight.toFloat()
            for (ch in word) {
                val x = index(ch)
                if (x < 0) continue
                counts[(a * N + b) * N + x] += w
                a = b
                b = x
            }
        }

        fun build() = LetterModel(counts)
    }

    companion object {
        private const val N = 27
        private const val START = 26
        private const val K = 2.0
        private const val FLOOR = 0.02

        /** Rebuilds a model from [toArray]'s counts; null when they don't fit. */
        fun fromArray(counts: FloatArray): LetterModel? = if (counts.size == N * N * N) LetterModel(counts.copyOf()) else null

        private fun index(ch: Char): Int {
            val c = ch.lowercaseChar()
            return if (c in 'a'..'z') c - 'a' else -1
        }

        /** How far a press may be from a key (in key widths) for that key still to be chosen. */
        const val REACH = 0.4f
        /** How quickly being further from a key outweighs its letter being likely. */
        private const val SPREAD = 0.25
        /** How much a letter's likelihood counts against the distance. */
        private const val WEIGHT = 0.55

        /**
         * Of [candidates] (each letter with its distance from the press, in key widths: 0 for
         * the key pressed), the index of the one to type: the key pressed unless a neighbour
         * the press nearly reached is much likelier after [context]. A press well inside a key
         * never changes; one on the edge between two goes to the likelier letter.
         */
        fun choose(model: LetterModel, context: CharSequence, candidates: List<Pair<Char, Float>>): Int {
            var best = 0
            var bestScore = Double.NEGATIVE_INFINITY
            candidates.forEachIndexed { i, (letter, distance) ->
                if (distance > REACH) return@forEachIndexed
                val d = distance / SPREAD
                val score = -d * d + WEIGHT * model.logProb(context, letter)
                if (score > bestScore) {
                    bestScore = score
                    best = i
                }
            }
            return best
        }

        /** A word list's frequency (probability, count ...) as a counting weight. */
        fun weightOf(frequency: Double): Double = sqrt(frequency.coerceAtLeast(0.0))
    }
}
