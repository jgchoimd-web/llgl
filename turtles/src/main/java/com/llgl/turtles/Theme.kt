package com.llgl.turtles

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF8CDBB0),
    onPrimary = Color(0xFF0B2E1F),
    primaryContainer = Color(0xFF2E7A55),
    onPrimaryContainer = Color(0xFFD6F5E4),
    secondary = Color(0xFFFFF4C2),
    onSecondary = Color(0xFF3A2E00),
    tertiary = Color(0xFFA6E2F5),
    background = Color(0xFF0B1030),
    onBackground = Color(0xFFEEF0FF),
    surface = Color(0xFF141B45),
    onSurface = Color(0xFFEEF0FF),
    surfaceVariant = Color(0xFF1F2A5E),
    onSurfaceVariant = Color(0xFFB9C0E6),
    outline = Color(0xFF4A568F),
)

@Composable
fun TurtlesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
