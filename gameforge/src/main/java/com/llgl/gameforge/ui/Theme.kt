package com.llgl.gameforge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFB9A3FF),
    onPrimary = Color(0xFF1E1240),
    primaryContainer = Color(0xFF3B2B73),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFF6FE3C4),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF1C4A3E),
    onSecondaryContainer = Color(0xFFBDF4E2),
    tertiary = Color(0xFFFFC46B),
    onTertiary = Color(0xFF3F2A00),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFE8E9F1),
    surface = Color(0xFF14171F),
    onSurface = Color(0xFFE8E9F1),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFB9BDCA),
    outline = Color(0xFF4A4F5E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3C0000),
)

@Composable
fun GameForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
