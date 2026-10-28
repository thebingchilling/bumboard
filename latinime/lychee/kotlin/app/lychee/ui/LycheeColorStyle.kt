package app.lychee.ui

import android.content.Context
import android.preference.PreferenceManager
import android.view.ContextThemeWrapper
import com.android.inputmethod.latin.R

/**
 * The keyboard's colour style, chosen in Settings > Theme: neutral grey functional keys like
 * Gboard's (the default), or functional keys tinted from the wallpaper. It is a theme overlay
 * (LycheeColors.*) that sets the functional keys' colour attributes.
 */
object LycheeColorStyle {
    const val PREF = "lychee_color_style"
    const val NEUTRAL = "neutral"
    const val WALLPAPER = "wallpaper"

    @Suppress("DEPRECATION")
    @JvmStatic
    fun get(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, null) ?: NEUTRAL

    @JvmStatic
    fun isNeutral(context: Context) = get(context) != WALLPAPER

    /** The theme overlay for the chosen style. */
    @JvmStatic
    fun overlay(context: Context): Int =
        if (isNeutral(context)) R.style.LycheeColors_Neutral else R.style.LycheeColors_Wallpaper

    /** [context] with the chosen style applied, for loading key drawables outside the keyboard. */
    @JvmStatic
    fun themed(context: Context): Context = ContextThemeWrapper(context, overlay(context))
}
