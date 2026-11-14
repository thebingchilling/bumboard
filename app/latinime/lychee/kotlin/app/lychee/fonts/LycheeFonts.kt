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
 *  - the keyboard's font, for everything on the keyboard (keys, suggestions, panels, the word
 *    card): Noto Sans, Google Sans Flex, the phone's font,
 *    LXGW WenKai or the user's own;
 *  - a font each for Chinese, Japanese and Korean (candidates, the word card, CJK key labels):
 *    LXGW WenKai, the phone's font or the user's own;
 *  - emoji: always Lychee's own Noto Color Emoji (the newest release).
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

    /**
     * Whether Lychee's own Noto Color Emoji can be drawn: it is Google Fonts' COLRv1 font (the
     * newest emoji, Google's current art), which Android draws from Android 13; before that the
     * phone's own emoji are used instead.
     */
    @JvmStatic
    val bundledEmojiDrawable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private const val BUNDLED_WENKAI = "lychee/LXGWWenKai-Regular.ttf"
    /** WenKai Medium, cut to the keys' letters and symbols: the keyboard font "LXGW WenKai". */
    private const val KEYS_WENKAI = "lychee/LXGWWenKaiKeys.ttf"
    /** Google Sans Flex, the interface font (fetched at build time; see scripts/fetch-fonts.sh). */
    const val UI_FONT = "lychee/GoogleSansFlex.ttf"
    /** Noto Sans (SIL OFL), the keyboard's font: it has the symbols Google Sans Flex lacks (⅙ ...). */
    const val KEYS_FONT = "lychee/NotoSans.ttf"
    /** The keyboard font choice for Google Sans Flex (the default, "", is Noto Sans). */
    const val GSF = "gsf"

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
        loaded.keys.filter { it.startsWith("/") || it.startsWith("emoji:") || it.startsWith("keys:") }.forEach { loaded.remove(it) }
        styled.clear()
        // their fallbacks include the emoji font chosen
        notoFaces.clear()
        gsfFaces.clear()
    }

    /**
     * The emoji font chosen, as a fallback family for the keyboard's fonts: so every emoji on
     * the keyboard (keys, suggestions, candidates, panels) is drawn by it, in colour, whatever
     * the phone's own emoji font is (its variation selectors pick the emoji form of ❤️, ☀️ ...).
     * Null for the phone's own emoji, which the system fallback brings.
     */
    private fun emojiFallback(app: Context): FontFamily? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            when (val c = choice(app, PREF_EMOJI)) {
                SYSTEM -> null
                "" -> if (bundledEmojiDrawable) FontFamily.Builder(Font.Builder(app.assets, BUNDLED_EMOJI).build()).build() else null
                else -> packFile(app, c)?.let { FontFamily.Builder(Font.Builder(it).build()).build() }
                    ?: if (bundledEmojiDrawable) FontFamily.Builder(Font.Builder(app.assets, BUNDLED_EMOJI).build()).build() else null
            }
        }.getOrNull()
    }

    /** [builder] given Lychee's symbols at [weight] and then the emoji font, before the phone's. */
    private fun addFallbacks(app: Context, builder: Typeface.CustomFallbackBuilder, weight: Int) {
        for (n in 0 until 3) {
            runCatching {
                Font.Builder(app.assets, "lychee/LycheeSymbols$n.ttf")
                    .setFontVariationSettings("'wght' $weight").setWeight(weight.coerceIn(1, 1000)).build()
            }.getOrNull()?.let { builder.addCustomFallback(FontFamily.Builder(it).build()) }
        }
        emojiFallback(app)?.let { builder.addCustomFallback(it) }
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
     * The font of the keyboard's own panels and menus at [weight] (400 regular, 500 medium,
     * ...): the keyboard font when one is chosen in Settings > Fonts (so it is the whole
     * keyboard's, not only the keys'), otherwise the app's: Google Sans Flex, or the phone's
     * sans-serif when chosen (or when the font is not in the APK).
     */
    @JvmStatic
    fun ui(context: Context, weight: Int = 400): Typeface = keysLatin(context, weight)

    /**
     * The font of what goes with the candidates (their readings and meanings, the word card):
     * the keyboard font too, so everything on the keyboard is in the one font chosen in
     * Settings > Fonts (other fonts only stand in for characters it lacks).
     */
    @JvmStatic
    fun appFont(context: Context, weight: Int = 400): Typeface = keysLatin(context, weight)

    /** The app's own font at [weight] (Settings), whatever the keyboard font. */
    @JvmStatic
    fun settingsFont(context: Context, weight: Int = 400): Typeface =
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // what Google Sans Flex lacks (∞ ★ ✓ ‰ ← ♥ ...) from Google's Noto fonts, cut to
                // just those symbols (scripts/subset-symbols.py), then the phone's fonts
                // (vulgar fractions such as ⅙, which Google Sans Flex lacks, come from Noto Sans)
                val font = Font.Builder(app.assets, UI_FONT)
                    .setFontVariationSettings("'wght' $weight").setWeight(weight.coerceIn(1, 1000)).build()
                val builder = Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                addFallbacks(app, builder, weight)
                // nothing from WenKai here: an English symbol never takes the Kai look; what
                // Google's fonts lack comes from the phone's sans-serif
                builder.setSystemFallback("sans-serif").build()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            "" -> noto(context, weight)
            GSF -> gsf(context, weight)
            SYSTEM -> system(weight)
            WENKAI -> wenkaiKeys(context)?.let { styledLike(it, bold) } ?: noto(context, weight)
            else -> packKeysFace(context, c, bold) ?: noto(context, weight)
        }
    }

    /** The user's own keyboard font, with Lychee's symbols and the emoji font behind it. */
    private fun packKeysFace(context: Context, id: String, bold: Boolean): Typeface? {
        if (!Features.isEnabled(Feature.CUSTOM_FONTS)) return null
        val file = packFile(context, id) ?: return null
        val app = app(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            load("keys:${file.path}:${file.lastModified()}:$bold") {
                runCatching {
                    val font = Font.Builder(file).setWeight(if (bold) 700 else 400).build()
                    val builder = Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                    addFallbacks(app, builder, if (bold) 500 else 400)
                    builder.setSystemFallback("sans-serif").build()
                }.getOrNull()
            }?.let { return it }
        }
        return packFace(context, id)?.let { styledLike(it, bold) }
    }

    private val notoFaces = HashMap<Int, Typeface>()

    /**
     * Noto Sans at [weight], the keys' font: then Noto's other symbols (the same cut as for
     * Google Sans Flex) and the phone's fonts; the interface font when it is not in the APK.
     */
    @Synchronized
    @JvmStatic
    fun noto(context: Context, weight: Int = 400): Typeface {
        notoFaces[weight]?.let { return it }
        val app = app(context)
        val face = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val font = Font.Builder(app.assets, KEYS_FONT)
                    .setFontVariationSettings("'wght' $weight").setWeight(weight.coerceIn(1, 1000)).build()
                val builder = Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                addFallbacks(app, builder, weight)
                builder.setSystemFallback("sans-serif").build()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(app.assets, KEYS_FONT).setFontVariationSettings("'wght' $weight").build()
            } else {
                Typeface.createFromAsset(app.assets, KEYS_FONT)
            }
        }.onFailure { Log.w(TAG, "keyboard font unavailable", it) }.getOrNull() ?: gsf(context, weight)
        notoFaces[weight] = face
        return face
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
        val face = keysLatin(context, weight)
        // symbols the keyboard font lacks in the phone's sans-serif rather than blank (WenKai only
        // when it is the keyboard font: Google Sans Flex's symbols stay in Google's style)
        if (label != null && label.isNotEmpty() && !hasGlyph(face, label)) {
            if (choice(context, PREF_KEYS) == WENKAI) wenkai(context)?.let { return it }
            // Noto Sans (with Noto's symbols) before the phone's font
            noto(context, weight).takeIf { hasGlyph(it, label) }?.let { return it }
            return if (weight >= 500) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return face
    }

    /**
     * Puts the keyboard font on every text in [root] still in a stock font (the phone's
     * default, bold or not), so nothing on the keyboard shows another font by accident; texts
     * given a font on purpose (Chinese, emoji, ...) keep theirs.
     */
    @JvmStatic
    fun applyTree(root: android.view.View) {
        val ctx = root.context
        fun walk(v: android.view.View) {
            // Chinese, Japanese and Korean text keeps its own font (Settings > Fonts), and the
            // buttons with a character on them keep their fixed heavy one
            if (v is android.widget.TextView && v !in fixedViews && !isCjk(v.text)) {
                val tf = v.typeface
                if (tf == null || tf == Typeface.DEFAULT || tf == Typeface.DEFAULT_BOLD ||
                    tf == Typeface.SANS_SERIF || tf == Typeface.MONOSPACE) {
                    val bold = tf != null && tf.isBold
                    val face = keysLatin(ctx, if (bold) 500 else 400)
                    if (face != tf) v.typeface = face
                }
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    /** Views whose font is fixed (see [heavy]); [applyTree] leaves them alone. */
    private val fixedViews = java.util.Collections.newSetFromMap(java.util.WeakHashMap<android.view.View, Boolean>())

    /**
     * The fixed font of buttons with a character on them (繁/简, 英, 粵/普): the phone's sans-serif
     * at its heaviest, whatever fonts are chosen, so they read as buttons, not text.
     */
    @JvmStatic
    fun heavy(): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 900, false)
        else Typeface.DEFAULT_BOLD

    /** Gives [view] the [heavy] font for good. */
    @JvmStatic
    fun fixHeavy(view: android.widget.TextView) {
        view.typeface = heavy()
        fixedViews += view
    }

    private val glyphPaint = Paint()
    private val glyphCache = HashMap<Pair<Typeface, String>, Boolean>()

    @Synchronized
    private fun hasGlyph(face: Typeface, label: CharSequence): Boolean =
        glyphCache.getOrPut(face to label.toString()) {
            glyphPaint.typeface = face
            glyphPaint.hasGlyph(label.toString())
        }

    // ------------------------------------------------------------------ Chinese, Japanese, Korean

    /** LXGW WenKai from the APK (fetched at build time; see scripts/fetch-fonts.sh), if there. */
    @JvmStatic
    fun wenkai(context: Context): Typeface? = load(BUNDLED_WENKAI) {
        runCatching { Typeface.createFromAsset(app(context).assets, BUNDLED_WENKAI) }.getOrNull()
    }

    /**
     * The keyboard font "LXGW WenKai": its Medium weight (a little bolder than the candidates'
     * Regular, so the keys read clearly), with the Regular for the characters the cut lacks.
     */
    @JvmStatic
    fun wenkaiKeys(context: Context): Typeface? = load(KEYS_WENKAI) {
        val app = app(context)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val builder = Typeface.CustomFallbackBuilder(FontFamily.Builder(Font.Builder(app.assets, KEYS_WENKAI).build()).build())
                runCatching { Font.Builder(app.assets, BUNDLED_WENKAI).build() }.getOrNull()?.let {
                    builder.addCustomFallback(FontFamily.Builder(it).build())
                }
                builder.setSystemFallback("sans-serif").build()
            } else {
                Typeface.createFromAsset(app.assets, KEYS_WENKAI)
            }
        }.getOrNull()
    } ?: wenkai(context)

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
        // English (the English word card): the app's font, never the Kai one
        if (lang == "en" && !isCjk(text)) appFont(context) else cjk(context, scriptOf(text) ?: lang)

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
    fun bundledEmoji(context: Context): Typeface = if (!bundledEmojiDrawable) Typeface.DEFAULT else load(BUNDLED_EMOJI) {
        val assets = app(context).assets
        // the phone's fonts behind it, for anything it lacks
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching {
            Typeface.CustomFallbackBuilder(FontFamily.Builder(Font.Builder(assets, BUNDLED_EMOJI).build()).build())
                .setSystemFallback("sans-serif").build()
        }.getOrNull() else null) ?: runCatching { Typeface.createFromAsset(assets, BUNDLED_EMOJI) }.getOrNull()
    } ?: Typeface.DEFAULT

    /**
     * The emoji font chosen in Settings > Fonts: Lychee's own Noto Color Emoji (the default, the
     * newest), a downloaded one (Noto 3D), the user's own, or the phone's. Whatever the chosen
     * font lacks (an emoji newer than it, a rare sequence) comes from the phone's emoji rather
     * than falling apart into pieces.
     */
    @JvmStatic
    fun emoji(context: Context): Typeface = when (val c = choice(context, PREF_EMOJI)) {
        "" -> bundledEmoji(context)
        SYSTEM -> Typeface.DEFAULT
        else -> packEmoji(context, c) ?: bundledEmoji(context)
    }

    /** A downloaded or own emoji font, with the phone's fonts behind it. */
    private fun packEmoji(context: Context, id: String): Typeface? {
        val file = packFile(context, id) ?: return null
        return load("emoji:${file.path}:${file.lastModified()}") {
            // what the chosen font lacks from Lychee's Noto Color Emoji (the newest), only then
            // the phone's: an emoji newer than the font still shows whole
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching {
                val builder = Typeface.CustomFallbackBuilder(FontFamily.Builder(Font.Builder(file).build()).build())
                runCatching { Font.Builder(app(context).assets, BUNDLED_EMOJI).build() }.getOrNull()?.takeIf { bundledEmojiDrawable }
                    ?.let { builder.addCustomFallback(FontFamily.Builder(it).build()) }
                builder.setSystemFallback("sans-serif").build()
            }.getOrNull() ?: PackImporter.loadFont(file) else PackImporter.loadFont(file)
        }
    }

    /** Emoji new in each Emoji version, newest first (data/packs/fonts/emoji_fonts.py checks the same). */
    private val EMOJI_VERSIONS = listOf(
        "18.0" to listOf("\uD83D\uDED9", "\uD83E\uDEDD", "\uD83E\uDEEB", "\uD83E\uDE8B"),
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
