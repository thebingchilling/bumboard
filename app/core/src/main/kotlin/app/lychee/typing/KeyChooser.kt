package app.lychee.typing

/**
 * Which key a press meant, the way the mainstream keyboards decide: not just the key under the
 * finger, but the likeliest of the keys around it, from
 *  - where the press landed against where this person's presses for each key land (each key's
 *    aim, from [TouchModel]): a bell curve around the aim, so the further off, the less likely;
 *  - how likely each key's letter is next in the word being typed ([LetterModel]), the space
 *    bar standing for the end of the word.
 * In effect the likely next keys grow and the unlikely ones shrink, without the keys moving.
 * A press near a key's aim always types that key, however unlikely its letter.
 */
object KeyChooser {
    /**
     * One key near the press: [symbol] its letter (a space for the space bar), ([dx], [dy])
     * the press from the key's aim, in key widths and heights.
     */
    class Candidate(val symbol: Char, val dx: Float, val dy: Float)

    /** How spread out presses for one key are (in key sizes): the bell curve's width. */
    private const val SIGMA_X = 0.3f
    private const val SIGMA_Y = 0.3f
    /** How much the letter's likelihood counts against where the press landed. */
    private const val WEIGHT = 0.5
    /** Keys further than this from the press (in key sizes, from their aim) aren't considered. */
    const val REACH = 0.95f

    /**
     * The index in [candidates] of the key meant; [hit] is the key under the finger (chosen
     * when nothing else is likelier). [model] null: by where the press landed alone.
     */
    fun choose(model: LetterModel?, word: CharSequence, whole: Boolean, candidates: List<Candidate>, hit: Int): Int {
        var best = hit
        var bestScore = Double.NEGATIVE_INFINITY
        candidates.forEachIndexed { i, c ->
            if (i != hit && (kotlin.math.abs(c.dx) > REACH || kotlin.math.abs(c.dy) > REACH)) return@forEachIndexed
            val score = spatial(c) + (model?.let { WEIGHT * it.logProb(word, c.symbol, whole) } ?: 0.0)
            // the key under the finger wins a tie
            if (score > bestScore || (score == bestScore && i == hit)) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    /** ln of the bell curve (without its constant) for a press ([dx], [dy]) from a key's aim. */
    fun spatial(c: Candidate): Double {
        val x = c.dx / SIGMA_X
        val y = c.dy / SIGMA_Y
        return -0.5 * (x * x + y * y)
    }
}
