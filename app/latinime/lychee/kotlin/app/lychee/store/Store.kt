package app.lychee.store

import android.content.Context

/**
 * Lychee's paid add-ons as PAID.md describes them: every individual add-on (covering every
 * language, except the full dictionary cards, which are per language), the three language packs
 * (one price for every language) and Full. Prices are in USD, one-time purchases.
 *
 * Only the store's UI exists so far: nothing is sold and nothing is locked ([FREE_FOR_NOW]), as
 * there is no billing yet. When billing comes, [owns] is where purchases are checked.
 */
object Store {

    /** No billing yet: everything is free while Lychee is in testing. */
    const val FREE_FOR_NOW = true

    enum class Kind { ADDON, PACK, FULL }

    /** Where an individual add-on is listed in the store. */
    enum class Category { FEATURES, DICTIONARIES, FONTS }

    /** One line of what a product gives; [coming] marks what isn't built yet. */
    data class Line(val text: String, val coming: Boolean = false)

    data class Product(
        val id: String,
        val kind: Kind,
        val name: String,
        val tagline: String,
        /** The price in US cents. */
        val cents: Int,
        val category: Category? = null,
        /** What it gives. */
        val lines: List<Line> = emptyList(),
        /** The individual add-ons a pack or Full includes (their prices make its value). */
        val includes: List<String> = emptyList(),
        /** A pack's bonus, not sold on its own (its language's fonts). */
        val bonus: String? = null,
    )

    // ------------------------------------------------------------------ individual add-ons

