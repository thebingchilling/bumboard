@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package app.lychee.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.lychee.fonts.LycheeFonts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback for Android 7–11 (no Material You): tonal palette from the icon's teal (#0F6E5F).
private val Light = lightColorScheme(
    primary = Color(0xFF006A5D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2DE),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E0),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF436278),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8E6FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = Color(0xFFF5FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE4EAE7),
    surfaceContainerHighest = Color(0xFFDEE4E1),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF82D5C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF9EF2DE),
    secondary = Color(0xFFB1CCC4),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = Color(0xFFABCAE4),
    onTertiary = Color(0xFF113348),
    tertiaryContainer = Color(0xFF2B4A5F),
    onTertiaryContainer = Color(0xFFC8E6FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF09100E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
)

@Composable
fun lycheeColorScheme(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) Dark else Light
    }
}

/** The app's font choice (Settings > Fonts), as state, so changing it restyles the settings at once. */
object AppFont {
    var system by mutableStateOf<Boolean?>(null)
}

/** Google Sans Flex in the weights the settings use; null for the phone's font, or when the
 *  font is not in the APK. */
@Composable
private fun googleSansFlex(): FontFamily? {
    val context = LocalContext.current
    val system = AppFont.system ?: remember { LycheeFonts.uiIsSystem(context) }
    return remember(system) {
        if (system || !LycheeFonts.hasUiFont(context)) return@remember null
        val assets = context.applicationContext.assets
        FontFamily(listOf(400, 500, 600, 700).map { w ->
            Font(
                LycheeFonts.UI_FONT, assets, FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        })
    }
}

/**
 * Material 3's type scale in [family], a step smaller for the settings' lists (the stock body
 * text made every row oversized).
 */
private fun typography(family: FontFamily?): Typography {
    val stock = Typography()
    val base = stock.copy(
        bodyLarge = stock.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyMedium = stock.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = stock.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = stock.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = stock.headlineMedium.copy(fontSize = 24.sp),
    )
    if (family == null) return base
    fun TextStyle.f() = copy(fontFamily = family)
    return base.copy(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
}

@Composable
fun LycheeTheme(content: @Composable () -> Unit) {
    val family = googleSansFlex()
    MaterialExpressiveTheme(
        colorScheme = lycheeColorScheme(isSystemInDarkTheme()),
        motionScheme = MotionScheme.expressive(),
        typography = remember(family) { typography(family) },
        content = content,
    )
}
