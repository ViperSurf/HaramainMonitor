package com.haramain.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.haramain.monitor.data.ThemeMode
import com.haramain.monitor.data.ThemePalette

/**
 * Centralized 3-Accent Material 3 Token Architecture supporting two palettes:
 *  1. Studio Orange (Default): Pure #000000 OLED black / warm espresso-brown surfaces +
 *     Studio Orange (Primary) + Warm Amber-Bronze (Secondary) + Silver-Slate (Tertiary).
 *  2. Haramain Emerald: Pure #000000 OLED black surfaces +
 *     Haramain Emerald (Primary) + Warm Brass (Secondary) + Teal-Cyan (Tertiary).
 */
private val StudioDark = darkColorScheme(
    primary = Color(0xFFFF7A2F),
    onPrimary = Color(0xFF2B1000),
    primaryContainer = Color(0xFF4D2108),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFE0A96D),
    onSecondary = Color(0xFF281805),
    secondaryContainer = Color(0xFF3E2912),
    onSecondaryContainer = Color(0xFFFDE3C2),
    tertiary = Color(0xFFB8C0C8),
    onTertiary = Color(0xFF1A2026),
    tertiaryContainer = Color(0xFF262B30),
    onTertiaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F2EF),
    surface = Color(0xFF13100E),
    onSurface = Color(0xFFF5F2EF),
    surfaceVariant = Color(0xFF211B17),
    onSurfaceVariant = Color(0xFFA89F99),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0C0B),
    surfaceContainer = Color(0xFF171310),
    surfaceContainerHigh = Color(0xFF201A16),
    surfaceContainerHighest = Color(0xFF2A231E),
    outline = Color(0xFF3A312B),
    outlineVariant = Color(0xFF27211C),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF2B0704),
    errorContainer = Color(0xFF42100C),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val StudioLight = lightColorScheme(
    primary = Color(0xFFD9530F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0CE),
    onPrimaryContainer = Color(0xFF431600),
    secondary = Color(0xFF8C5A2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCE8D2),
    onSecondaryContainer = Color(0xFF361D05),
    tertiary = Color(0xFF52606D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4E7EB),
    onTertiaryContainer = Color(0xFF1F2933),
    background = Color(0xFFFAF8F6),
    onBackground = Color(0xFF1D1815),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1815),
    surfaceVariant = Color(0xFFEDE7E2),
    onSurfaceVariant = Color(0xFF5E544E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2EE),
    surfaceContainer = Color(0xFFF1ECE7),
    surfaceContainerHigh = Color(0xFFEAE3DD),
    surfaceContainerHighest = Color(0xFFE2DAD3),
    outline = Color(0xFFC4B9B0),
    outlineVariant = Color(0xFFDDD4CC),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val HaramainDark = darkColorScheme(
    primary = Color(0xFF3ECF8E),
    onPrimary = Color(0xFF042617),
    primaryContainer = Color(0xFF0E3D2A),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFD9A441),
    onSecondary = Color(0xFF261A04),
    secondaryContainer = Color(0xFF3A2B0D),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFF56C2C9),
    onTertiary = Color(0xFF042426),
    tertiaryContainer = Color(0xFF0D383B),
    onTertiaryContainer = Color(0xFFB2F5EA),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDF2EE),
    surface = Color(0xFF0C100E),
    onSurface = Color(0xFFEDF2EE),
    surfaceVariant = Color(0xFF171E1B),
    onSurfaceVariant = Color(0xFFA3B0A8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0E0C),
    surfaceContainer = Color(0xFF101513),
    surfaceContainerHigh = Color(0xFF171E1B),
    surfaceContainerHighest = Color(0xFF1F2723),
    outline = Color(0xFF2B3530),
    outlineVariant = Color(0xFF1E2622),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF2B0704),
    errorContainer = Color(0xFF42100C),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val HaramainLight = lightColorScheme(
    primary = Color(0xFF0B7A4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF053822),
    secondary = Color(0xFF9A7414),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF3D2C04),
    tertiary = Color(0xFF006B72),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF122019),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF122019),
    surfaceVariant = Color(0xFFE4ECE7),
    onSurfaceVariant = Color(0xFF4C5A51),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFECF2EE),
    surfaceContainerHigh = Color(0xFFE4ECE7),
    surfaceContainerHighest = Color(0xFFDCE5DF),
    outline = Color(0xFFB8C4BC),
    outlineVariant = Color(0xFFD6E0DA),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val HaramainTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.SansSerif),
)

@Composable
fun HaramainMonitorTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.STUDIO,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = when (palette) {
        ThemePalette.STUDIO -> if (dark) StudioDark else StudioLight
        ThemePalette.HARAMAIN -> if (dark) HaramainDark else HaramainLight
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = HaramainTypography,
        content = content,
    )
}
