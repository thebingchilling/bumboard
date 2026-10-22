package app.bumboard.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Material 3 colour roles for BumBoard's View-based keyboard UI (dictionary popup, emoji
 * panel): the wallpaper-based system palette on Android 12+, BumBoard's teal palette before.
 * Compose screens use BumTheme instead.
 */
data class M3Colors(
    val surface: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val outline: Int,
) {
    companion object {
        fun of(ctx: Context): M3Colors {
            val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fun c(id: Int) = ctx.getColor(id)
                return if (dark) M3Colors(
                    surface = c(android.R.color.system_neutral1_900),
                    surfaceContainer = c(android.R.color.system_neutral1_800),
                    surfaceContainerHigh = c(android.R.color.system_neutral2_800),
                    onSurface = c(android.R.color.system_neutral1_100),
                    onSurfaceVariant = c(android.R.color.system_neutral2_200),
                    primary = c(android.R.color.system_accent1_200),
                    onPrimary = c(android.R.color.system_accent1_800),
                    secondaryContainer = c(android.R.color.system_accent2_700),
                    onSecondaryContainer = c(android.R.color.system_accent2_100),
                    outline = c(android.R.color.system_neutral2_400),
                ) else M3Colors(
                    surface = c(android.R.color.system_neutral1_10),
                    surfaceContainer = c(android.R.color.system_neutral1_50),
                    surfaceContainerHigh = c(android.R.color.system_neutral2_100),
                    onSurface = c(android.R.color.system_neutral1_900),
                    onSurfaceVariant = c(android.R.color.system_neutral2_700),
                    primary = c(android.R.color.system_accent1_600),
                    onPrimary = c(android.R.color.system_accent1_0),
                    secondaryContainer = c(android.R.color.system_accent2_100),
                    onSecondaryContainer = c(android.R.color.system_accent2_900),
                    outline = c(android.R.color.system_neutral2_500),
                )
            }
            return if (dark) M3Colors(
                surface = 0xFF0E1513.toInt(), surfaceContainer = 0xFF1B211F.toInt(),
                surfaceContainerHigh = 0xFF252B2A.toInt(), onSurface = 0xFFDEE4E1.toInt(),
                onSurfaceVariant = 0xFFBEC9C5.toInt(), primary = 0xFF82D5C3.toInt(),
                onPrimary = 0xFF00382F.toInt(), secondaryContainer = 0xFF334B45.toInt(),
                onSecondaryContainer = 0xFFCCE8E0.toInt(), outline = 0xFF899390.toInt(),
            ) else M3Colors(
                surface = 0xFFF5FBF8.toInt(), surfaceContainer = 0xFFE9EFEC.toInt(),
                surfaceContainerHigh = 0xFFE4EAE7.toInt(), onSurface = 0xFF171D1B.toInt(),
                onSurfaceVariant = 0xFF3F4946.toInt(), primary = 0xFF006A5D.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(), secondaryContainer = 0xFFCCE8E0.toInt(),
                onSecondaryContainer = 0xFF06201B.toInt(), outline = 0xFF6F7976.toInt(),
            )
        }
    }
}
