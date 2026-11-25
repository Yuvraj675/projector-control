package dev.keystone.auto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours that Material3 has no slot for. */
data class AppColors(
    val success: Color,
    val warning: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val glow: Color,
)

private val DarkApp = AppColors(
    success = Color(0xFF5BE08C), warning = Color(0xFFFFB84D),
    bgTop = Color(0xFF0B0B0D), bgBottom = Color(0xFF0B0B0D), glow = Color(0x33FF6B3D),
)
private val LightApp = AppColors(
    success = Color(0xFF16A34A), warning = Color(0xFFC27800),
    bgTop = Color(0xFFF5F4F1), bgBottom = Color(0xFFF5F4F1), glow = Color(0x22E8481C),
)

val LocalAppColors = staticCompositionLocalOf { DarkApp }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF6B3D), onPrimary = Color(0xFF1C0A03),
    primaryContainer = Color(0xFF2B1810), onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = Color(0xFF9AA4FF), onSecondary = Color(0xFF0E1550),
    tertiary = Color(0xFFFFB84D), onTertiary = Color(0xFF3A2400),
    background = Color(0xFF0B0B0D), onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF151517), onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1F1F23), onSurfaceVariant = Color(0xFF8E8E96),
    outline = Color(0xFF3A3A41), outlineVariant = Color(0xFF26262B),
    error = Color(0xFFFF6B6B), onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF3A1A1E), onErrorContainer = Color(0xFFFFD6D6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE8481C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D9), onPrimaryContainer = Color(0xFF4A1405),
    secondary = Color(0xFF4458D6), onSecondary = Color.White,
    tertiary = Color(0xFFC27800), onTertiary = Color.White,
    background = Color(0xFFF5F4F1), onBackground = Color(0xFF111114),
    surface = Color.White, onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFECEAE5), onSurfaceVariant = Color(0xFF6A6A72),
    outline = Color(0xFFC4C4C8), outlineVariant = Color(0xFFE4E2DC),
    error = Color(0xFFD64545), onError = Color.White,
    errorContainer = Color(0xFFFFE0E0), onErrorContainer = Color(0xFF5A0F0F),
)

private val base = FontFamily.SansSerif

val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp),
    headlineMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

@Composable
fun ProjectorControlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides (if (dark) DarkApp else LightApp)) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val MaterialTheme.appColors: AppColors
    @Composable get() = LocalAppColors.current
