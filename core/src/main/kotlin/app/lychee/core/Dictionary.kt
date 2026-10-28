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

data class Headword(
    val id: Long,
    val traditional: String,
    val simplified: String,
    val frequency: Long,
    /** readings per language, most common first */
    val readings: Map<Lang, List<String>>,
    val senses: List<Sense>,
)

/** Looks up every headword written as [form] (Traditional or Simplified). */
fun interface Dictionary {
    fun lookup(form: String): List<Headword>
}
