package app.lychee.fonts

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import app.lychee.Feature
import app.lychee.Features
import app.lychee.packs.PackImporter
import app.lychee.packs.Packs
import java.io.File

/**
 * The fonts Lychee draws with, chosen in Settings > Fonts:
 *  - the app's own font: Google Sans Flex or the phone's font (no others);
 *  - the keyboard's font, for the letters on the keys and the suggestions: Google Sans Flex,
 *    LXGW WenKai or the user's own;
 *  - a font each for Chinese, Japanese and Korean (candidates, the word card, CJK key labels):
 *    LXGW WenKai, the phone's font or the user's own;
 *  - the emoji font: Lychee's Noto Color Emoji, the phone's emoji or a downloaded one.
 * They only change how Lychee shows text; apps draw what is typed in their own fonts.
 *
 * Typefaces are loaded once per choice and kept (the font files are memory-mapped by Android);
 * changing a choice clears them.
 */
object LycheeFonts {
    private const val TAG = "LycheeFonts"
    const val PREF_UI = "lychee_font_ui"
    const val PREF_KEYS = "lychee_font_keys"
    const val PREF_CHINESE = "lychee_font_zh"
    const val PREF_JAPANESE = "lychee_font_ja"
    const val PREF_KOREAN = "lychee_font_ko"
    const val PREF_EMOJI = "lychee_font_emoji"
    private val PREFS = setOf(PREF_UI, PREF_KEYS, PREF_CHINESE, PREF_JAPANESE, PREF_KOREAN, PREF_EMOJI, Packs.PREF_CHANGED)

    /** Choice values: the phone's own font, and LXGW WenKai (for the keys). An empty choice is
     *  the default (Google Sans Flex for the app and keys, WenKai for CJK, Noto for emoji);
     *  anything else is a pack id. */
    const val SYSTEM = "system"
    const val WENKAI = "wenkai"

    private const val BUNDLED_EMOJI = "lychee/NotoColorEmoji.ttf"
    private const val BUNDLED_WENKAI = "lychee/LXGWWenKai-Regular.ttf"
    /** Google Sans Flex, the interface font (fetched at build time; see scripts/fetch-fonts.sh). */
    const val UI_FONT = "lychee/GoogleSansFlex.ttf"

