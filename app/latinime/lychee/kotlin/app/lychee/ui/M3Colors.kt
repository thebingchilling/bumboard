package app.lychee.ui

import android.content.Context
import com.android.inputmethod.latin.R

/**
 * Material 3 colour roles for Lychee's View-based keyboard UI (emoji panel, emoji search,
 * dictionary popup). They come from the same resources as the keyboard theme
 * (lychee_m3_colors.xml), with the functional keys in the chosen [LycheeColorStyle]: the wallpaper-based system palette on Android 12+, Lychee's teal
 * palette before, light or dark with the system. Compose screens use LycheeTheme instead.
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
            val functional = LycheeColorStyle.functionalColors(ctx)
            return M3Colors(
                surface = LycheeColorStyle.color(ctx, R.attr.lycheeColorBackground),
                surfaceContainer = LycheeColorStyle.color(ctx, R.attr.lycheeColorKey),
                surfaceContainerPressed = LycheeColorStyle.color(ctx, R.attr.lycheeColorKeyPressed),
                surfaceContainerHigh = c(R.color.lychee_m3_surface_high),
                onSurface = LycheeColorStyle.color(ctx, R.attr.lycheeColorOnKey),
                onSurfaceVariant = LycheeColorStyle.color(ctx, R.attr.lycheeColorOnKeyVariant),
                primary = LycheeColorStyle.color(ctx, R.attr.lycheeColorAccent),
                onPrimary = LycheeColorStyle.color(ctx, R.attr.lycheeColorOnAccent),
                primaryContainer = c(R.color.lychee_m3_primary_container),
                onPrimaryContainer = c(R.color.lychee_m3_on_primary_container),
                secondaryContainer = functional[0],
                secondaryContainerPressed = functional[1],
                onSecondaryContainer = functional[2],
                outline = c(R.color.lychee_m3_outline),
                outlineVariant = c(R.color.lychee_m3_outline_variant),
            )
        }
    }
}
