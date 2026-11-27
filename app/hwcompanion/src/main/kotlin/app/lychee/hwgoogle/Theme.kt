package app.lychee.hwgoogle

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Lychee's own palette (its Settings' Material 3 colours, the same on every phone), so the
// companion looks like part of Lychee.
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

/** Material 3 in Lychee's colours, light or dark as the phone is. */
@Composable
fun CompanionTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
