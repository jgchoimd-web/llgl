package com.llgl.xnl

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.llgl.xnl.term.Commands
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            XnlTheme {
                SetupScreen(prefs, onSetWallpaper = { openWallpaperPicker() })
            }
        }
    }

    /** Opens the system's live wallpaper preview for this wallpaper, or the general chooser as a fallback. */
    private fun openWallpaperPicker() {
        val component = ComponentName(this, XnlWallpaper::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.wallpaper_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}

private val ACCENT_COLORS = listOf(Color(0xFF38E8FF), Color(0xFFFFB347), Color(0xFF5CFF8A), Color(0xFFFF5FD2), Color(0xFFE6F1FF))
private val ACCENT_NAMES = listOf("시안", "앰버", "그린", "마젠타", "화이트")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(prefs: Prefs, onSetWallpaper: () -> Unit) {
    var accent by remember { mutableIntStateOf(prefs.accent) }
    var columns by remember { mutableIntStateOf(prefs.columns) }
    var speed by remember { mutableFloatStateOf(prefs.speed) }
    var shell by remember { mutableStateOf(prefs.shell) }
    var lockPreview by remember { mutableStateOf(true) }
    var broken by remember { mutableIntStateOf(prefs.broken.size) }

    Scaffold(topBar = { TopAppBar(title = { Text("XNL 배경화면") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AndroidView(
                factory = { TerminalView(it) },
                update = { it.configure(lockPreview) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Text(
                "터미널 하나가 쭉 내려갑니다. 보이는 것은 전부 이 폰의 진짜 것입니다: 명령은 폰의 sh(/system/bin/sh)에서 실제로 실행되고 출력은 그대로 찍힙니다(uname, uptime, free, df, /proc, getprop, ps …). xnl로 시작하는 명령은 앱이 Android API로 읽은 값(배터리·메모리·디스플레이·센서·카메라·네트워크 …)을 보여 줍니다. 꾸며 낸 줄은 없습니다. 톡 치면 다음 명령이 바로 실행됩니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSetWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("배경화면으로 설정", style = MaterialTheme.typography.titleMedium) }
            Text(
                "Android 14 이상이면 미리보기의 ‘배경화면 설정’에서 잠금화면을 고를 수 있습니다. 잠금화면에 놓이면 시계 자리의 오래된 줄이 흐려집니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("글자색 · ${ACCENT_NAMES[accent]}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((i, c) in ACCENT_COLORS.withIndex()) {
                            val selected = i == accent
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x55FFFFFF), CircleShape)
                                    .clickable {
                                        accent = i
                                        prefs.accent = i
                                    },
                            )
                        }
                    }
                    Text("글자 크기 · 가로 ${columns}칸", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = columns.toFloat(),
                        onValueChange = { columns = it.roundToInt() },
                        onValueChangeFinished = { prefs.columns = columns },
                        valueRange = Prefs.MIN_COLUMNS.toFloat()..Prefs.MAX_COLUMNS.toFloat(),
                        steps = Prefs.MAX_COLUMNS - Prefs.MIN_COLUMNS - 1,
                    )
                    Text("속도 ${(speed * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        onValueChangeFinished = { prefs.speed = speed },
                        valueRange = 0.5f..3f,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("셸 명령 실행", style = MaterialTheme.typography.bodyMedium)
                            Text("폰의 sh로 uname·uptime·free·df 같은 실제 명령을 돌려 그 출력을 그대로 보여 줍니다. 끄면 앱 API로 읽는 xnl 명령만 돕니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = shell, onCheckedChange = { shell = it; prefs.shell = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("잠금화면처럼 미리보기", style = MaterialTheme.typography.bodyMedium)
                            Text("시계 자리(위쪽 40%)의 줄이 흐려진 모습을 봅니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lockPreview, onCheckedChange = { lockPreview = it })
                    }
                    if (broken > 0) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("이 폰에서 실패해 뺀 명령 ${broken}개", style = MaterialTheme.typography.bodyMedium)
                                Text("권한이 없거나 파일이 없어 한 번 실패한 명령은 다시 띄우지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { prefs.resetBroken(); broken = 0 }) { Text("다시 시도") }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("도는 명령", style = MaterialTheme.typography.titleSmall)
                    Text("셸 ${Commands.SHELL.size}개 · xnl ${Commands.BUILTIN.size}개. 자주 바뀌는 값(uptime, 배터리, 부하, 클럭)은 자주, 안 바뀌는 값(커널, CPU, 빌드)은 가끔.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (Commands.SHELL + Commands.BUILTIN).joinToString("\n") { "$ " + it.text },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
