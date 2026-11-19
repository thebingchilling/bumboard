package app.lychee.core

/** One row of a conjugation table: what the form is, and the word in it. */
data class Conjugated(val label: String, val text: String)

/**
 * Japanese conjugation tables for the word card, by the conjugation class the Japanese pack
 * records for each word (JMdict's): v1 (ichidan), v5u v5k v5g v5s v5t v5n v5b v5m v5r (godan, by
 * ending), v5k-s (行く), v5r-i (ある), v5aru (いらっしゃる, ください...), v5u-s (問う), vk (来る),
 * vs (suru nouns: 勉強), vs-i (する itself and verbs ending in する), adj-i, adj-ix (いい/よい),
 * adj-na. Works on the word as written (食べる, 行く) or in kana (たべる).
 */
object JapaneseConjugation {

    /** Godan endings: dictionary ending -> its a, i, e, o rows and its te / ta forms. */
    private class Godan(val a: String, val i: String, val e: String, val o: String, val te: String, val ta: String)

    private val GODAN = mapOf(
        "v5u" to ('う' to Godan("わ", "い", "え", "お", "って", "った")),
        "v5k" to ('く' to Godan("か", "き", "け", "こ", "いて", "いた")),
        "v5k-s" to ('く' to Godan("か", "き", "け", "こ", "って", "った")),
        "v5g" to ('ぐ' to Godan("が", "ぎ", "げ", "ご", "いで", "いだ")),
        "v5s" to ('す' to Godan("さ", "し", "せ", "そ", "して", "した")),
        "v5t" to ('つ' to Godan("た", "ち", "て", "と", "って", "った")),
        "v5n" to ('ぬ' to Godan("な", "に", "ね", "の", "んで", "んだ")),
        "v5b" to ('ぶ' to Godan("ば", "び", "べ", "ぼ", "んで", "んだ")),
        "v5m" to ('む' to Godan("ま", "み", "め", "も", "んで", "んだ")),
        "v5r" to ('る' to Godan("ら", "り", "れ", "ろ", "って", "った")),
        "v5r-i" to ('る' to Godan("ら", "り", "れ", "ろ", "って", "った")),
        "v5aru" to ('る' to Godan("ら", "い", "れ", "ろ", "って", "った")),
        "v5u-s" to ('う' to Godan("わ", "い", "え", "お", "うて", "うた")),
    )

