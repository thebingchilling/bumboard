package app.lychee.ui

import android.content.Context
import android.preference.PreferenceManager
import android.view.ContextThemeWrapper
import com.android.inputmethod.latin.R

/**
 * The keyboard's colour style, chosen in Settings > Theme: neutral grey functional keys like
 * Gboard's (the default), functional keys tinted from the wallpaper, or one of Lychee's
 * palettes (rose, ocean, forest). It is a theme overlay (LycheeColors.*) that sets the
 * functional keys' colour attributes.
 */
object LycheeColorStyle {
    const val PREF = "lychee_color_style"
    const val NEUTRAL = "neutral"
    const val WALLPAPER = "wallpaper"
    const val ROSE = "rose"
    const val OCEAN = "ocean"
    const val FOREST = "forest"
    val ALL = listOf(NEUTRAL, WALLPAPER, ROSE, OCEAN, FOREST)

    @Suppress("DEPRECATION")
    @JvmStatic
    fun get(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, null) ?: NEUTRAL

    @JvmStatic
    fun isNeutral(context: Context) = get(context) == NEUTRAL

    /** The theme overlay for the chosen style. */
    @JvmStatic
    fun overlay(context: Context): Int = when (get(context)) {
        WALLPAPER -> R.style.LycheeColors_Wallpaper
        ROSE -> R.style.LycheeColors_Rose
        OCEAN -> R.style.LycheeColors_Ocean
        FOREST -> R.style.LycheeColors_Forest
        else -> R.style.LycheeColors_Neutral
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
