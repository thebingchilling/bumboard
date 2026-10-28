package app.bumboard.korean

/**
 * Hangul composition for the 2-set (두벌식) layout: compatibility jamo typed one at a time
 * become syllables, as on Gboard. Pure logic, no data; see [HangulComposer].
 */
object Hangul {
    /** Initial consonants in Unicode syllable order. */
    const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    /** Vowels in Unicode syllable order. */
    const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    /** Final consonants in Unicode syllable order (index 0: none). */
    const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val VOWEL_PAIRS = mapOf(
        "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ',
        "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ',
    )
    private val FINAL_PAIRS = mapOf(
        "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ',
        "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ',
    )
    /** A compound final split back into its two consonants. */
    private val FINAL_SPLIT = FINAL_PAIRS.entries.associate { (pair, c) -> c to (pair[0] to pair[1]) }

    fun isJamo(code: Int) = code in 0x3131..0x3163
    fun isVowel(c: Char) = c in JUNG
    fun isConsonant(c: Char) = c in CHO || c in JONG.substring(1)

    fun combineVowels(a: Char, b: Char): Char? = VOWEL_PAIRS["$a$b"]
    fun combineFinals(a: Char, b: Char): Char? = FINAL_PAIRS["$a$b"]
    fun splitFinal(c: Char): Pair<Char, Char>? = FINAL_SPLIT[c]
    fun canBeFinal(c: Char) = c != ' ' && JONG.indexOf(c) > 0
    fun canBeInitial(c: Char) = c in CHO

    /** The syllable for [cho] + [jung] (+ [jong]), or the lone jamo when it is incomplete. */
    fun compose(cho: Char?, jung: Char?, jong: Char?): String {
        if (cho != null && jung != null) {
            val l = CHO.indexOf(cho)
            val v = JUNG.indexOf(jung)
            val t = if (jong == null) 0 else JONG.indexOf(jong).coerceAtLeast(0)
            if (l >= 0 && v >= 0) return (0xAC00 + (l * 21 + v) * 28 + t).toChar().toString()
        }
        return listOfNotNull(cho, jung, jong).joinToString("")
    }
}

/** One syllable being typed. */
data class Syllable(val cho: Char? = null, val jung: Char? = null, val jong: Char? = null) {
    val isEmpty get() = cho == null && jung == null && jong == null
    val text get() = Hangul.compose(cho, jung, jong)
}

/**
 * Builds syllables from jamo. [type] returns the text to commit (the syllables now finished, or
 * "") and leaves the one still open in [current]; [backspace] undoes the last jamo of it.
 */
class HangulComposer {
    var current = Syllable()
        private set
    /** States of [current] before each jamo, for backspace. */
    private val history = ArrayDeque<Syllable>()

    val isComposing get() = !current.isEmpty

    fun reset() {
        current = Syllable()
        history.clear()
    }

    /** Takes all of [current] out as finished text. */
    fun finish(): String {
        val text = current.text
        reset()
        return text
    }

    fun type(jamo: Char): String {
        val s = current
        val committed = StringBuilder()
        fun start(next: Syllable) {
            committed.append(s.text)
            history.clear()
            current = next
        }
        fun extend(next: Syllable) {
            history.addLast(s)
            current = next
        }
        if (Hangul.isVowel(jamo)) {
            when {
                s.isEmpty -> extend(Syllable(jung = jamo))
                s.jung == null -> extend(s.copy(jung = jamo))   // consonant alone: it becomes the initial
                s.jong == null -> {
                    val pair = Hangul.combineVowels(s.jung, jamo)
                    if (pair != null) extend(s.copy(jung = pair)) else start(Syllable(jung = jamo))
                }
                else -> {
                    // the final consonant moves on to start the next syllable (닭+ㅏ → 달가)
                    val split = Hangul.splitFinal(s.jong)
                    val (keep, move) = if (split != null) split.first to split.second else null to s.jong
                    if (Hangul.canBeInitial(move)) {
                        committed.append(s.copy(jong = keep).text)
                        history.clear()
                        history.addLast(Syllable(cho = move))
                        current = Syllable(cho = move, jung = jamo)
                    } else {
                        start(Syllable(jung = jamo))
                    }
                }
            }
        } else {
            when {
                s.isEmpty -> extend(Syllable(cho = jamo))
                s.cho == null || s.jung == null -> start(Syllable(cho = jamo))
                s.jong == null ->
                    if (Hangul.canBeFinal(jamo)) extend(s.copy(jong = jamo)) else start(Syllable(cho = jamo))
                else -> {
                    val pair = Hangul.combineFinals(s.jong, jamo)
                    if (pair != null) extend(s.copy(jong = pair)) else start(Syllable(cho = jamo))
                }
            }
        }
        return committed.toString()
    }

    /** Undoes the last jamo; false when nothing was being composed. */
    fun backspace(): Boolean {
        if (current.isEmpty) return false
        current = history.removeLastOrNull() ?: Syllable()
        return true
    }
}
