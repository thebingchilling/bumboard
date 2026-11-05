package app.lychee.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.preference.PreferenceManager
import java.util.Calendar

/**
 * How the keys look, from Settings > Theme: light, dark or following the time of day, key
 * borders (backgrounds behind the letter keys) on or off, and how round their corners are.
 */
object KeyStyle {
    const val PREF_THEME_MODE = "lychee_theme_mode"
    const val PREF_BORDERS = "lychee_key_borders"
    const val PREF_ROUNDNESS = "lychee_key_roundness"
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    /** dark from 19:00 to 07:00 */
    const val MODE_TIME = "time"
    /** Corner roundness in percent; 33 is the stock 8 dp. */
    const val DEFAULT_ROUNDNESS = 33
    private const val MAX_RADIUS_DP = 24f

    /** Read by KeyboardView while drawing; refreshed by [load]. */
    @JvmStatic
    @Volatile
    var borders = true
        private set

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    /** Re-reads the settings; returns a signature that changes when the keyboard must be rebuilt. */
    @JvmStatic
    fun load(context: Context): String {
        val p = prefs(context)
        borders = p.getBoolean(PREF_BORDERS, true)
        return "${night(context)}:${p.getInt(PREF_ROUNDNESS, DEFAULT_ROUNDNESS)}:$borders"
    }

    /** True for dark, false for light, null to follow the system. */
    @JvmStatic
    fun night(context: Context): Boolean? = when (prefs(context).getString(PREF_THEME_MODE, MODE_SYSTEM)) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        MODE_TIME -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 19 || it < 7 }
        else -> null
    }

    /**
     * The keyboard's configuration override: light or dark as chosen ([night]), and the
     * keyboard's own language for its menus ([locale], the current keyboard's), or null for none.
     */
    @JvmStatic
    fun override(context: Context, locale: java.util.Locale?): Configuration? {
        val night = night(context)
        if (night == null && locale == null) return null
        val base = context.resources.configuration
        return Configuration().apply {
            if (night != null) {
                uiMode = (base.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            if (locale != null) setLocale(locale)
        }
    }

    /** Gives the key shapes in [drawable] the chosen corner radius (pills stay pills). */
    @JvmStatic
    fun round(context: Context, drawable: Drawable?): Drawable? {
        drawable ?: return null
        val roundness = prefs(context).getInt(PREF_ROUNDNESS, DEFAULT_ROUNDNESS)
        if (roundness == DEFAULT_ROUNDNESS) return drawable
        val density = context.resources.displayMetrics.density
        val radius = roundness / 100f * MAX_RADIUS_DP * density
        val pill = 50 * density
        fun walk(d: Drawable) {
            when (d) {
                is DrawableContainer -> (d.constantState as? DrawableContainer.DrawableContainerState)
                    ?.children?.filterNotNull()?.forEach(::walk)
                is LayerDrawable -> for (i in 0 until d.numberOfLayers) walk(d.getDrawable(i))
                is GradientDrawable -> if (d.cornerRadius < pill) d.cornerRadius = radius
            }
        }
        val mutable = drawable.mutate()
        walk(mutable)
        return mutable
    }
}
