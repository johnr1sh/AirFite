package dev.johnrish.pocketengineer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand colors. Sky is the single accent; green and red are reserved for status. */
object AirFiteColors {
    val Sky = Color(0xFF5CC8F5)
    val Ink = Color(0xFF0D1117)
}

private val DarkScheme = darkColorScheme(
    primary = AirFiteColors.Sky,
    onPrimary = Color(0xFF00212D),
    primaryContainer = Color(0xFF12323F),
    onPrimaryContainer = Color(0xFFBFE9FA),
    secondary = Color(0xFF7EE787),
    onSecondary = Color(0xFF00210A),
    secondaryContainer = Color(0xFF12323F),
    onSecondaryContainer = Color(0xFFBFE9FA),
    background = AirFiteColors.Ink,
    onBackground = Color(0xFFE6EDF3),
    surface = AirFiteColors.Ink,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF0F141A),
    surfaceContainer = Color(0xFF11161D),
    surfaceContainerHigh = Color(0xFF161B22),
    surfaceContainerHighest = Color(0xFF1C2330),
    outline = Color(0xFF3D444D),
    outlineVariant = Color(0xFF21262D),
    error = Color(0xFFF85149),
    onError = Color(0xFF3C0A08),
    errorContainer = Color(0xFF3D1210),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val base = Typography()

/** Sans for prose; monospace for labels, paths and code so the app reads as an engineering tool. */
private val AirFiteTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace),
    labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
)

/** Tight radii on purpose: this is a tool, not a toy. */
private val AirFiteShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)

/** Dark only for now; the launcher icon, window background and system bars are matched to it. */
@Composable
fun AirFiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AirFiteTypography, shapes = AirFiteShapes, content = content)
}
