package com.llgl.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6F5C),
    secondary = Color(0xFF4F6F64),
    tertiary = Color(0xFF3E6B8E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6C0),
    secondary = Color(0xFFB4CCC2),
    tertiary = Color(0xFFA7C8E9),
)

@Composable
fun LlglTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic (wallpaper-based) colors are available on Android 12+.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
