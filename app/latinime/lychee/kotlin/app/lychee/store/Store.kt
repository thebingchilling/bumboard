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
    enum class Sample { CHINESE, JAPANESE, KOREAN, HANDWRITING, STROKES, EXAMPLES, FONTS, ALL }

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
        "card_zh", Kind.ADDON, "Chinese In Depth",
        "The complete word card: Cantonese and Mandarin side by side", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with Cantonese and Mandarin readings side by side"),
            Line("Taiwan readings and colloquial Cantonese marked"),
            Line("HSK levels and Cantonese frequency bands"),
            Line("Measure words"),
            Line("Character breakdown and English-to-Chinese lookup"),
        ),
        tile = "學",
        about = "Hold any word you type to see everything about it: every meaning, both Cantonese and " +
            "Mandarin, how common it is, its measure word and what its characters are made of. Built " +
            "for learners who type in Chinese every day.",
        demo = "Free for every HSK 1-2 word: the complete card, no time limit.",
        sources = "CC-CEDICT (CC BY-SA 4.0), CC-Canto (CC BY-SA 3.0), Unihan (Unicode licence), " +
            "HSK lists from complete-hsk-vocabulary (MIT).",
        sample = Sample.CHINESE,
    )
    val CARD_JA = Product(
        "card_ja", Kind.ADDON, "Japanese In Depth",
        "The complete word card: pitch accent, conjugation and kanji", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with old kanji forms and Japan-made kanji noted"),
            Line("JLPT levels"),
            Line("Pitch accent, drawn over the reading"),
            Line("Verb and adjective conjugation tables"),
            Line("Kanji details: parts, school grade, frequency, readings"),
        ),
        tile = "語",
        about = "Hold any word to see how it is said (pitch accent), how it conjugates, its JLPT level " +
            "and what each kanji means and is made of. Everything a textbook would tell you, inside " +
            "your keyboard.",
        demo = "Free for every JLPT N5 word: the complete card, no time limit.",
        sources = "JMdict, KANJIDIC2 and KRADFILE (EDRDG, CC BY-SA 4.0), JLPT lists by Jonathan " +
            "Waller (CC BY), Kanjium pitch accents (CC BY-SA 4.0).",
        sample = Sample.JAPANESE,
    )
    val CARD_KO = Product(
        "card_ko", Kind.ADDON, "Korean In Depth",
        "The complete word card: pronunciation, conjugation and Hanja", 999, Category.DICTIONARIES,
        listOf(
            Line("Every meaning, with each word's Hanja"),
            Line("Beginner, intermediate and advanced levels"),
            Line("Sound changes shown, like 학교 said [학꾜]"),
            Line("Verb and adjective conjugation tables"),
            Line("Each Hanja's meaning and sound"),
        ),
        tile = "한",
        about = "Hold any word to see how it is really said, how it conjugates (irregular verbs " +
            "included), its level and the Hanja behind it, from the National Institute of Korean " +
            "Language's own learner dictionary.",
        demo = "Free for every beginner-level word: the complete card, no time limit.",
        sources = "Basic Korean Dictionary, National Institute of Korean Language (CC BY-SA 2.0 KR), " +
            "Wiktionary via kaikki.org (CC BY-SA 4.0), Unihan (Unicode licence).",
        sample = Sample.KOREAN,
    )
    val HANDWRITING = Product(
        "handwriting", Kind.ADDON, "Google Handwriting",
        "Google's ML Kit recognition, for messy, fast and joined-up writing", 599, Category.FEATURES,
        listOf(
            Line("Chinese, Japanese and Korean"),
            Line("Reads whole words and phrases, not just single characters"),
            Line("Recognition runs on your phone; nothing you write is sent"),
        ),
        tile = "✍",
        about = "Lychee's built-in handwriting is good for neat characters. Google's ML Kit reads real " +
            "handwriting: hurried, joined-up, whole phrases at once. It needs the free Lychee " +
            "handwriting app, because ML Kit's licence keeps it in an app of its own; Lychee itself " +
            "never goes online.",
        demo = "Free for single characters.",
        sources = "Google ML Kit Digital Ink Recognition (Google's terms), in the free Lychee " +
            "handwriting app. Google's models are downloaded once (about 20 MB per language).",
        sample = Sample.HANDWRITING,
        needsApp = true,
    )
    val STROKES = Product(
        "strokes", Kind.ADDON, "Stroke Order",
        "Animated stroke order on the word card", 399, Category.FEATURES,
        listOf(Line("Chinese, Japanese and Korean characters"), Line("Stroke by stroke, at your pace")),
        tile = "永",
        about = "Watch any character drawn stroke by stroke, in the right order and direction, right " +
            "on the word card.",
        demo = "Free for the beginner characters (HSK 1-2, JLPT N5, beginner Korean).",
        sources = "Make Me a Hanzi (Arphic Public License, LGPL), KanjiVG (CC BY-SA 3.0).",
        sample = Sample.STROKES,
    )
    val EXAMPLES = Product(
        "examples", Kind.ADDON, "Example Sentences",
        "Real sentences with English for the words you type", 399, Category.FEATURES,
        listOf(Line("Cantonese, Mandarin, Japanese and Korean"), Line("Written by people, not generated")),
        tile = "例",
        about = "See each word used in real sentences written by people, each with its English " +
            "translation and reading.",
        demo = "Free: every sentence for beginner words, one sentence for every other word.",
        sources = "Tatoeba (CC BY 2.0 FR), sentences written and translated by its contributors.",
        sample = Sample.EXAMPLES,
    )
    val FONTS = Product(
        "fonts", Kind.ADDON, "Font & Emoji Collection",
        "Every font and emoji style, on your keys, candidates and cards", 499, Category.FONTS,
        listOf(
            Line("Chinese: Kaiti, Song and brush styles, handwritten and rounded"),
            Line("Japanese: textbook, Mincho, rounded and brush styles"),
            Line("Korean: Myeongjo, Dodum, pen and brush styles"),
            Line("Emoji: Noto 3D, Fluent, Twemoji, OpenMoji, Blobmoji, Noto Emoji"),
        ),
        tile = "Aa",
        about = "Make Lychee look the way you like: a different font for each language, and the emoji " +
            "style you prefer. Every font is open source.",
        demo = "Preview every style in Settings > Fonts before buying.",
        sources = "Open-source fonts under the SIL Open Font License, Apache 2.0, MIT and CC BY.",
        sample = Sample.FONTS,
    )

    val addons = listOf(CARD_ZH, CARD_JA, CARD_KO, HANDWRITING, STROKES, EXAMPLES, FONTS)

    // ------------------------------------------------------------------ packs and Complete

    private val shared = listOf(HANDWRITING.id, STROKES.id, EXAMPLES.id)
    private val sharedLine = Line("Google Handwriting, Stroke Order and Example Sentences")

    val PACK_ZH = Product(
        "pack_zh", Kind.PACK, "Chinese Pack", "Cantonese and Mandarin, in full", 1499,
        lines = CARD_ZH.lines + sharedLine,
        includes = listOf(CARD_ZH.id) + shared, bonus = "Chinese fonts", tile = "中",
        about = "Everything for Chinese in one purchase: Chinese In Depth, Google Handwriting, " +
            "Stroke Order and Example Sentences, with a set of Chinese fonts only the packs include.",
        demo = CARD_ZH.demo, sources = CARD_ZH.sources, sample = Sample.CHINESE,
    )
    val PACK_JA = Product(
        "pack_ja", Kind.PACK, "Japanese Pack", "Japanese, in full", 1499,
        lines = CARD_JA.lines + sharedLine,
        includes = listOf(CARD_JA.id) + shared, bonus = "Japanese fonts", tile = "日",
        about = "Everything for Japanese in one purchase: Japanese In Depth, Google Handwriting, " +
            "Stroke Order and Example Sentences, with a set of Japanese fonts only the packs include.",
        demo = CARD_JA.demo, sources = CARD_JA.sources, sample = Sample.JAPANESE,
    )
    val PACK_KO = Product(
        "pack_ko", Kind.PACK, "Korean Pack", "Korean, in full", 1499,
        lines = CARD_KO.lines + sharedLine,
        includes = listOf(CARD_KO.id) + shared, bonus = "Korean fonts", tile = "韓",
        about = "Everything for Korean in one purchase: Korean In Depth, Google Handwriting, " +
            "Stroke Order and Example Sentences, with a set of Korean fonts only the packs include.",
        demo = CARD_KO.demo, sources = CARD_KO.sources, sample = Sample.KOREAN,
    )
    val FULL = Product(
        "full", Kind.FULL, "Lychee Complete", "Everything Lychee makes, now and in the future", 2999,
        lines = listOf(
            Line("All three packs: Chinese, Japanese and Korean in full"),
            Line("Google Handwriting, Stroke Order and Example Sentences"),
            Line("The whole Font & Emoji Collection"),
            Line("Every future Lychee add-on, at no extra cost"),
        ),
        includes = addons.map { it.id }, tile = "荔",
        about = "One purchase, everything: every language in full, every feature and every font, and " +
            "every add-on Lychee makes from now on. Packs you already own count towards it.",
        demo = "Every add-on's free trial applies.",
        sources = "Every source above, each under its own licence (Settings > About > Licences).",
        sample = Sample.ALL,
    )

    val packs = listOf(PACK_ZH, PACK_JA, PACK_KO)

    /** Everything sold, Complete first. */
    val all = listOf(FULL) + packs + addons

    fun byId(id: String): Product? = all.firstOrNull { it.id == id }

    /** "$14.99". */
    fun price(cents: Int): String = "$%d.%02d".format(cents / 100, cents % 100)

    /** What a pack's or Complete's add-ons cost one by one (its bonus not counted). */
    fun value(p: Product): Int = p.includes.sumOf { byId(it)?.cents ?: 0 }

    /** The packs (and Complete) that include the add-on [id]. */
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
