package com.llgl.xnl

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF38E8FF),
    onPrimary = Color(0xFF00343C),
    primaryContainer = Color(0xFF1C6F7A),
    onPrimaryContainer = Color(0xFFD2F8FF),
    secondary = Color(0xFFFFB347),
    onSecondary = Color(0xFF3A2400),
    tertiary = Color(0xFF5CFF8A),
    background = Color(0xFF060A0F),
    onBackground = Color(0xFFE6F1FF),
    surface = Color(0xFF0C141D),
    onSurface = Color(0xFFE6F1FF),
    surfaceVariant = Color(0xFF13202C),
    onSurfaceVariant = Color(0xFF9FB3C8),
    outline = Color(0xFF2E4254),
)

@Composable
fun XnlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
