package com.example.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

data class AqroExtraColors(
    val pale: Color,
    val line: Color,
    val muted: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val warningContainer: Color,
    val isDark: Boolean,
)

val LocalAqroColors = staticCompositionLocalOf {
    AqroExtraColors(
        pale = LightPale,
        line = LightLine,
        muted = LightMuted,
        heroStart = ForestDarker,
        heroEnd = EmeraldLight,
        dangerContainer = DangerSoftLight,
        onDangerContainer = DangerDarkRed,
        warningContainer = WarningSoftLight,
        isDark = false,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPale,
    onPrimaryContainer = EmeraldPrimary,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3E4),
    onSecondaryContainer = ForestDeep,
    tertiary = PhosphorusAmber,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightPale,
    onSurfaceVariant = LightMuted,
    outline = LightLine,
    outlineVariant = LightLine,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerSoftLight,
    onErrorContainer = DangerDarkRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color.White,
    primaryContainer = DarkPale,
    onPrimaryContainer = EmeraldBrightLight,
    secondary = EmeraldBrightLight,
    onSecondary = Color(0xFF092412),
    secondaryContainer = Color(0xFF1D3B26),
    onSecondaryContainer = Color(0xFFB9F2C9),
    tertiary = PhosphorusAmber,
    onTertiary = Color(0xFF281800),
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkPale,
    onSurfaceVariant = DarkMuted,
    outline = DarkLine,
    outlineVariant = DarkLine,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerSoftDark,
    onErrorContainer = Color(0xFFFFB4AB),
)

private val AqroShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

enum class ThemePreference(val code: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromCode(code: String?): ThemePreference =
            entries.firstOrNull { it.code == code } ?: LIGHT
    }
}

@Composable
fun AqroMeslehetTheme(
    themePreference: ThemePreference = ThemePreference.LIGHT,
    content: @Composable () -> Unit,
) {
    val useDark = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity
            activity?.enableEdgeToEdge(
                statusBarStyle = if (useDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                },
                navigationBarStyle = if (useDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                },
            )
        }
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val extraColors = if (useDark) {
        AqroExtraColors(
            pale = DarkPale,
            line = DarkLine,
            muted = DarkMuted,
            heroStart = Color(0xFF134625),
            heroEnd = Color(0xFF237B42),
            dangerContainer = DangerSoftDark,
            onDangerContainer = Color(0xFFFFB4AB),
            warningContainer = WarningSoftDark,
            isDark = true,
        )
    } else {
        AqroExtraColors(
            pale = LightPale,
            line = LightLine,
            muted = LightMuted,
            heroStart = ForestDarker,
            heroEnd = EmeraldLight,
            dangerContainer = DangerSoftLight,
            onDangerContainer = DangerDarkRed,
            warningContainer = WarningSoftLight,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalAqroColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AqroTypography,
            shapes = AqroShapes,
            content = content,
        )
    }
}
