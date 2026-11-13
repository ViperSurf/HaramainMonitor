package com.haramain.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark-mode-first palette: deep emerald + brass, echoing the Haramain preset.
private val HaramainColors = darkColorScheme(
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

@Composable
fun HaramainMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HaramainColors, content = content)
}
