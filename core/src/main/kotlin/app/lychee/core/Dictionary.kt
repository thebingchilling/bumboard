package app.lychee.core

/** Languages in readings.db. [Any] marks per-character definitions from Unihan. */
enum class Lang(val code: String) {
    Cantonese("yue"), Mandarin("cmn"), Any("*");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Any
    }
}

data class Sense(
    val lang: Lang,
    /** reading this sense belongs to (numbered syllables), when known */
    val reading: String?,
    val gloss: String,
    val partOfSpeech: String? = null,
    val register: String? = null,
    val label: String? = null,
    /** Standard Written Chinese equivalent (TypeDuck) */
    val written: String? = null,
    /** Cantonese vernacular equivalent (TypeDuck) */
    val vernacular: String? = null,
    val collocation: String? = null,
    val source: String = "",
    /** the reading in the other language (CEDICT pinyin -> Jyutping), when known */
    val alt: String? = null,
)

/** What readings.db knows about a word beyond its readings and meanings. */
data class WordInfo(
    /** HSK 2.0 level, 1-6 (Mandarin) */
    val hsk: Int? = null,
    /** Cantonese frequency band: 1 common, 2 everyday, 3 advanced */
    val band: Int? = null,
    /** "col" colloquial (口語) or "wri" written (書面) Cantonese */
    val register: String? = null,
    /** measure words as CEDICT writes them: "個|个[ge4],隻|只[zhi1]" */
    val measureWords: String? = null,
    /** jieba's part-of-speech tag (n, v, a ...) */
    val partOfSpeech: String? = null,
) {
    /** Measure words as (Traditional, Simplified, pinyin) */
    fun measureWordList(): List<Triple<String, String, String>> =
        measureWords.orEmpty().split(',').mapNotNull { item ->
            val m = Regex("^([^|\\[]+)(?:\\|([^\\[]+))?\\[([^\\]]+)]$").matchEntire(item.trim())
                ?: return@mapNotNull null
            val trad = m.groupValues[1]
            Triple(trad, m.groupValues[2].ifEmpty { trad }, m.groupValues[3])
        }

    companion object {
        val None = WordInfo()

        /** jieba's tags, in words */
        val PARTS_OF_SPEECH = mapOf(
            "n" to "noun", "nr" to "name", "ns" to "place", "nt" to "organisation", "nz" to "proper noun",
            "ng" to "noun", "v" to "verb", "vn" to "verb, noun", "vd" to "verb, adverb", "vg" to "verb",
            "a" to "adjective", "ad" to "adjective, adverb", "an" to "adjective, noun", "ag" to "adjective",
            "d" to "adverb", "dg" to "adverb", "m" to "number", "mq" to "number", "q" to "measure word",
            "r" to "pronoun", "rr" to "pronoun", "rz" to "pronoun", "p" to "preposition", "c" to "conjunction",
            "u" to "particle", "uj" to "particle", "ul" to "particle", "uz" to "particle", "ud" to "particle",
            "ug" to "particle", "uv" to "particle", "y" to "particle", "e" to "interjection",
            "o" to "onomatopoeia", "i" to "idiom", "l" to "set phrase", "j" to "abbreviation",
            "t" to "time word", "tg" to "time word", "f" to "direction", "s" to "place word",
            "b" to "attributive", "z" to "descriptive", "zg" to "descriptive", "k" to "suffix", "h" to "prefix",
        )
    }
}

data class Headword(
    val id: Long,
    val traditional: String,
    val simplified: String,
    val frequency: Long,
    /** readings per language, most common first */
    val readings: Map<Lang, List<String>>,
    val senses: List<Sense>,
    val info: WordInfo = WordInfo.None,
)

/** Looks up every headword written as [form] (Traditional or Simplified). */
fun interface Dictionary {
    fun lookup(form: String): List<Headword>
}
