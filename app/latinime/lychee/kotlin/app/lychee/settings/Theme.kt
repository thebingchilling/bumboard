@file:OptIn(ExperimentalTextApi::class)

package app.lychee.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

// Lychee's own palette, the same on every phone (not the wallpaper's Material You colours, whose
// dark blues sat badly on the pages): a lychee rose from the icon, with a leaf green beside it,
// as Material 3 tonal roles.
private val Light = lightColorScheme(
    primary = Color(0xFFA53A50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = Color(0xFF40000F),
    secondary = Color(0xFF765659),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DC),
    onSecondaryContainer = Color(0xFF2C1518),
    tertiary = Color(0xFF3D6838),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEF0B1),
    onTertiaryContainer = Color(0xFF002203),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF4DDDF),
    onSurfaceVariant = Color(0xFF524345),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F1),
    surfaceContainer = Color(0xFFFBEAEB),
    surfaceContainerHigh = Color(0xFFF5E4E5),
    surfaceContainerHighest = Color(0xFFEFDEDF),
    outline = Color(0xFF847374),
    outlineVariant = Color(0xFFD7C1C3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB2BC),
    onPrimary = Color(0xFF66001F),
    primaryContainer = Color(0xFF85233A),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFFE5BDC0),
    onSecondary = Color(0xFF43292C),
    secondaryContainer = Color(0xFF5C3F42),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = Color(0xFFA3D497),
    onTertiary = Color(0xFF0C390D),
    tertiaryContainer = Color(0xFF255021),
    onTertiaryContainer = Color(0xFFBEF0B1),
    background = Color(0xFF1A1112),
    onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF1A1112),
    onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD7C1C3),
    surfaceContainerLowest = Color(0xFF140C0D),
    surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF271D1E),
    surfaceContainerHigh = Color(0xFF322728),
    surfaceContainerHighest = Color(0xFF3D3233),
    outline = Color(0xFF9F8C8E),
    outlineVariant = Color(0xFF524345),
)

/** The settings' colours: Lychee's rose palette (Material 3's roles), the same on every page and phone. */
@Composable
fun lycheeColorScheme(dark: Boolean): ColorScheme = if (dark) Dark else Light

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

/** Material 3's type scale, in [family] (Google Sans Flex, or the phone's font when null). */
private fun typography(family: FontFamily?): Typography {
    val base = Typography()
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
    MaterialTheme(
        colorScheme = lycheeColorScheme(isSystemInDarkTheme()),
        typography = remember(family) { typography(family) },
        content = content,
    )
}