    val CARD_ZH = Product(
        "card_zh", Kind.ADDON, "Chinese Dictionary Pro",
        "The full Chinese card: Cantonese and Mandarin side by side", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with Cantonese and Mandarin readings side by side"),
            Line("Taiwan readings and colloquial Cantonese labels"),
            Line("HSK levels and Cantonese frequency bands"),
            Line("Measure words"),
            Line("Character breakdown and English-to-Chinese lookup"),
        ),
    )
    val CARD_JA = Product(
        "card_ja", Kind.ADDON, "Japanese Dictionary Pro",
        "The full Japanese card: every meaning, kanji in depth", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with old kanji forms and Japan-made kanji noted"),
            Line("JLPT levels", coming = true),
            Line("Pitch accent", coming = true),
            Line("Verb and adjective conjugation tables", coming = true),
            Line("Kanji details: parts, grade, frequency, readings", coming = true),
        ),
    )
    val CARD_KO = Product(
        "card_ko", Kind.ADDON, "Korean Dictionary Pro",
        "The full Korean card: every meaning, Hanja in depth", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with each word's Hanja"),
            Line("TOPIK levels", coming = true),
            Line("Sound changes shown, like 학교 said [학꾜]", coming = true),
            Line("Verb and adjective conjugation tables", coming = true),
            Line("Each Hanja's meaning and sound, linked to Chinese and Japanese", coming = true),
        ),
    )
    val HANDWRITING = Product(
        "handwriting", Kind.ADDON, "Google Handwriting",
        "Google's recognition for messy, fast and joined-up writing", 599, Category.FEATURES,
        listOf(Line("Chinese, Japanese and Korean, read on your phone")),
    )
    val STROKES = Product(
        "strokes", Kind.ADDON, "Stroke Order",
        "Animated stroke order on the word card", 399, Category.FEATURES,
        listOf(Line("Chinese, Japanese and Korean characters")),
    )
    val EXAMPLES = Product(
        "examples", Kind.ADDON, "Example Sentences",
        "Real sentences with English for every word", 399, Category.FEATURES,
        listOf(Line("Cantonese, Mandarin, Japanese and Korean")),
    )
    val FONTS = Product(
        "fonts", Kind.ADDON, "Font Pack",
        "Every font and emoji style, on your keys, candidates and cards", 499, Category.FONTS,
        listOf(
            Line("Chinese: Kaiti, Song and brush styles, handwritten and rounded"),
            Line("Japanese: textbook, Mincho, rounded and brush styles"),
            Line("Korean: Myeongjo, Dodum, pen and brush styles"),
            Line("Emoji: Noto 3D, Fluent, Twemoji, OpenMoji, Blobmoji, Noto Emoji"),
        ),
    )

    val addons = listOf(CARD_ZH, CARD_JA, CARD_KO, HANDWRITING, STROKES, EXAMPLES, FONTS)

    // ------------------------------------------------------------------ packs and Full

    private val shared = listOf(HANDWRITING.id, STROKES.id, EXAMPLES.id)

    val PACK_ZH = Product(
        "pack_zh", Kind.PACK, "Chinese", "Cantonese and Mandarin, in full", 1499,
        lines = CARD_ZH.lines + listOf(Line("Google Handwriting, Stroke Order and Example Sentences")),
        includes = listOf(CARD_ZH.id) + shared, bonus = "Chinese fonts",
    )
    val PACK_JA = Product(
        "pack_ja", Kind.PACK, "Japanese", "Japanese, in full", 1499,
        lines = CARD_JA.lines + listOf(Line("Google Handwriting, Stroke Order and Example Sentences")),
        includes = listOf(CARD_JA.id) + shared, bonus = "Japanese fonts",
    )
    val PACK_KO = Product(
        "pack_ko", Kind.PACK, "Korean", "Korean, in full", 1499,
        lines = CARD_KO.lines + listOf(Line("Google Handwriting, Stroke Order and Example Sentences")),
        includes = listOf(CARD_KO.id) + shared, bonus = "Korean fonts",
    )
    val FULL = Product(
        "full", Kind.FULL, "Lychee Full", "Everything Lychee makes, now and in the future", 2999,
        lines = listOf(
            Line("All three packs: Chinese, Japanese and Korean in full"),
            Line("Google Handwriting, Stroke Order and Example Sentences"),
            Line("The whole Font Pack, with every emoji style"),
            Line("Every future Lychee add-on, at no extra cost"),
        ),
        includes = addons.map { it.id },
    )

    val packs = listOf(PACK_ZH, PACK_JA, PACK_KO)

    /** Everything sold, Full first. */
    val all = listOf(FULL) + packs + addons

    fun byId(id: String): Product? = all.firstOrNull { it.id == id }

    /** "$14.99". */
    fun price(cents: Int): String = "$%d.%02d".format(cents / 100, cents % 100)

    /** What a pack's or Full's add-ons cost one by one (its bonus not counted). */
    fun value(p: Product): Int = p.includes.sumOf { byId(it)?.cents ?: 0 }

    /** The packs (and Full) that include the add-on [id]. */
    fun includedIn(id: String): List<Product> = (packs + FULL).filter { id in it.includes }

    /** The add-on a downloadable pack belongs to, for its row in Add-ons. */
    fun forPack(packId: String): Product? = when (packId) {
        "strokes_zh" -> STROKES
        "examples" -> EXAMPLES
        "emoji_noto3d" -> FONTS
        else -> null
    }

    /** Whether [id] is unlocked: everything, while there is no billing. */
    @Suppress("UNUSED_PARAMETER")
    fun owns(context: Context, id: String): Boolean = FREE_FOR_NOW

    /**
     * Why paid add-ons can't be offered on this phone, said plainly, or null when they can. Only
     * Google Play matters: nothing about the phone itself (root, bootloader, Android version,
     * emulator) is ever checked (PAID.md).
     */
    fun unavailableReason(context: Context): String? =
        if (hasGooglePlay(context)) null
        else "Paid add-ons will be bought through Google Play, which isn't on this phone. Lychee's " +
            "own way to buy, for phones without Google Play, comes later. Everything free, and " +
            "every demo, keeps working."

    private fun hasGooglePlay(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.android.vending", 0); true }.getOrDefault(false)
}