    private var appContext: Context? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Choices as read (cleared when one changes). */
    private val choices = HashMap<String, String>()
    /** Loaded fonts by cache key (a pack's file, or a bundled font's name). */
    private val loaded = HashMap<String, Typeface?>()
    private val gsfFaces = HashMap<Int, Typeface>()
    private var gsfMissing = false

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    private fun app(context: Context): Context {
        appContext?.let { return it }
        val app = context.applicationContext ?: context
        appContext = app
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in PREFS) clear()
        }
        listener = l
        prefs(app).registerOnSharedPreferenceChangeListener(l)
        return app
    }

    @Synchronized
    private fun clear() {
        choices.clear()
        loaded.keys.filter { it.startsWith("/") }.forEach { loaded.remove(it) }
        styled.clear()
    }

    @Synchronized
    private fun choice(context: Context, pref: String): String =
        choices.getOrPut(pref) { prefs(app(context)).getString(pref, null).orEmpty() }

    /** A pack's font file, when [id] is a font pack in the catalogue and installed. */
    private fun packFile(context: Context, id: String): File? =
        Packs.byId(context, id)?.takeIf { it.kind.isFont }?.let { Packs.mainFile(context, id) }

    @Synchronized
    private fun load(key: String, make: () -> Typeface?): Typeface? =
        if (loaded.containsKey(key)) loaded[key] else make().also { loaded[key] = it }

    private fun packFace(context: Context, id: String): Typeface? {
        if (!Features.isEnabled(Feature.CUSTOM_FONTS)) return null
        val file = packFile(context, id) ?: return null
        return load("${file.path}:${file.lastModified()}") { PackImporter.loadFont(file) }
    }

    // ------------------------------------------------------------------ app font

    /** Whether the app (settings and the keyboard's panels) uses the phone's font. */
    @JvmStatic
    fun uiIsSystem(context: Context) = choice(context, PREF_UI) == SYSTEM

    /**
     * The app's font at [weight] (400 regular, 500 medium, ...): Google Sans Flex, or the
     * phone's sans-serif when chosen (or when the font is not in the APK).
     */
    @JvmStatic
    fun ui(context: Context, weight: Int = 400): Typeface =
        if (uiIsSystem(context)) system(weight) else gsf(context, weight)

    /** The app's font in place of [face]: medium for bold, regular otherwise. */
    @JvmStatic
    fun uiLike(context: Context?, face: Typeface?): Typeface? {
        val ctx = context ?: appContext ?: return face
        return ui(ctx, if (face != null && face.isBold) 500 else 400)
    }

    private fun system(weight: Int): Typeface =
        Typeface.create(if (weight >= 500) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)

    /** Google Sans Flex at [weight]; the phone's sans-serif when it is not in the APK. */
    @Synchronized
    @JvmStatic
    fun gsf(context: Context, weight: Int = 400): Typeface {
        gsfFaces[weight]?.let { return it }
        val app = app(context)
        val face = if (gsfMissing) null else runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(app.assets, UI_FONT).setFontVariationSettings("'wght' $weight").build()
            } else {
                Typeface.createFromAsset(app.assets, UI_FONT)
            }
        }.onFailure {
            gsfMissing = true
            Log.w(TAG, "interface font unavailable", it)
        }.getOrNull()
        val result = face ?: system(weight)
        gsfFaces[weight] = result
        return result
    }

    /** Whether Google Sans Flex is in the APK (Compose checks before using it). */
    @JvmStatic
    fun hasUiFont(context: Context): Boolean =
        runCatching { context.assets.open(UI_FONT).close(); true }.getOrDefault(false)

    // ------------------------------------------------------------------ keyboard font

    /** Bold and regular versions of fonts that have no weights of their own. */
    private val styled = HashMap<Pair<Typeface, Boolean>, Typeface>()

    @Synchronized
    private fun styledLike(face: Typeface, bold: Boolean): Typeface =
        if (!bold) face else styled.getOrPut(face to true) { Typeface.create(face, Typeface.BOLD) }

    /**
     * The keyboard's font for letters at [weight]: Google Sans Flex, LXGW WenKai or the
     * user's own font.
     */
    @JvmStatic
    fun keysLatin(context: Context, weight: Int = 400): Typeface {
        val bold = weight >= 500
        return when (val c = choice(context, PREF_KEYS)) {
            "" -> gsf(context, weight)
            WENKAI -> wenkai(context)?.let { styledLike(it, bold) } ?: gsf(context, weight)
            else -> packFace(context, c)?.let { styledLike(it, bold) } ?: gsf(context, weight)
        }
    }

    /** The keyboard's font in place of [face] (bold stays bold); for code without a context. */
    @JvmStatic
    fun keysLike(context: Context?, face: Typeface?): Typeface? {
        val ctx = context ?: appContext ?: return face
        return keysLatin(ctx, if (face != null && face.isBold) 500 else 400)
    }

    /**
     * The font for a key showing [label], in place of LatinIME's [base]: the language's font
     * for Chinese, Japanese and Korean labels (when the keyboard font is not WenKai, which
     * covers them), the keyboard font otherwise.
     */
    @JvmStatic
    fun keyFace(context: Context, label: CharSequence?, base: Typeface?): Typeface {
        val weight = if (base != null && base.isBold) 500 else 400
        if (label != null && isCjk(label) && choice(context, PREF_KEYS) != WENKAI) {
            forText(context, label)?.let { return it }
            return Typeface.DEFAULT
        }
        return keysLatin(context, weight)
    }

    // ------------------------------------------------------------------ Chinese, Japanese, Korean

    /** LXGW WenKai from the APK (fetched at build time; see scripts/fetch-fonts.sh), if there. */
    @JvmStatic
    fun wenkai(context: Context): Typeface? = load(BUNDLED_WENKAI) {
        runCatching { Typeface.createFromAsset(app(context).assets, BUNDLED_WENKAI) }.getOrNull()
    }

    private fun prefOf(lang: String?) = when (lang) {
        "ja" -> PREF_JAPANESE
        "ko" -> PREF_KOREAN
        else -> PREF_CHINESE
    }

    /**
     * The font for [lang] ("zh", "yue", "cmn", "ja", "ko"): LXGW WenKai by default, the user's
     * own font, or null for the phone's.
     */
    @JvmStatic
    fun cjk(context: Context, lang: String?): Typeface? = when (val c = choice(context, prefOf(lang))) {
        "" -> wenkai(context)
        SYSTEM -> null
        else -> packFace(context, c) ?: wenkai(context)
    }

    /** The Chinese font (candidates, the word card); null for the phone's. */
    @JvmStatic
    fun chinese(context: Context): Typeface? = cjk(context, "zh")

    /**
     * The font for [text]: Korean for Hangul, Japanese for kana, otherwise [lang]'s (Chinese
     * when none is given, so kanji typed in Japanese takes the Japanese font only when [lang]
     * says so).
     */
    @JvmStatic
    @JvmOverloads
    fun forText(context: Context, text: CharSequence, lang: String? = null): Typeface? =
        cjk(context, scriptOf(text) ?: lang)

    /** "ko" for Hangul, "ja" for kana, null for anything else. */
    @JvmStatic
    fun scriptOf(text: CharSequence): String? {
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.HANGUL -> return "ko"
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> return "ja"
                else -> {}
            }
            i += Character.charCount(cp)
        }
        return null
    }

    /** Whether [text] has Chinese, Japanese or Korean characters. */
    @JvmStatic
    fun isCjk(text: CharSequence): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL,
                Character.UnicodeScript.BOPOMOFO -> return true
                else -> {}
            }
            i += Character.charCount(cp)
        }
        return false
    }

    // ------------------------------------------------------------------ emoji

    /** Lychee's own copy of Noto Color Emoji. */
    @JvmStatic
    fun bundledEmoji(context: Context): Typeface = load(BUNDLED_EMOJI) {
        runCatching { Typeface.createFromAsset(app(context).assets, BUNDLED_EMOJI) }.getOrNull()
    } ?: Typeface.DEFAULT

    /**
     * The chosen emoji font: Lychee's Noto, the phone's emoji ([Typeface.DEFAULT]) or a
     * downloaded one. On Android 10+ Lychee's Noto stands in for any emoji a downloaded font
     * lacks; earlier, the font is used as it is.
     */
    @JvmStatic
    fun emoji(context: Context): Typeface {
        if (!Features.isEnabled(Feature.CUSTOM_EMOJI_FONTS)) return bundledEmoji(context)
        val c = choice(context, PREF_EMOJI)
        if (c == SYSTEM) return Typeface.DEFAULT
        if (c.isEmpty()) return bundledEmoji(context)
        val file = packFile(context, c) ?: return bundledEmoji(context)
        return load("${file.path}:${file.lastModified()}:emoji") {
            withNotoFallback(context, file) ?: PackImporter.loadFont(file)
        } ?: bundledEmoji(context)
    }

    private fun withNotoFallback(context: Context, file: File): Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val chosen = FontFamily.Builder(Font.Builder(file).build()).build()
            val noto = FontFamily.Builder(Font.Builder(app(context).assets, BUNDLED_EMOJI).build()).build()
            Typeface.CustomFallbackBuilder(chosen).addCustomFallback(noto).build()
        } catch (e: Exception) {
            Log.w(TAG, "emoji font fallback unavailable", e)
            null
        }
    }

    /** Emoji new in each Emoji version, newest first (data/packs/fonts/emoji_fonts.py checks the same). */
    private val EMOJI_VERSIONS = listOf(
        "17.0" to listOf("🫪", "🛘", "🪊", "🫈"),
        "16.0" to listOf("🫩", "🪉", "🫟"),
        "15.1" to listOf("🐦‍🔥", "🙂‍↔️"),
        "15.0" to listOf("🫨", "🩵"),
        "14.0" to listOf("🫠", "🫡"),
        "13.1" to listOf("❤️‍🔥"),
        "13.0" to listOf("🥲"),
        "12.0" to listOf("🥱"),
        "11.0" to listOf("🥰"),
    )

    /** The newest Emoji version [face] draws ("16.0"), or null when it is older than 11.0. */
    @JvmStatic
    fun emojiVersion(face: Typeface): String? {
        val paint = Paint().apply { typeface = face }
        return EMOJI_VERSIONS.firstOrNull { (_, samples) -> samples.any { paint.hasGlyph(it) } }?.first
    }

    /** The Emoji version of a choice ([PREF_EMOJI] value), or null when unknown. */
    @JvmStatic
    fun emojiVersionOf(context: Context, choice: String): String? = when (choice) {
        "" -> emojiVersion(bundledEmoji(context))
        SYSTEM -> emojiVersion(Typeface.DEFAULT)
        else -> packFile(context, choice)?.let { f -> PackImporter.loadFont(f)?.let { emojiVersion(it) } }
            ?: Packs.byId(context, choice)?.emoji
    }
}
