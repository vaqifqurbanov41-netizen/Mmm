package com.aistudio.texnonezaret.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aistudio.texnonezaret.data.ThemeMode

@Immutable
data class TexnoColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val brand: Color,
    val brandInk: Color,
    val plateBg: Color,
    val plateLine: Color,
    val okText: Color,
    val okBg: Color,
    val warnText: Color,
    val warnBg: Color,
    val badText: Color,
    val badBg: Color,
)

private val LightTexnoColors = TexnoColors(
    bg = Color(0xFFEEF0E6),
    surface = Color(0xFFFBFBF6),
    ink = Color(0xFF1D2A1C),
    muted = Color(0xFF5B6A56),
    line = Color(0xFFC9CFBA),
    brand = Color(0xFF2F5D34),
    brandInk = Color(0xFFFFFFFF),
    plateBg = Color(0xFFFFFFFF),
    plateLine = Color(0xFF1F3B8A),
    okText = Color(0xFF1D6B34),
    okBg = Color(0xFFDCEFD9),
    warnText = Color(0xFF7A5200),
    warnBg = Color(0xFFFBE9B4),
    badText = Color(0xFFA3201A),
    badBg = Color(0xFFF8D9D5),
)

private val DarkTexnoColors = TexnoColors(
    bg = Color(0xFF151B14),
    surface = Color(0xFF1E271D),
    ink = Color(0xFFE8EDDF),
    muted = Color(0xFF9FAE98),
    line = Color(0xFF364233),
    brand = Color(0xFF7FC287),
    brandInk = Color(0xFF10200F),
    plateBg = Color(0xFFF4F4EE),
    plateLine = Color(0xFF7D9CF0),
    okText = Color(0xFF8FD69B),
    okBg = Color(0xFF1F3A24),
    warnText = Color(0xFFF1CA6A),
    warnBg = Color(0xFF40320F),
    badText = Color(0xFFF2A49E),
    badBg = Color(0xFF46201D),
)

val LocalTexnoColors = staticCompositionLocalOf { LightTexnoColors }

private val LightColorScheme = lightColorScheme(
    primary = LightTexnoColors.brand,
    onPrimary = LightTexnoColors.brandInk,
    secondary = LightTexnoColors.plateLine,
    onSecondary = Color.White,
    background = LightTexnoColors.bg,
    onBackground = LightTexnoColors.ink,
    surface = LightTexnoColors.surface,
    onSurface = LightTexnoColors.ink,
    surfaceVariant = Color(0xFFE6EAD9),
    onSurfaceVariant = LightTexnoColors.muted,
    outline = LightTexnoColors.line,
    error = LightTexnoColors.badText,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTexnoColors.brand,
    onPrimary = DarkTexnoColors.brandInk,
    secondary = DarkTexnoColors.plateLine,
    onSecondary = Color(0xFF0A1430),
    background = DarkTexnoColors.bg,
    onBackground = DarkTexnoColors.ink,
    surface = DarkTexnoColors.surface,
    onSurface = DarkTexnoColors.ink,
    surfaceVariant = Color(0xFF283326),
    onSurfaceVariant = DarkTexnoColors.muted,
    outline = DarkTexnoColors.line,
    error = DarkTexnoColors.badText,
    onError = Color(0xFF2C0B09),
)

private val TexnoTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun TexnonezaretTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val extra = if (dark) DarkTexnoColors else LightTexnoColors
    val scheme = if (dark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalTexnoColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TexnoTypography,
            content = content,
        )
    }
}
