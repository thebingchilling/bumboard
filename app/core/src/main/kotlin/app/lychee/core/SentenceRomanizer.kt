package app.lychee.core

/**
 * Example sentences in romanisation, under the sentence on the word cards: Japanese in Hepburn
 * (the kana as read; kanji by their reading in the dictionary), Korean in Revised Romanization,
 * Chinese by the dictionary's reading of each word ([Annotator.romanise]).
 */
object SentenceRomanizer {

    // ------------------------------------------------------------------ Japanese

    private val KANA = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "ゐ" to "i", "ゑ" to "e", "を" to "o", "ん" to "n", "ゔ" to "vu",
        "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o", "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo", "ゎ" to "wa",
        // two kana read as one syllable
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo", "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho", "しぇ" to "she", "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo", "じぇ" to "je",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho", "ちぇ" to "che", "ぢゃ" to "ja", "ぢゅ" to "ju", "ぢょ" to "jo",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo", "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo", "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo", "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "てぃ" to "ti", "でぃ" to "di",
        "とぅ" to "tu", "どぅ" to "du", "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo", "つぁ" to "tsa",
        "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
    )

    private val PUNCT = mapOf(
        '。' to ".", '、' to ",", '，' to ",", '！' to "!", '？' to "?", '：' to ":", '；' to ";",
        '「' to "\"", '」' to "\"", '『' to "\"", '』' to "\"", '（' to "(", '）' to ")", '・' to " ",
        '〜' to "~", '…' to "…", '　' to " ", '“' to "\"", '”' to "\"",
    )

    private fun hiragana(text: String): String = buildString {
        for (ch in text) append(if (ch in 'ァ'..'ヶ') ch - 0x60 else ch)
    }

    /** Kana (hiragana or katakana) in Hepburn: きょうと → kyouto, がっこう → gakkou. */
    fun kana(text: String): String {
        val s = hiragana(text)
        val out = StringBuilder()
        var double = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == 'っ' -> { double = true; i++; continue }
                ch == 'ー' -> {
                    // a long vowel: the last vowel again
                    out.lastOrNull { it in "aeiou" }?.let { out.append(it) }
                    i++; continue
                }
            }
            val two = if (i + 1 < s.length) KANA[s.substring(i, i + 2)] else null
            val roman = two ?: KANA[s.substring(i, i + 1)]
            if (roman == null) {
                out.append(PUNCT[ch] ?: ch.toString())
                double = false
                i++
                continue
            }
            if (double) {
                // a small tsu doubles the consonant after it (ch is written tch)
                out.append(if (roman.startsWith("ch")) "t" else roman[0].toString())
                double = false
            }
            out.append(roman)
            i += if (two != null) 2 else 1
        }
        return out.toString()
    }

    /** Romaji to kana: Hepburn and the usual typed spellings (si, tu, nn ...). */
    private val ROMAJI: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        // Hepburn from the table above; the first kana for a spelling wins (ji: じ, not ぢ)
        for ((kana, roman) in KANA) if (kana.length == 1 && kana[0] in "ぁぃぅぇぉゃゅょゎ") continue else m.putIfAbsent(roman, kana)
        m.putAll(mapOf(
            "si" to "し", "ti" to "ち", "tu" to "つ", "hu" to "ふ", "zi" to "じ", "di" to "ぢ", "du" to "づ",
            "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ", "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
            "zya" to "じゃ", "zyu" to "じゅ", "zyo" to "じょ", "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
            "nn" to "ん", "n'" to "ん", "wo" to "を", "xtu" to "っ", "ltu" to "っ",
            "-" to "ー",
        ))
        m
    }

    /** Romaji (as typed or in Hepburn) in hiragana: kyouto → きょうと, gakkou → がっこう. */
    fun hiraganaOf(romaji: String): String {
        val s = romaji.lowercase().filter { !it.isWhitespace() }
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            // a doubled consonant (and tch): a small tsu
            if (i + 1 < s.length && ch !in "aeioun" && ch.isLetter() && (s[i + 1] == ch || (ch == 't' && s[i + 1] == 'c'))) {
                out.append('っ')
                i++
                continue
            }
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len > s.length) continue
                val part = s.substring(i, i + len)
                // n before a consonant (or at the end) is ん; before a vowel or y it starts a syllable
                if (part == "n" && i + 1 < s.length && s[i + 1] in "aeiouy") continue
                // nn before a vowel is ん then a syllable of n (konnichiha: こんにちは)
                if (part == "nn" && i + 2 < s.length && s[i + 2] in "aeiouy") continue
                val kana = ROMAJI[part] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (!matched) {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private fun isKana(ch: Char) = ch in 'ぁ'..'ゖ' || ch in 'ァ'..'ヺ' || ch == 'ー' || ch == 'ゝ'

    /**
     * A Japanese sentence in Hepburn, a word at a time: the longest run the dictionary knows
     * ([reading] gives a written form's reading in kana, null when it has none) read as that
     * word, kana as they are; words separated by spaces.
     */
    fun japanese(sentence: String, reading: (String) -> String?): String {
        val words = ArrayList<String>()
        var i = 0
        val n = sentence.length
        while (i < n) {
            val ch = sentence[i]
            if (ch.isWhitespace()) { i++; continue }
            if (PUNCT.containsKey(ch) || !(isKana(ch) || Annotator.isHan(ch.code))) {
                val p = PUNCT[ch] ?: ch.toString()
                if (p.isNotBlank()) {
                    // punctuation joins the word before it
                    if (words.isNotEmpty() && p in listOf(".", ",", "!", "?", ":", ";", "…", ")")) words[words.size - 1] += p
                    else words += p
                }
                i++
                continue
            }
            var matched = false
            for (len in minOf(MAX_WORD, n - i) downTo 1) {
                val form = sentence.substring(i, i + len)
                if (form.any { !(isKana(it) || Annotator.isHan(it.code)) }) continue
                val r = reading(form) ?: continue
                if (r.isBlank()) continue
                words += kana(r)
                i += len
                matched = true
                break
            }
            if (!matched) {
                // no word for it: a kana as it reads (the particles は and へ as wa and e), a
                // kanji left as it is
                words += when {
                    ch == 'は' && words.isNotEmpty() -> "wa"
                    ch == 'へ' && words.isNotEmpty() -> "e"
                    isKana(ch) -> kana(ch.toString())
                    else -> ch.toString()
                }
                i++
            }
        }
        return words.joinToString(" ").replace(" ,", ",")
    }

    private const val MAX_WORD = 8

    // ------------------------------------------------------------------ Korean

    private val INITIAL = arrayOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
    private val MEDIAL = arrayOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
    /** each final as it sounds at the end of a syllable */
    private val FINAL = arrayOf("", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t")
    /**
     * a final carried over to a next syllable that starts with a vowel: what stays, and the
     * initial (index into [INITIAL]) it becomes
     */
    private val CARRY = mapOf(
        1 to (0 to 0), 2 to (0 to 1), 3 to (1 to 9), 4 to (0 to 2), 5 to (4 to 12), 6 to (4 to 18),
        7 to (0 to 3), 8 to (0 to 5), 9 to (8 to 0), 10 to (8 to 6), 11 to (8 to 7), 12 to (8 to 9),
        13 to (8 to 16), 14 to (8 to 17), 15 to (8 to 18), 16 to (0 to 6), 17 to (0 to 7), 18 to (17 to 9),
        19 to (0 to 9), 20 to (0 to 10), 22 to (0 to 12), 23 to (0 to 14), 24 to (0 to 15), 25 to (0 to 16),
        26 to (0 to 17), 27 to (0 to 18),
    )

    /** Korean in Revised Romanization: 한국어 → hangugeo, 감사합니다 → gamsahamnida. */
    fun korean(text: String): String {
        data class Syl(var initial: Int, val medial: Int, var final: Int)
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch !in '가'..'힣') {
                out.append(PUNCT[ch] ?: ch.toString())
                i++
                continue
            }
            // a run of syllables (a word)
            val run = ArrayList<Syl>()
            while (i < text.length && text[i] in '가'..'힣') {
                val v = text[i] - '가'
                run += Syl(v / 588, (v % 588) / 28, v % 28)
                i++
            }
            for (k in run.indices) {
                val s = run[k]
                val next = run.getOrNull(k + 1)
                // a final before a vowel is said at the start of the next syllable
                if (next != null && next.initial == 11 && s.final != 0 && s.final != 21) {
                    CARRY[s.final]?.let { (stay, initial) ->
                        // ㅎ before a vowel is silent
                        next.initial = if (initial == 18) 11 else initial
                        s.final = stay
                    }
                }
                val initial = INITIAL[s.initial].let { if (it == "r" && k == 0) "r" else it }
                out.append(initial).append(MEDIAL[s.medial])
                var fin = FINAL[s.final]
                if (next != null) {
                    val ni = next.initial
                    when {
                        // ㄹ then ㄹ, and ㄴ then ㄹ: ll
                        fin == "l" && ni == 5 -> next.initial = -1
                        fin == "n" && ni == 5 -> { fin = "l"; next.initial = -1 }
                        // a stop before a nasal turns nasal: 합니다 → hamnida, 국물 → gungmul
                        ni == 2 || ni == 6 -> fin = when (fin) { "k" -> "ng"; "t" -> "n"; "p" -> "m"; else -> fin }
                    }
                }
                out.append(fin)
                if (next != null && next.initial == -1) {
                    out.append('l')
                    next.initial = 11
                }
            }
        }
        return out.toString()
    }
}
