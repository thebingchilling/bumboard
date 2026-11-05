package app.lychee.ui

import android.content.Context
import android.preference.PreferenceManager
import android.view.ContextThemeWrapper
import com.android.inputmethod.latin.R

/**
 * The keyboard's colours, chosen in Settings > Theme: neutral grey functional keys like
 * Gboard's (the default), keys tinted from the wallpaper, or one of Lychee's palettes, which
 * colour the whole keyboard (background, letter and functional keys, Enter), light or dark.
 * Each is a theme overlay (LycheeColors.*) setting the lycheeColor* attributes that every key
 * drawable, the keyboard's styles and the panels read.
 */
object LycheeColorStyle {
    const val PREF = "lychee_color_style"
    const val NEUTRAL = "neutral"
    const val WALLPAPER = "wallpaper"
    const val ROSE = "rose"
    const val OCEAN = "ocean"
    const val FOREST = "forest"
    const val LAVENDER = "lavender"
    const val SUNSET = "sunset"
    const val MONO = "mono"
    val ALL = listOf(NEUTRAL, WALLPAPER, ROSE, OCEAN, FOREST, LAVENDER, SUNSET, MONO)

    @Suppress("DEPRECATION")
    @JvmStatic
    fun get(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, null) ?: NEUTRAL

    @JvmStatic
    fun isNeutral(context: Context) = get(context) == NEUTRAL

    /** The theme overlay for the chosen style. */
    @JvmStatic
    fun overlay(context: Context): Int = overlayOf(get(context))

    /** The theme overlay of the style [style]. */
    @JvmStatic
    fun overlayOf(style: String): Int = when (style) {
        WALLPAPER -> R.style.LycheeColors_Wallpaper
        ROSE -> R.style.LycheeColors_Rose
        OCEAN -> R.style.LycheeColors_Ocean
        FOREST -> R.style.LycheeColors_Forest
        LAVENDER -> R.style.LycheeColors_Lavender
        SUNSET -> R.style.LycheeColors_Sunset
        MONO -> R.style.LycheeColors_Mono
        else -> R.style.LycheeColors_Neutral
    }

    /** A colour attribute ([attr], e.g. R.attr.lycheeColorKey) in the chosen style. */
    @JvmStatic
    fun color(context: Context, attr: Int): Int {
        val a = themed(context).obtainStyledAttributes(intArrayOf(attr))
        val c = a.getColor(0, 0)
        a.recycle()
        return c
    }

    /** A swatch of [style]: background, letter key, functional key and accent. */
    @JvmStatic
    fun swatch(context: Context, style: String): IntArray {
        val a = ContextThemeWrapper(context, overlayOf(style)).obtainStyledAttributes(
            intArrayOf(R.attr.lycheeColorBackground, R.attr.lycheeColorKey, R.attr.lycheeColorFunctional, R.attr.lycheeColorAccent),
        )
        val out = IntArray(4) { a.getColor(it, 0) }
        a.recycle()
        return out
    }

    /** The functional keys' colours (fill, pressed, content) in the chosen style. */
    @JvmStatic
    fun functionalColors(context: Context): IntArray {
        val a = themed(context).obtainStyledAttributes(
            intArrayOf(R.attr.lycheeColorFunctional, R.attr.lycheeColorFunctionalPressed, R.attr.lycheeColorOnFunctional),
        )
        val out = IntArray(3) { a.getColor(it, 0) }
        a.recycle()
        return out
    }

    /** [context] with the chosen style applied, for loading key drawables outside the keyboard. */
    @JvmStatic
    fun themed(context: Context): Context = ContextThemeWrapper(context, overlay(context))
}
