package app.bumboard.fonts

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import app.bumboard.Feature
import app.bumboard.Features
import app.bumboard.packs.PackImporter
import app.bumboard.packs.Packs
import java.io.File

/**
 * The fonts BumBoard draws with, chosen in Settings > Fonts: a Chinese font for candidates,
 * the dictionary card and (optionally) the keys, and an emoji font for the emoji panel. They
 * only change how BumBoard shows text; apps draw what is typed in their own fonts.
 *
 * Typefaces are loaded once per choice and kept; the font files are memory-mapped by Android.
 */
object BumFonts {
    private const val TAG = "BumFonts"
    const val PREF_CHINESE = "bum_font_zh"
    const val PREF_CHINESE_KEYS = "bum_font_zh_keys"
    const val PREF_EMOJI = "bum_font_emoji"
    private const val BUNDLED_EMOJI = "bumboard/NotoColorEmoji.ttf"

    private var chineseKey: String? = null
    private var chineseFace: Typeface? = null
    private var emojiKey: String? = null
    private var emojiFace: Typeface? = null
    private var bundledEmoji: Typeface? = null

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    /** The chosen pack's font file, or null for the default (or when it went missing). */
    private fun chosenFile(context: Context, pref: String): File? {
        val id = prefs(context).getString(pref, null)?.takeIf { it.isNotEmpty() } ?: return null
        return Packs.mainFile(context, id)
    }

    /** Cache key: the file and when it last changed (a re-import replaces it). */
    private fun keyOf(file: File?) = file?.let { "${it.path}:${it.lastModified()}" } ?: ""

    /** The chosen Chinese font, or null for the system's. */
    @JvmStatic
    fun chinese(context: Context): Typeface? {
        if (!Features.isEnabled(Feature.CUSTOM_FONTS)) return null
        val file = chosenFile(context, PREF_CHINESE)
        val key = keyOf(file)
        if (key != chineseKey) {
            chineseKey = key
            chineseFace = file?.let { PackImporter.loadFont(it) }
        }
        return chineseFace
    }

    /** The Chinese font for key labels, when "Use on the keys" is on; otherwise null. */
    @JvmStatic
    fun keys(context: Context): Typeface? =
        if (prefs(context).getBoolean(PREF_CHINESE_KEYS, false)) chinese(context) else null

    /** BumBoard's own copy of Noto Color Emoji. */
    @Synchronized
    @JvmStatic
    fun bundledEmoji(context: Context): Typeface {
        bundledEmoji?.let { return it }
        val face = runCatching { Typeface.createFromAsset(context.applicationContext.assets, BUNDLED_EMOJI) }
            .getOrElse { Typeface.DEFAULT }
        bundledEmoji = face
        return face
    }

    /**
     * The chosen emoji font. On Android 10+ BumBoard's Noto stands in for any emoji it lacks;
     * earlier, the chosen font is used as it is.
     */
    @Synchronized
    @JvmStatic
    fun emoji(context: Context): Typeface {
        if (!Features.isEnabled(Feature.CUSTOM_EMOJI_FONTS)) return bundledEmoji(context)
        val file = chosenFile(context, PREF_EMOJI) ?: return bundledEmoji(context)
        val key = keyOf(file)
        if (key != emojiKey) {
            emojiKey = key
            emojiFace = withNotoFallback(context, file) ?: PackImporter.loadFont(file)
        }
        return emojiFace ?: bundledEmoji(context)
    }

    private fun withNotoFallback(context: Context, file: File): Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val chosen = FontFamily.Builder(Font.Builder(file).build()).build()
            val noto = FontFamily.Builder(Font.Builder(context.applicationContext.assets, BUNDLED_EMOJI).build()).build()
            Typeface.CustomFallbackBuilder(chosen).addCustomFallback(noto).build()
        } catch (e: Exception) {
            Log.w(TAG, "emoji font fallback unavailable", e)
            null
        }
    }
}
