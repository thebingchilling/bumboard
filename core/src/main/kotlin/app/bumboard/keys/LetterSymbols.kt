package app.bumboard.keys

/**
 * The symbols on the letter keys, like Gboard's "long-press for symbols": each QWERTY letter
 * shows a symbol in its corner (q %, a @, z * ...) and its long-press popup offers that symbol
 * first, then related ones, then the letter's accents. Without the number row the top row
 * shows its digit instead, with the symbol next in the popup.
 */
object LetterSymbols {
    /** The corner symbol of each letter, in QWERTY order. */
    private val HINTS = mapOf(
        'q' to "%", 'w' to "\\", 'e' to "|", 'r' to "=", 't' to "[",
        'y' to "]", 'u' to "<", 'i' to ">", 'o' to "{", 'p' to "}",
        'a' to "@", 's' to "#", 'd' to CURRENCY, 'f' to "_", 'g' to "&",
        'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
        'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
        'n' to "!", 'm' to "?",
    )

    /** More symbols after the corner one in each popup. */
    private val EXTRAS = mapOf(
        'q' to listOf("‰"),
        'e' to listOf("¦"),
        'r' to listOf("≠", "≈"),
        'u' to listOf("≤", "‹", "«"),
        'i' to listOf("≥", "›", "»"),
        's' to listOf("№"),
        'g' to listOf("§", "¶"),
        'h' to listOf("–", "—", "·"),
        'j' to listOf("±"),
        'k' to listOf("[", "{"),
        'l' to listOf("]", "}"),
        'z' to listOf("†", "‡", "★"),
        'x' to listOf("“", "”", "„"),
        'c' to listOf("‘", "’", "‚"),
        'v' to listOf("…"),
        'n' to listOf("¡", "‼"),
        'm' to listOf("¿", "‽"),
    )

    private val DIGITS = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
    )

    /** Stands for the keyboard's own currency sign ([hint] and [moreKeys] take it). */
    private const val CURRENCY = "\u0000currency"
    private val CURRENCIES = listOf("$", "€", "£", "¥", "¢", "₹", "₩", "₱")

    private fun resolve(symbol: String, currency: String) = if (symbol == CURRENCY) currency else symbol

    /** The corner hint for [letter], or null when it has none. */
    @JvmStatic
    fun hint(letter: Char, numberRow: Boolean, currency: String): String? {
        val c = letter.lowercaseChar()
        if (!numberRow) DIGITS[c]?.let { return it }
        return HINTS[c]?.let { resolve(it, currency) }
    }

    /**
     * The symbols to put at the start of [letter]'s popup: its hint first (the digit, then the
     * symbol, without the number row), then the related symbols. Null for a letter with none.
     */
    @JvmStatic
    fun moreKeys(letter: Char, numberRow: Boolean, currency: String): Array<String>? {
        val c = letter.lowercaseChar()
        val symbol = HINTS[c] ?: return null
        val out = ArrayList<String>()
        if (!numberRow) DIGITS[c]?.let { out += it }
        out += resolve(symbol, currency)
        if (symbol == CURRENCY) out += CURRENCIES.filter { it != currency }
        EXTRAS[c]?.let { out += it }
        return out.distinct().toTypedArray()
    }
}
