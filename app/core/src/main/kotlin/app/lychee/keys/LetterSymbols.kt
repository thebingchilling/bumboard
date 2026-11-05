package app.lychee.keys

/**
 * The symbols on the letter keys, copied from Gboard: each QWERTY letter shows a symbol in its
 * corner (q %, a @, z * ...) and its long-press popup starts with that symbol. On the English
 * keyboards the letter's accents follow (from the locale); on the Chinese keyboards the popup is
 * the symbol, the letter and its capital, and on 拼音 the vowels' tone marks too. Without the
 * number row the top row shows its digit instead, with the symbol next in the popup.
 */
object LetterSymbols {
    enum class Style { LATIN, CHINESE, PINYIN }

    private val HINTS = mapOf(
        'q' to "%", 'w' to "\\", 'e' to "|", 'r' to "=", 't' to "[",
        'y' to "]", 'u' to "<", 'i' to ">", 'o' to "{", 'p' to "}",
        'a' to "@", 's' to "#", 'd' to CURRENCY, 'f' to "_", 'g' to "&",
        'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
        'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
        'n' to "!", 'm' to "?",
    )

    /** Gboard's Chinese keyboards differ on three keys. */
    private val CHINESE_HINTS = mapOf('d' to "$", 'z' to "、", 'c' to ".")

    private val PINYIN_TONES = mapOf(
        'a' to listOf("ā", "á", "ǎ", "à"),
        'e' to listOf("ê", "ē", "é", "ě", "è"),
        'i' to listOf("ī", "í", "ǐ", "ì"),
        'o' to listOf("ō", "ó", "ǒ", "ò"),
        'u' to listOf("ū", "ú", "ǔ", "ù"),
        'v' to listOf("ü", "ǖ", "ǘ", "ǚ", "ǜ"),
    )

    private val DIGITS = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
    )

    /** Stands for the keyboard's own currency sign. */
    private const val CURRENCY = "\u0000currency"

    private fun symbol(c: Char, style: Style, currency: String): String? {
        val s = (if (style != Style.LATIN) CHINESE_HINTS[c] else null) ?: HINTS[c] ?: return null
        return if (s == CURRENCY) currency else s
    }

    /** The corner hint for [letter], or null when it has none. */
    @JvmStatic
    fun hint(letter: Char, numberRow: Boolean, currency: String, style: Style = Style.LATIN): String? {
        val c = letter.lowercaseChar()
        if (!numberRow) DIGITS[c]?.let { return it }
        return symbol(c, style, currency)
    }

    /**
     * The start of [letter]'s popup: the digit (without the number row) and the symbol; on the
     * Chinese keyboards also the letter, its capital and (拼音) the tone marks. Null for a
     * letter with no symbol. On the English keyboards the locale's accents go after these.
     */
    @JvmStatic
    fun moreKeys(letter: Char, numberRow: Boolean, currency: String, style: Style = Style.LATIN): Array<String>? {
        val c = letter.lowercaseChar()
        val s = symbol(c, style, currency) ?: return null
        val out = ArrayList<String>()
        if (!numberRow) DIGITS[c]?.let { out += it }
        out += s
        if (style != Style.LATIN) {
            out += c.toString()
            out += c.uppercaseChar().toString()
            if (style == Style.PINYIN) PINYIN_TONES[c]?.let { out += it }
        }
        return out.distinct().toTypedArray()
    }
}
