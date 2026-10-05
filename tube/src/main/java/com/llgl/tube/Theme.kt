package com.llgl.tube

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFFF5A4E),
    onPrimary = Color(0xFF3B0A06),
    primaryContainer = Color(0xFF7A2A22),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFB8C4D4),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFECECF0),
    surface = Color(0xFF151518),
    onSurface = Color(0xFFECECF0),
    surfaceVariant = Color(0xFF202026),
    onSurfaceVariant = Color(0xFFA9AFBA),
    outline = Color(0xFF3B3B44),
)

@Composable
fun TubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
