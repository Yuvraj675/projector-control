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
    success = Color(0xFF4ADE80), warning = Color(0xFFFFB84D),
    bgTop = Color(0xFF0A0F1E), bgBottom = Color(0xFF111A38), glow = Color(0x3335E0C8),
)
private val LightApp = AppColors(
    success = Color(0xFF16A34A), warning = Color(0xFFC27800),
    bgTop = Color(0xFFF4F6FD), bgBottom = Color(0xFFE6ECFA), glow = Color(0x2235E0C8),
)

val LocalAppColors = staticCompositionLocalOf { DarkApp }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF35E0C8), onPrimary = Color(0xFF00241F),
    primaryContainer = Color(0xFF0F4D45), onPrimaryContainer = Color(0xFFB7F5EC),
    secondary = Color(0xFF8E9BFF), onSecondary = Color(0xFF0E1550),
    tertiary = Color(0xFFFFB84D), onTertiary = Color(0xFF3A2400),
    background = Color(0xFF0A0F1E), onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF121A33), onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Color(0xFF1B2547), onSurfaceVariant = Color(0xFFA9B4DA),
    outline = Color(0xFF38426B), outlineVariant = Color(0xFF263056),
    error = Color(0xFFFF6B6B), onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF4A1B22), onErrorContainer = Color(0xFFFFD6D6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B9C8A), onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F3EC), onPrimaryContainer = Color(0xFF00403A),
    secondary = Color(0xFF4458D6), onSecondary = Color.White,
    tertiary = Color(0xFFC27800), onTertiary = Color.White,
    background = Color(0xFFF4F6FD), onBackground = Color(0xFF10152B),
    surface = Color.White, onSurface = Color(0xFF10152B),
    surfaceVariant = Color(0xFFE3E8F8), onSurfaceVariant = Color(0xFF4B5578),
    outline = Color(0xFFB8C0DE), outlineVariant = Color(0xFFD5DBEF),
    error = Color(0xFFD64545), onError = Color.White,
    errorContainer = Color(0xFFFFE0E0), onErrorContainer = Color(0xFF5A0F0F),
)

private val base = FontFamily.SansSerif

val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun ProjectorConnectTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
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
