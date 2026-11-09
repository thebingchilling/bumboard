package app.lychee.core

/**
 * Pinyin (numbered syllables: zhong1 guo2, lv4 or lu:4) in Zhuyin (bopomofo), as taught in
 * Taiwan: ㄓㄨㄥ ㄍㄨㄛˊ. Tone 1 has no mark; the neutral tone's dot comes first (˙ㄇㄚ).
 */
object Zhuyin {
    private val INITIALS = listOf(
        "zh" to "ㄓ", "ch" to "ㄔ", "sh" to "ㄕ",
        "b" to "ㄅ", "p" to "ㄆ", "m" to "ㄇ", "f" to "ㄈ", "d" to "ㄉ", "t" to "ㄊ", "n" to "ㄋ", "l" to "ㄌ",
        "g" to "ㄍ", "k" to "ㄎ", "h" to "ㄏ", "j" to "ㄐ", "q" to "ㄑ", "x" to "ㄒ", "r" to "ㄖ",
        "z" to "ㄗ", "c" to "ㄘ", "s" to "ㄙ",
    )

    private val FINALS = mapOf(
        "a" to "ㄚ", "o" to "ㄛ", "e" to "ㄜ", "ê" to "ㄝ", "ai" to "ㄞ", "ei" to "ㄟ", "ao" to "ㄠ", "ou" to "ㄡ",
        "an" to "ㄢ", "en" to "ㄣ", "ang" to "ㄤ", "eng" to "ㄥ", "er" to "ㄦ", "ong" to "ㄨㄥ",
        "i" to "ㄧ", "ia" to "ㄧㄚ", "ie" to "ㄧㄝ", "iao" to "ㄧㄠ", "iu" to "ㄧㄡ", "iou" to "ㄧㄡ", "ian" to "ㄧㄢ",
        "in" to "ㄧㄣ", "iang" to "ㄧㄤ", "ing" to "ㄧㄥ", "iong" to "ㄩㄥ", "io" to "ㄧㄛ",
        "u" to "ㄨ", "ua" to "ㄨㄚ", "uo" to "ㄨㄛ", "uai" to "ㄨㄞ", "ui" to "ㄨㄟ", "uei" to "ㄨㄟ", "uan" to "ㄨㄢ",
        "un" to "ㄨㄣ", "uen" to "ㄨㄣ", "uang" to "ㄨㄤ", "ueng" to "ㄨㄥ",
        "ü" to "ㄩ", "üe" to "ㄩㄝ", "üan" to "ㄩㄢ", "ün" to "ㄩㄣ",
    )

    /** y- and w- spellings, which stand for the medials i, u and ü. */
    private val WHOLE = mapOf(
        "yi" to "ㄧ", "ya" to "ㄧㄚ", "yo" to "ㄧㄛ", "ye" to "ㄧㄝ", "yai" to "ㄧㄞ", "yao" to "ㄧㄠ", "you" to "ㄧㄡ",
        "yan" to "ㄧㄢ", "yin" to "ㄧㄣ", "yang" to "ㄧㄤ", "ying" to "ㄧㄥ", "yong" to "ㄩㄥ",
        "yu" to "ㄩ", "yue" to "ㄩㄝ", "yuan" to "ㄩㄢ", "yun" to "ㄩㄣ",
        "wu" to "ㄨ", "wa" to "ㄨㄚ", "wo" to "ㄨㄛ", "wai" to "ㄨㄞ", "wei" to "ㄨㄟ", "wan" to "ㄨㄢ",
        "wen" to "ㄨㄣ", "wang" to "ㄨㄤ", "weng" to "ㄨㄥ",
        "er" to "ㄦ", "r" to "ㄦ",
    )

    private val TONES = arrayOf("", "", "ˊ", "ˇ", "ˋ")

    /** One numbered pinyin syllable in Zhuyin; null when it isn't one. */
    fun syllable(pinyin: String): String? {
        var s = pinyin.trim().lowercase().replace("u:", "ü").replace("v", "ü")
        var tone = 5
        if (s.isNotEmpty() && s.last().isDigit()) {
            tone = s.last() - '0'
            s = s.dropLast(1)
        }
        if (s.isEmpty()) return null
        val body = WHOLE[s] ?: run {
            val (initial, symbol) = INITIALS.firstOrNull { s.startsWith(it.first) } ?: ("" to "")
            var rest = s.substring(initial.length)
            // zhi chi shi ri zi ci si: the initial alone
            if (rest == "i" && initial in setOf("zh", "ch", "sh", "r", "z", "c", "s")) return@run symbol
            // u after j, q, x is ü
            if (initial in setOf("j", "q", "x") && rest.startsWith("u")) rest = "ü" + rest.drop(1)
            val fin = if (rest.isEmpty()) "" else FINALS[rest] ?: return null
            symbol + fin
        }
        return when (tone) {
            in 1..4 -> body + TONES[tone]
            else -> "˙$body"
        }
    }

    /** Numbered pinyin syllables (space-separated) in Zhuyin; unknown syllables kept as they are. */
    fun format(reading: String): String =
        reading.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { syllable(it) ?: it }
}
