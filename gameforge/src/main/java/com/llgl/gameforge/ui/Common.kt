package com.llgl.gameforge.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Keeps the screen awake while the composable is on screen (long generations, games). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Immersive mode while the composable is on screen; bars come back on dispose. */
@Composable
fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "?"
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}초" else "${s / 60}분 ${s % 60}초"
}

fun formatDate(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

/** A dark monospace box; with [follow] it keeps the newest line in view as text streams in. */
@Composable
fun CodeBox(text: String, modifier: Modifier = Modifier, follow: Boolean = false) {
    val scroll = rememberScrollState()
    if (follow) {
        LaunchedEffect(text.length) {
            withFrameNanos { }
            scroll.scrollTo(scroll.maxValue)
        }
    }
    Surface(modifier = modifier, color = Color(0xFF0B0D12), shape = MaterialTheme.shapes.medium) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(10.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Color(0xFFCFD6E4),
        )
    }
}
