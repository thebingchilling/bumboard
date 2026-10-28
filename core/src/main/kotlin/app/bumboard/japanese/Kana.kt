package app.bumboard.japanese

/**
 * Kana for the 12-key flick keyboard: its keys, the ゛゜小 key's changes, and the romaji
 * the kana are fed to Rime as (Hepburn, っ doubling the next consonant, as in BumBoard's
 * Japanese dictionary).
 */
object Kana {
    /** Each flick key: tap, then left, up, right, down (null: nothing that way). */
    val KEYS: List<List<String?>> = listOf(
        listOf("あ", "い", "う", "え", "お"),
        listOf("か", "き", "く", "け", "こ"),
        listOf("さ", "し", "す", "せ", "そ"),
        listOf("た", "ち", "つ", "て", "と"),
        listOf("な", "に", "ぬ", "ね", "の"),
        listOf("は", "ひ", "ふ", "へ", "ほ"),
        listOf("ま", "み", "む", "め", "も"),
        listOf("や", "「", "ゆ", "」", "よ"),
        listOf("ら", "り", "る", "れ", "ろ"),
        listOf("わ", "を", "ん", "ー", null),
        listOf("、", "。", "？", "！", "…"),
    )

    /** What ゛゜小 turns each kana into, in turn (back to the start after the last). */
    private val CYCLES = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つっづ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ",
    )
    private val NEXT: Map<Char, Char> = buildMap {
        for (cycle in CYCLES) for (i in cycle.indices) put(cycle[i], cycle[(i + 1) % cycle.length])
    }

    /** The kana ゛゜小 changes [c] into, or null when it has no other form. */
    fun modify(c: Char): Char? = NEXT[c]

    private val BASE = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n", 'ゔ' to "vu", 'ー' to "-",
        'ぁ' to "xa", 'ぃ' to "xi", 'ぅ' to "xu", 'ぇ' to "xe", 'ぉ' to "xo",
        'ゃ' to "xya", 'ゅ' to "xyu", 'ょ' to "xyo", 'ゎ' to "xwa", 'っ' to "xtsu",
    )
    private val YOON = mapOf(
        'き' to "ky", 'ぎ' to "gy", 'し' to "sh", 'じ' to "j", 'ち' to "ch", 'ぢ' to "j", 'に' to "ny",
        'ひ' to "hy", 'び' to "by", 'ぴ' to "py", 'み' to "my", 'り' to "ry",
    )
    private val SMALL_Y = mapOf('ゃ' to "a", 'ゅ' to "u", 'ょ' to "o")
    private val EXTRA = mapOf(
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "てぃ" to "ti", "でぃ" to "di",
        "とぅ" to "tu", "どぅ" to "du", "うぃ" to "wi", "うぇ" to "we", "しぇ" to "she", "じぇ" to "je",
        "ちぇ" to "che",
    )

    /**
     * The romaji keys for [kana] (hiragana; anything else is left out). っ before a consonant
     * doubles it; elsewhere it is "xtsu". An ん before a vowel or y is "n'" so it stays ん.
     */
    fun romaji(kana: CharSequence): String {
        val out = StringBuilder()
        var i = 0
        var double = false
        while (i < kana.length) {
            val pair = if (i + 1 < kana.length) kana.substring(i, i + 2) else ""
            val syl: String
            when {
                pair in EXTRA -> { syl = EXTRA.getValue(pair); i += 2 }
                pair.length == 2 && pair[0] in YOON && pair[1] in SMALL_Y -> {
                    syl = YOON.getValue(pair[0]) + SMALL_Y.getValue(pair[1]); i += 2
                }
                kana[i] == 'っ' && i + 1 < kana.length && kana[i + 1] !in "あいうえおんっーゃゅょぁぃぅぇぉ" &&
                    kana[i + 1] in BASE -> { double = true; i++; continue }
                kana[i] in BASE -> { syl = BASE.getValue(kana[i]); i++ }
                else -> { i++; continue }
            }
            var s = syl
            if (double) {
                s = if (s.startsWith("ch")) "t$s" else s[0] + s
                double = false
            }
            if (out.endsWith("n") && out.isNotEmpty() && s.first() in "aiueoy" && endsWithLoneN(out)) out.append('\'')
            out.append(s)
        }
        return out.toString()
    }

    /** Whether the romaji so far ends in ん's lone n (not the n of na, ni...). */
    private fun endsWithLoneN(out: StringBuilder): Boolean {
        // a lone n follows a vowel, another n, a dash or nothing
        val before = if (out.length >= 2) out[out.length - 2] else ' '
        return before in "aiueon-' "
    }
}
