package com.llgl.vibe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Pink = Color(0xFFFF7AB6)
val Mint = Color(0xFF7AF0D8)
val Amber = Color(0xFFFFC94A)
val Ink = Color(0xFF120E1C)

private val Scheme = darkColorScheme(
    primary = Pink,
    onPrimary = Color(0xFF3A0A22),
    primaryContainer = Color(0xFF4A1B35),
    onPrimaryContainer = Color(0xFFFFD6E8),
    secondary = Mint,
    onSecondary = Color(0xFF003A30),
    secondaryContainer = Color(0xFF1C4A42),
    onSecondaryContainer = Color(0xFFC6FFF3),
    tertiary = Amber,
    background = Ink,
    onBackground = Color(0xFFF2EEFF),
    surface = Color(0xFF1B1529),
    onSurface = Color(0xFFF2EEFF),
    surfaceVariant = Color(0xFF2A2140),
    onSurfaceVariant = Color(0xFFBFB6D6),
    outline = Color(0xFF55496E),
    error = Color(0xFFFF8A80),
)

@Composable
fun VibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
