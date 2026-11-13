package com.haramain.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.haramain.monitor.data.ThemeMode

// Deep emerald + brass, echoing the Haramain preset — dark and light variants.
private val HaramainDark = darkColorScheme(
    primary = Color(0xFF3ECF8E),
    onPrimary = Color(0xFF06281A),
    secondary = Color(0xFFD9A441),
    onSecondary = Color(0xFF2A1E05),
    background = Color(0xFF0B0F0D),
    onBackground = Color(0xFFE8EDE9),
    surface = Color(0xFF141A17),
    onSurface = Color(0xFFE8EDE9),
    surfaceVariant = Color(0xFF1E2622),
    onSurfaceVariant = Color(0xFFA7B3AB),
    error = Color(0xFFFF6B5E),
)

private val HaramainLight = lightColorScheme(
    primary = Color(0xFF0B7A4B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF9A7414),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF122019),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF122019),
    surfaceVariant = Color(0xFFE2EAE3),
    onSurfaceVariant = Color(0xFF4C5A51),
    error = Color(0xFFC62828),
)

@Composable
fun HaramainMonitorTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) HaramainDark else HaramainLight, content = content)
}
