package app.bumboard.ui

import android.content.Context
import com.android.inputmethod.latin.R

/**
 * Material 3 colour roles for BumBoard's View-based keyboard UI (emoji panel, emoji search,
 * dictionary popup). They come from the same resources as the keyboard theme
 * (bumboard_m3_colors.xml): the wallpaper-based system palette on Android 12+, BumBoard's teal
 * palette before, light or dark with the system. Compose screens use BumTheme instead.
 */
data class M3Colors(
    /** Behind the keys: the keyboard, the emoji panel, the strip. */
    val surface: Int,
    /** A letter key. */
    val surfaceContainer: Int,
    val surfaceContainerPressed: Int,
    /** Popups and search fields over the keyboard. */
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    /** A functional key (shift, delete, ?123, emoji, ABC). */
    val secondaryContainer: Int,
    val secondaryContainerPressed: Int,
    val onSecondaryContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
) {
    companion object {
        fun of(ctx: Context): M3Colors {
            fun c(id: Int) = ctx.getColor(id)
            return M3Colors(
                surface = c(R.color.bum_m3_kb_background),
                surfaceContainer = c(R.color.bum_m3_key),
                surfaceContainerPressed = c(R.color.bum_m3_key_pressed),
                surfaceContainerHigh = c(R.color.bum_m3_surface_high),
                onSurface = c(R.color.bum_m3_on_key),
                onSurfaceVariant = c(R.color.bum_m3_on_key_variant),
                primary = c(R.color.bum_m3_primary),
                onPrimary = c(R.color.bum_m3_on_primary),
                primaryContainer = c(R.color.bum_m3_primary_container),
                onPrimaryContainer = c(R.color.bum_m3_on_primary_container),
                secondaryContainer = c(R.color.bum_m3_key_functional),
                secondaryContainerPressed = c(R.color.bum_m3_key_functional_pressed),
                onSecondaryContainer = c(R.color.bum_m3_on_key_functional),
                outline = c(R.color.bum_m3_outline),
                outlineVariant = c(R.color.bum_m3_outline_variant),
            )
        }
    }
}
