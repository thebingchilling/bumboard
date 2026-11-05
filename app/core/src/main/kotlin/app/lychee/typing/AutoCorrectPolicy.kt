package app.lychee.typing

/**
 * Lychee's say on English auto-correction, on top of LatinIME's score threshold: firmer with
 * jumbled words (letters swapped or scrambled: "teh", "becuase"; or no word shape at all:
 * "sdfg"), gentler with words that look like words but aren't in the dictionary ("bruh",
 * "nah", names, slang), which stay as typed when the nearest word is more than one slip away.
 */
object AutoCorrectPolicy {

    enum class Decision {
        /** LatinIME's threshold decides. */
        DEFAULT,
        /** Leave the typed word alone. */
        KEEP,
        /** Correct to the suggestion, even below the threshold. */
        CORRECT,
    }

    /** Informal words and abbreviations people type on purpose, never corrected. */
    val INFORMAL: Set<String> = setOf(
        "bruh", "bro", "nah", "naw", "yeah", "yea", "ya", "yah", "yep", "yup", "nope", "lol", "lmao",
        "lmfao", "rofl", "omg", "omfg", "idk", "idc", "imo", "imho", "tbh", "tbf", "smh", "fyi", "btw",
        "brb", "afk", "irl", "ngl", "fr", "rn", "ty", "thx", "pls", "plz", "gonna", "wanna", "gotta",
        "kinda", "sorta", "dunno", "lemme", "gimme", "y'all", "yall", "ain't", "aint", "dude", "fam",
        "sus", "lit", "vibe", "vibes", "ok", "okay", "okey", "k", "kk", "hmm", "hmmm", "huh", "meh",
        "ugh", "eh", "oh", "ah", "aww", "aw", "yay", "woah", "whoa", "wow", "haha", "hahaha", "hehe",
        "xd", "xoxo", "bff", "gg", "wp", "np", "nvm", "jk", "ikr", "asap", "tho", "cuz", "coz", "bc",
        "u", "ur", "r", "y", "w", "b4", "gr8", "luv", "cya", "ttyl", "sis", "bestie", "bae", "slay",
    )

    /** What to do with [typed] when LatinIME would offer [suggestion] as its correction. */
    fun decide(typed: String, suggestion: String): Decision {
        val word = typed.lowercase()
        val target = suggestion.lowercase()
        if (word.isEmpty() || word == target) return Decision.DEFAULT
        if (word in INFORMAL) return Decision.KEEP
        if (word.length < 3 || !word.all { it.isLetter() || it == '\'' }) return Decision.DEFAULT
        // the same letters in another order: a jumble, corrected
        if (word.toCharArray().sorted() == target.toCharArray().sorted()) return Decision.CORRECT
        // nothing like a word: corrected
        if (!wordLike(word)) return Decision.CORRECT
        // a word-like word more than one slip from the suggestion: kept as typed
        if (distance(word, target) >= 2) return Decision.KEEP
        return Decision.DEFAULT
    }

    private const val VOWELS = "aeiouy"

    /** Whether [word] has the shape of an English word: a vowel, and no long runs of consonants
     *  or vowels or of one letter. */
    fun wordLike(word: String): Boolean {
        val letters = word.filter { it.isLetter() }
        if (letters.none { it in VOWELS }) return false
        var consonants = 0
        var vowels = 0
        var same = 1
        for ((i, c) in letters.withIndex()) {
            if (c in VOWELS) { vowels++; consonants = 0 } else { consonants++; vowels = 0 }
            same = if (i > 0 && letters[i - 1] == c) same + 1 else 1
            if (consonants > 5 || vowels > 3 || same > 2) return false
        }
        return true
    }

    /** Edit distance with a swap of neighbouring letters counting as one edit. */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }
}