    /** The table for [word] of class [vclass], or null when the class has none. */
    fun table(word: String, vclass: String?): List<Conjugated>? {
        if (word.isEmpty() || vclass == null) return null
        GODAN[vclass]?.let { (end, g) ->
            if (word.last() != end) return null
            val stem = word.dropLast(1)
            // ある's negative is ない, not あらない
            val nai = if (vclass == "v5r-i") stem.dropLast(1) + "ない" else stem + g.a + "ない"
            val nakatta = nai.dropLast(1) + "かった"
            return verb(
                word, polite = stem + g.i, nai = nai, nakatta = nakatta,
                te = stem + g.te, ta = stem + g.ta,
                potential = stem + g.e + "る", passive = stem + g.a + "れる",
                causative = stem + g.a + "せる", volitional = stem + g.o + "う",
                conditional = stem + g.e + "ば", imperative = stem + g.e,
            )
        }
        return when (vclass) {
            "v1" -> {
                if (word.last() != 'る') return null
                val s = word.dropLast(1)
                verb(word, polite = s, nai = s + "ない", nakatta = s + "なかった", te = s + "て", ta = s + "た",
                    potential = s + "られる", passive = s + "られる", causative = s + "させる",
                    volitional = s + "よう", conditional = s + "れば", imperative = s + "ろ")
            }
            "vk" -> {
                // 来る: the kanji stays 来; in kana the vowel changes (こない, きます, くれば)
                val kanji = word.endsWith("来る")
                if (!kanji && !word.endsWith("くる")) return null
                val base = word.dropLast(2)
                fun k(kana: String, rest: String) = base + (if (kanji) "来" else kana) + rest
                verb(word, polite = k("き", ""), nai = k("こ", "ない"), nakatta = k("こ", "なかった"),
                    te = k("き", "て"), ta = k("き", "た"), potential = k("こ", "られる"),
                    passive = k("こ", "られる"), causative = k("こ", "させる"), volitional = k("こ", "よう"),
                    conditional = k("く", "れば"), imperative = k("こ", "い"))
            }
            "vs-i", "vs" -> {
                // する itself and verbs ending in する; suru nouns (勉強) take する after them
                val base = when {
                    word.endsWith("する") -> word.dropLast(2)
                    word.endsWith("為る") -> word.dropLast(2)
                    vclass == "vs" -> word
                    else -> return null
                }
                verb(base + "する", polite = base + "し", nai = base + "しない", nakatta = base + "しなかった",
                    te = base + "して", ta = base + "した", potential = base + "できる", passive = base + "される",
                    causative = base + "させる", volitional = base + "しよう", conditional = base + "すれば",
                    imperative = base + "しろ")
            }
            "adj-i", "adj-ix" -> {
                if (word.last() != 'い') return null
                // いい conjugates as よい: よくない, よかった
                val s = if (vclass == "adj-ix") word.dropLast(2) + (if (word.endsWith("良い")) "良" else "よ") else word.dropLast(1)
                listOf(
                    Conjugated("Dictionary", word),
                    Conjugated("Polite", "${word}です"),
                    Conjugated("Negative", s + "くない"),
                    Conjugated("Past", s + "かった"),
                    Conjugated("Negative past", s + "くなかった"),
                    Conjugated("Te-form", s + "くて"),
                    Conjugated("Adverb", s + "く"),
                    Conjugated("Conditional", s + "ければ"),
                )
            }
            "adj-na" -> listOf(
                Conjugated("Plain", "${word}だ"),
                Conjugated("Polite", "${word}です"),
                Conjugated("Negative", "${word}じゃない"),
                Conjugated("Past", "${word}だった"),
                Conjugated("Negative past", "${word}じゃなかった"),
                Conjugated("Te-form", "${word}で"),
                Conjugated("Adverb", "${word}に"),
                Conjugated("Before a noun", "${word}な"),
            )
            else -> null
        }
    }

    private fun verb(
        word: String, polite: String, nai: String, nakatta: String, te: String, ta: String,
        potential: String, passive: String, causative: String, volitional: String,
        conditional: String, imperative: String,
    ) = listOf(
        Conjugated("Dictionary", word),
        Conjugated("Polite", polite + "ます"),
        Conjugated("Negative", nai),
        Conjugated("Polite negative", polite + "ません"),
        Conjugated("Past", ta),
        Conjugated("Polite past", polite + "ました"),
        Conjugated("Negative past", nakatta),
        Conjugated("Te-form", te),
        Conjugated("Want to", polite + "たい"),
        Conjugated("Potential", potential),
        Conjugated("Passive", passive),
        Conjugated("Causative", causative),
        Conjugated("Volitional", volitional),
        Conjugated("Conditional", conditional),
        Conjugated("Imperative", imperative),
    )
}

/**
 * Korean conjugation tables for the word card, built from the key forms the National Institute
 * of Korean Language's learner dictionary gives for each verb and adjective (the -아/어 form, the
 * -(으)니 form and, for verbs, the -는 form: 굽다 -> 구워, 구우니, 굽는). Those carry every
 * irregularity (ㅂ, ㄷ, ㅅ, ㅎ, 르, ㄹ), so the rest follows from them by rule.
 */
object KoreanConjugation {

