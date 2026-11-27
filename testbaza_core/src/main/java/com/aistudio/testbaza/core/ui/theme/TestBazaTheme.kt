package com.aistudio.testbaza.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.testbaza.core.R
import com.aistudio.testbaza.core.domain.TestBazaThemeMode

val OutfitFamily = FontFamily(
    Font(R.font.outfit, FontWeight.SemiBold),
    Font(R.font.outfit, FontWeight.Bold),
)

val PlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

@Immutable
data class VipColorTokens(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gold: Color,
    val goldSoft: Color,
    val goldBorder: Color,
    val emerald: Color,
    val emeraldSoft: Color,
    val emeraldBorder: Color,
    val rose: Color,
    val roseSoft: Color,
    val roseBorder: Color,
    val cyan: Color,
    val cyanSoft: Color,
)

private val VipDarkTokens = VipColorTokens(
    isDark = true,
    background = Color(0xFF070B12),
    surface = Color(0xFF101826),
    surfaceElevated = Color(0xFF162133),
    border = Color(0xFF1F2D42),
    borderStrong = Color(0xFF2D405C),
    textPrimary = Color(0xFFF3F6FA),
    textSecondary = Color(0xFF97A6BA),
    gold = Color(0xFFE5C158),
    goldSoft = Color(0x26E5C158),
    goldBorder = Color(0x66E5C158),
    emerald = Color(0xFF10B981),
    emeraldSoft = Color(0x2610B981),
    emeraldBorder = Color(0x8010B981),
    rose = Color(0xFFF43F5E),
    roseSoft = Color(0x26F43F5E),
    roseBorder = Color(0x80F43F5E),
    cyan = Color(0xFF38BDF8),
    cyanSoft = Color(0x2638BDF8),
)

private val VipLightTokens = VipColorTokens(
    isDark = false,
    background = Color(0xFFF6F4EE),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDE9DF),
    border = Color(0xFFDFD8C8),
    borderStrong = Color(0xFFC5BAA3),
    textPrimary = Color(0xFF101928),
    textSecondary = Color(0xFF566376),
    gold = Color(0xFFB5820E),
    goldSoft = Color(0x1FB5820E),
    goldBorder = Color(0x66B5820E),
    emerald = Color(0xFF059669),
    emeraldSoft = Color(0x1F059669),
    emeraldBorder = Color(0x73059669),
    rose = Color(0xFFE11D48),
    roseSoft = Color(0x1FE11D48),
    roseBorder = Color(0x73E11D48),
    cyan = Color(0xFF0284C7),
    cyanSoft = Color(0x1F0284C7),
)

val LocalVipColors = staticCompositionLocalOf { VipDarkTokens }

private val VipDarkMaterialScheme = darkColorScheme(
    primary = VipDarkTokens.emerald,
    onPrimary = Color(0xFF032318),
    secondary = VipDarkTokens.gold,
    onSecondary = Color(0xFF211700),
    tertiary = VipDarkTokens.cyan,
    background = VipDarkTokens.background,
    onBackground = VipDarkTokens.textPrimary,
    surface = VipDarkTokens.surface,
    onSurface = VipDarkTokens.textPrimary,
    surfaceVariant = VipDarkTokens.surfaceElevated,
    onSurfaceVariant = VipDarkTokens.textSecondary,
    outline = VipDarkTokens.border,
    error = VipDarkTokens.rose,
    onError = Color.White,
)

private val VipLightMaterialScheme = lightColorScheme(
    primary = VipLightTokens.emerald,
    onPrimary = Color.White,
    secondary = VipLightTokens.gold,
    onSecondary = Color.White,
    tertiary = VipLightTokens.cyan,
    background = VipLightTokens.background,
    onBackground = VipLightTokens.textPrimary,
    surface = VipLightTokens.surface,
    onSurface = VipLightTokens.textPrimary,
    surfaceVariant = VipLightTokens.surfaceElevated,
    onSurfaceVariant = VipLightTokens.textSecondary,
    outline = VipLightTokens.border,
    error = VipLightTokens.rose,
    onError = Color.White,
)

private val TestBazaTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val TestBazaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TestBazaTheme(
    themeMode: TestBazaThemeMode,
    content: @Composable () -> Unit,
) {
    val tokens = if (themeMode == TestBazaThemeMode.DARK) VipDarkTokens else VipLightTokens
    val colorScheme = if (themeMode == TestBazaThemeMode.DARK) VipDarkMaterialScheme else VipLightMaterialScheme

    CompositionLocalProvider(LocalVipColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TestBazaTypography,
            shapes = TestBazaShapes,
            content = content,
        )
    }
}
