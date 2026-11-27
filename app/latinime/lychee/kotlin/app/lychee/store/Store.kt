package app.lychee.store

import android.content.Context
import android.os.Build

/**
 * Lychee's paid add-ons as PAID.md describes them: every individual add-on (covering every
 * language, except the "In Depth" dictionary cards, which are per language), the three language
 * packs (one price for every language) and Lychee Complete. Prices are in USD, one-time purchases.
 *
 * The words are meant to sell honestly: what each add-on really does, where its data comes from
 * and under which licence, and what stays on the phone. No "Pro", no hype.
 *
 * Only the store's UI exists so far: nothing is sold and nothing is locked ([FREE_FOR_NOW]), as
 * there is no billing yet. When billing comes, [owns] is where purchases are checked.
 */
object Store {

    /** No billing yet: everything is free while Lychee is in testing. */
    const val FREE_FOR_NOW = true

    enum class Kind { ADDON, PACK, FULL }

    /** Where an individual add-on is listed in the store. */
    enum class Category { DICTIONARIES, FEATURES, FONTS }

    /** The worked example a listing's page shows. */
    enum class Sample { CHINESE, JAPANESE, KOREAN, ENGLISH, HANDWRITING, STROKES, EXAMPLES, FONTS, ALL }

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
        /** The individual add-ons a pack or Complete includes (their prices make its value). */
        val includes: List<String> = emptyList(),
        /** A pack's bonus, not sold on its own (its language's fonts). */
        val bonus: String? = null,
        /** What its tile shows. */
        val tile: String = "",
        /** A short paragraph on its page: what it is for, plainly. */
        val about: String = "",
        /** What can be used free before buying. */
        val demo: String = "",
        /** Where its data and code come from, with their licences. */
        val sources: String = "",
        /** The worked example on its page. */
        val sample: Sample = Sample.ALL,
        /** Needs another app (Google Handwriting: the Lychee handwriting app). */
        val needsApp: Boolean = false,
    )

    // ------------------------------------------------------------------ individual add-ons

    val CARD_ZH = Product(
        "card_zh", Kind.ADDON, "The Chinese Handbook",
        "Everything about a word, a tap away", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, read in Cantonese and Mandarin"),
            Line("Taiwan readings and spoken Cantonese words marked"),
            Line("Each word's HSK level and how common it is"),
            Line("The right measure word, every time"),
            Line("What each character is built from, and English-to-Chinese look-up"),
        ),
        tile = "學",
        about = "Typed 學校 and wondered why it takes 所 and not 個? Hold the word. You'll see every " +
            "meaning, how it sounds in Cantonese and in Mandarin, how common it is and which measure " +
            "word goes with it. Tap a character to see what it's made of.",
        demo = "Every HSK 1–2 word gets the full card, free, for as long as you like.",
        sources = "CC-CEDICT (CC BY-SA 4.0), CC-Canto (CC BY-SA 3.0), Unihan (Unicode licence), " +
            "HSK lists from complete-hsk-vocabulary (MIT).",
        sample = Sample.CHINESE,
    )
    val CARD_JA = Product(
        "card_ja", Kind.ADDON, "The Japanese Handbook",
        "Pitch, verb forms and kanji, on every word", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with old kanji forms and Japan-made kanji pointed out"),
            Line("The JLPT level of each word"),
            Line("Pitch accent drawn over the reading, so you say it right"),
            Line("Every form of a verb or adjective in one table"),
            Line("Each kanji's parts, readings, school grade and how common it is"),
        ),
        tile = "語",
        about = "Your textbook says 食べる means \"to eat\". Hold it here and you'll also see where " +
            "the pitch drops, how to say \"didn't eat\" politely, and what 食 is made of, right " +
            "where you typed it.",
        demo = "Every JLPT N5 word gets the full card, free, for as long as you like.",
        sources = "JMdict, KANJIDIC2 and KRADFILE (EDRDG, CC BY-SA 4.0), JLPT lists by Jonathan " +
            "Waller (CC BY), Kanjium pitch accents (CC BY-SA 4.0).",
        sample = Sample.JAPANESE,
    )
    val CARD_KO = Product(
        "card_ko", Kind.ADDON, "The Korean Handbook",
        "How it's really said, and the Hanja behind it", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with the word's Hanja"),
            Line("Beginner, intermediate or advanced, on every word"),
            Line("How it's actually said: 학교 sounds like [학꾜]"),
            Line("Every form of a verb or adjective, irregular ones included"),
            Line("What each Hanja means and how it sounds"),
        ),
        tile = "한",
        about = "Korean is rarely said the way it's spelt. Hold a word to see how it really sounds, " +
            "all its forms, and the Hanja that explain why 학교 and 교실 share a syllable. From the " +
            "National Institute of Korean Language's dictionary for learners.",
        demo = "Every beginner word gets the full card, free, for as long as you like.",
        sources = "Basic Korean Dictionary, National Institute of Korean Language (CC BY-SA 2.0 KR), " +
            "Wiktionary via kaikki.org (CC BY-SA 4.0), Unihan (Unicode licence).",
        sample = Sample.KOREAN,
    )
    val HANDWRITING = Product(
        "handwriting", Kind.ADDON, "Scribble",
        "Quick, messy writing read far better, by Google's ML Kit", 599, Category.FEATURES,
        listOf(
            Line("Chinese, Japanese, Korean and English"),
            Line("Whole words and phrases in one go, not one character at a time"),
            Line("Read on your phone: nothing you write is sent anywhere"),
        ),
        tile = "✍",
        about = "Lychee's own handwriting reader likes neat characters. Scribble uses Google's ML Kit, " +
            "which copes with rushed strokes and whole phrases at once. It runs in the free Lychee " +
            "handwriting app, because ML Kit's licence needs an app of its own; Lychee itself never " +
            "goes online.",
        demo = "Single characters are free.",
        sources = "Google ML Kit Digital Ink Recognition (Google's terms), in the free Lychee " +
            "handwriting app. Google's models are downloaded once (about 20 MB per language).",
        sample = Sample.HANDWRITING,
        needsApp = true,
    )
    val STROKES = Product(
        "strokes", Kind.ADDON, "Brushwork",
        "Watch any character write itself, in the right order", 399, Category.FEATURES,
        listOf(Line("Chinese, Japanese and Korean characters"), Line("Slow it down, step through it, play it again")),
        tile = "永",
        about = "Writing strokes in the right order is what makes characters look right, and they're " +
            "easier to remember too. Open Strokes on any word and watch each one drawn in turn, at " +
            "your speed.",
        demo = "Free for beginner characters (HSK 1–2, JLPT N5, beginner Korean).",
        sources = "Make Me a Hanzi (Arphic Public License, LGPL), KanjiVG (CC BY-SA 3.0).",
        sample = Sample.STROKES,
    )
    val EXAMPLES = Product(
        "examples", Kind.ADDON, "Overheard",
        "See how people really use the word", 399, Category.FEATURES,
        listOf(Line("Cantonese, Mandarin, Japanese, Korean and English, with translations"), Line("Written by real people, not made up by a computer")),
        tile = "例",
        about = "A word makes more sense once you've seen it used. Every word gets sentences written " +
            "and translated by Tatoeba's volunteers, with the reading underneath.",
        demo = "Free: every sentence for beginner words, and one for every other word.",
        sources = "Tatoeba (CC BY 2.0 FR), sentences written and translated by its contributors.",
        sample = Sample.EXAMPLES,
    )
    val FONTS = Product(
        "fonts", Kind.ADDON, "Inkwell",
        "Brush, book and handwritten fonts, and five more emoji styles", 499, Category.FONTS,
        listOf(
            Line("Chinese: book (Song), handwritten, rounded and brush"),
            Line("Japanese: textbook, Mincho, rounded and brush"),
            Line("Korean: Myeongjo, Dodum, pen and brush"),
            Line("English: easy-reading and handwritten letters for the keys"),
            Line("Emoji: Noto 3D, Twemoji, OpenMoji, Blobmoji and black-and-white Noto Emoji"),
        ),
        tile = "Aa",
        about = "Characters look different on a menu, a street sign or a handwritten note. Seeing them " +
            "in different hands while you type helps you read them anywhere. Pick a font for each " +
            "language, and the emoji you like best.",
        demo = "Try every style in Settings > Fonts first.",
        sources = "Fonts from Google Fonts and their makers, under the SIL Open Font License; emoji " +
            "under CC BY 4.0 (Twemoji), CC BY-SA 4.0 (OpenMoji), Apache 2.0 (Blobmoji) and OFL (Noto).",
        sample = Sample.FONTS,
    )

    val CARD_EN = Product(
        "card_en", Kind.ADDON, "The English Handbook",
        "Where a word comes from, how it sounds, and how to use it", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with phrasal verbs and idioms"),
            Line("US and UK pronunciation in IPA, with the stressed syllable marked"),
            Line("Every form: plurals, past tenses, comparatives, irregular ones marked"),
            Line("Where the word comes from, its roots, prefixes and suffixes"),
            Line("Words that mean the same, and the opposite"),
        ),
        tile = "En",
        about = "Know \"present\" but not why it's said two ways? Hold a word to see how it sounds in " +
            "the US and the UK, where the stress falls, every form it takes, and the Latin or Old " +
            "English it grew from.",
        demo = "The full card for the 3,000 most common words, free, for as long as you like.",
        sources = "Wiktionary via kaikki.org (CC BY-SA 4.0), CMU Pronouncing Dictionary (BSD), " +
            "WordNet 3.0 (WordNet licence), word frequencies from wordfreq (CC BY-SA 4.0).",
        sample = Sample.ENGLISH,
    )

    val addons = listOf(CARD_ZH, CARD_JA, CARD_KO, CARD_EN, HANDWRITING, STROKES, EXAMPLES, FONTS)

    // ------------------------------------------------------------------ packs and the whole lot

    private val shared = listOf(HANDWRITING.id, STROKES.id, EXAMPLES.id)
    private val sharedLine = Line("${HANDWRITING.name}, ${STROKES.name} and ${EXAMPLES.name}")

    private fun packAbout(card: Product, fonts: String) = "${card.name}, ${HANDWRITING.name}, ${STROKES.name} and " +
        "${EXAMPLES.name} together, plus a set of $fonts fonts you only get in the Satchels."

    val PACK_ZH = Product(
        "pack_zh", Kind.PACK, "The Chinese Satchel", "Cantonese and Mandarin: the Handbook and every add-on", 1499,
        lines = CARD_ZH.lines + sharedLine,
        includes = listOf(CARD_ZH.id) + shared, bonus = "Chinese fonts", tile = "中",
        about = packAbout(CARD_ZH, "Chinese"),
        demo = CARD_ZH.demo, sources = CARD_ZH.sources, sample = Sample.CHINESE,
    )
    val PACK_JA = Product(
        "pack_ja", Kind.PACK, "The Japanese Satchel", "The Handbook and every add-on for Japanese", 1499,
        lines = CARD_JA.lines + sharedLine,
        includes = listOf(CARD_JA.id) + shared, bonus = "Japanese fonts", tile = "日",
        about = packAbout(CARD_JA, "Japanese"),
        demo = CARD_JA.demo, sources = CARD_JA.sources, sample = Sample.JAPANESE,
    )
    val PACK_KO = Product(
        "pack_ko", Kind.PACK, "The Korean Satchel", "The Handbook and every add-on for Korean", 1499,
        lines = CARD_KO.lines + sharedLine,
        includes = listOf(CARD_KO.id) + shared, bonus = "Korean fonts", tile = "韓",
        about = packAbout(CARD_KO, "Korean"),
        demo = CARD_KO.demo, sources = CARD_KO.sources, sample = Sample.KOREAN,
    )
    val FULL = Product(
        "full", Kind.FULL, "The Whole Lychee", "Every language and add-on, and the ones still to come", 2999,
        lines = listOf(
            Line("All four Satchels: Chinese, Japanese, Korean and English"),
            Line("${HANDWRITING.name}, ${STROKES.name} and ${EXAMPLES.name}"),
            Line("All of ${FONTS.name}"),
            Line("Every add-on Lychee makes later, at no extra cost"),
        ),
        includes = addons.map { it.id }, tile = "荔",
        about = "Buy it once and you have everything: every language, every add-on and every font, " +
            "plus whatever Lychee adds later. Satchels you already own come off the price.",
        demo = "Every add-on's free trial applies.",
        sources = "Every source above, each under its own licence (Settings > About > Licences).",
        sample = Sample.ALL,
    )

    val PACK_EN = Product(
        "pack_en", Kind.PACK, "The English Satchel", "The Handbook and every add-on for English", 1499,
        lines = CARD_EN.lines + Line("${HANDWRITING.name} and ${EXAMPLES.name} in English"),
        includes = listOf(CARD_EN.id, HANDWRITING.id, EXAMPLES.id), bonus = "English fonts", tile = "En",
        about = "${CARD_EN.name}, ${HANDWRITING.name} and ${EXAMPLES.name} together, plus a set of " +
            "English fonts you only get in the Satchels.",
        demo = CARD_EN.demo, sources = CARD_EN.sources, sample = Sample.ENGLISH,
    )

    val packs = listOf(PACK_ZH, PACK_JA, PACK_KO, PACK_EN)

    /** Everything sold, Complete first. */
    val all = listOf(FULL) + packs + addons

    fun byId(id: String): Product? = all.firstOrNull { it.id == id }

    /** "$14.99". */
    fun price(cents: Int): String = "$%d.%02d".format(cents / 100, cents % 100)

    /** What a pack's or Complete's add-ons cost one by one (its bonus not counted). */
    fun value(p: Product): Int = p.includes.sumOf { byId(it)?.cents ?: 0 }

    /** The packs (and Complete) that include the add-on [id]. */
    fun includedIn(id: String): List<Product> = (packs + FULL).filter { id in it.includes }

    /**
     * The add-on a downloadable pack is part of (shown on that add-on's page, not as a listing of
     * its own): Brushwork's strokes, Overheard's sentences, and every font and emoji style
     * (Inkwell's), but your own fonts.
     */
    fun forPack(packId: String): Product? = when {
        packId == "strokes_zh" -> STROKES
        packId == "examples" -> EXAMPLES
        packId.endsWith("_custom") -> null
        packId.startsWith("emoji_") || packId.startsWith("font_") -> FONTS
        else -> null
    }

    /** Whether [id] is unlocked: everything, while there is no billing. */
    @Suppress("UNUSED_PARAMETER")
    fun owns(context: Context, id: String): Boolean = FREE_FOR_NOW

    /**
     * Why paid add-ons can't be offered on this phone, said plainly, or null when they can
     * (PAID.md). Root and an unlocked bootloader are never a reason. Emulators, faked or
     * software-stored keys and revoked phone models are found when an add-on is unlocked, and
     * that step says why too.
     */
    fun unavailableReason(context: Context): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P ->
            "Paid add-ons will need Android 9 or newer, which can keep their keys inside the " +
                "phone's security chip. This phone has Android ${Build.VERSION.RELEASE}. Everything " +
                "free, and every free trial, keeps working."
        !hasGooglePlay(context) ->
            "Paid add-ons will be bought through Google Play, which isn't on this phone. Lychee's " +
                "own way to buy, for phones without Google Play, comes later. Everything free, and " +
                "every free trial, keeps working."
        else -> null
    }

    private fun hasGooglePlay(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.android.vending", 0); true }.getOrDefault(false)
}