    /**
     * The table for [lemma] (ending in 다) from its [forms] (written forms, in the learner
     * dictionary's order), or null when they don't give what is needed. [verb]: false for
     * adjectives (no -는, wanting or asking).
     */
    fun table(lemma: String, forms: List<String>, verb: Boolean): List<Conjugated>? {
        if (!lemma.endsWith("다") || lemma.length < 2) return null
        val stem = lemma.dropLast(1)
        // the -(으)니 form gives the 으 stem; the -는 form the present; the other the 아/어 stem
        val uni = forms.firstOrNull { it.endsWith("니") } ?: return null
        val neun = forms.firstOrNull { it.endsWith("는") && it != uni }
        var a = forms.firstOrNull { it != uni && it != neun } ?: return null
        // 하다 verbs may give the written 하여: spoken 해
        if (a.endsWith("하여")) a = a.dropLast(2) + "해"
        val eu = uni.dropLast(1)
        val formal = when (final(stem.last())) {
            0 -> addFinal(stem, 17) + "니다"
            8 -> addFinal(stem.dropLast(1) + withoutFinal(stem.last()), 17) + "니다"
            else -> stem + "습니다"
        }
        val rows = mutableListOf(
            Conjugated("Dictionary", lemma),
            Conjugated("Polite", a + "요"),
            Conjugated("Formal", formal),
            Conjugated("Past", addFinal(a, 20) + "어요"),
            Conjugated("Future", addFinal(eu, 8) + " 거예요"),
            Conjugated("Negative", stem + "지 않아요"),
            Conjugated("And (-고)", stem + "고"),
            Conjugated("So (-아/어서)", a + "서"),
            Conjugated("If (-(으)면)", eu + "면"),
        )
        if (verb) {
            rows += Conjugated("Want to", stem + "고 싶어요")
            rows += Conjugated("Please (-(으)세요)", eu + "세요")
            neun?.let { rows += Conjugated("Before a noun", it) }
        } else {
            rows += Conjugated("Before a noun", addFinal(eu, 4))
        }
        return rows
    }

    private const val BASE = 0xAC00

    private fun isSyllable(c: Char) = c.code in BASE..0xD7A3

    /** The final consonant index of a syllable (0: none; 4 ㄴ, 8 ㄹ, 17 ㅂ, 20 ㅆ). */
    private fun final(c: Char): Int = if (isSyllable(c)) (c.code - BASE) % 28 else -1

    private fun withoutFinal(c: Char): Char = if (isSyllable(c)) (c.code - final(c)).toChar() else c

    /** [s] with final consonant [index] added to its last syllable (when it has none). */
    private fun addFinal(s: String, index: Int): String {
        val c = s.lastOrNull() ?: return s
        if (!isSyllable(c) || final(c) != 0) return s
        return s.dropLast(1) + (c.code + index).toChar()
    }
}

/**
 * Japanese pitch accent for the word card: the reading with its high morae marked by a line
 * above them and a ꜜ where the pitch drops, from the accent number (the mora after which the
 * pitch falls; 0: it never falls within the word).
 */
object JapanesePitch {

    private const val SMALL = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮ"

    /** [reading] split into morae (きょう -> きょ, う; small kana join the one before). */
    fun morae(reading: String): List<String> {
        val out = mutableListOf<String>()
        for (ch in reading) {
            if (ch in SMALL && out.isNotEmpty()) out[out.size - 1] = out.last() + ch else out += ch.toString()
        }
        return out
    }

    /**
     * The accent numbers in a Kanjium accent ("0", "0,2", "(副)0,(名)3"), first first; empty
     * when there is none.
     */
    fun numbers(accent: String): List<Int> =
        accent.split(',').mapNotNull { it.replace(Regex("\\(.*?\\)"), "").trim().toIntOrNull() }.distinct()

    /** [reading] with accent [n] marked: high morae overlined, ꜜ after the fall. */
    fun marked(reading: String, n: Int): String {
        val m = morae(reading)
        if (m.isEmpty()) return reading
        val sb = StringBuilder()
        m.forEachIndexed { i, mora ->
            val pos = i + 1
            val high = when (n) {
                0 -> pos > 1
                1 -> pos == 1
                else -> pos in 2..n
            } || (m.size == 1 && n == 0)
            if (high) mora.forEach { c -> sb.append(c).append('̅') } else sb.append(mora)
            if (pos == n) sb.append('ꜜ')
        }
        return sb.toString()
    }

    /** The accent type's name: Heiban (flat), Atamadaka, Nakadaka or Odaka. */
    fun typeName(reading: String, n: Int): String {
        val count = morae(reading).size
        return when {
            n == 0 -> "Heiban (flat)"
            n == 1 -> "Atamadaka (high first)"
            n >= count -> "Odaka (falls after the word)"
            else -> "Nakadaka (falls in the middle)"
        }
    }
}
