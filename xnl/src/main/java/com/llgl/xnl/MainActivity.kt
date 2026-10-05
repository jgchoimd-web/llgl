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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    var wordmark by remember { mutableStateOf(prefs.wordmark) }
    var logSpeed by remember { mutableFloatStateOf(prefs.logSpeed) }
    var tilt by remember { mutableStateOf(prefs.tilt) }
    var lockPreview by remember { mutableStateOf(true) }

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
                factory = { KernelView(it) },
                update = { it.configure(accent, wordmark, logSpeed, tilt, lockPreview) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Text(
                "XNL 커널의 안쪽을 배경으로 둡니다. 부팅 로그가 흐르고, 코어 부하가 숨 쉬고, 메모리 페이지가 켜졌다 꺼지고, 인터럽트가 코어로 달립니다. 아래 상태 줄의 업타임·메모리·배터리·호스트 커널은 이 폰의 진짜 값입니다. 톡 치면 그 자리에서 인터럽트가 일어나고, 코어 타일을 치면 그 코어가 올라갑니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSetWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("배경화면으로 설정", style = MaterialTheme.typography.titleMedium) }
            Text(
                "Android 14 이상이면 미리보기의 ‘배경화면 설정’에서 잠금화면을 고를 수 있습니다. 잠금화면에 놓이면 코어·워드마크가 시계 아래로 내려갑니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("강조색 · ${ACCENT_NAMES[accent]}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((i, c) in ACCENT_COLORS.withIndex()) {
                            val selected = i == accent
                            androidx.compose.foundation.layout.Box(
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("임시 워드마크", style = MaterialTheme.typography.bodyMedium)
                            Text("로고가 아직 없어 막대로 짠 XNL 글자를 둡니다. 로고가 정해지면 이 자리에 넣습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wordmark, onCheckedChange = { wordmark = it; prefs.wordmark = it })
                    }
                    Text("로그 속도 ${(logSpeed * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = logSpeed,
                        onValueChange = { logSpeed = it },
                        onValueChangeFinished = { prefs.logSpeed = logSpeed },
                        valueRange = 0.3f..3f,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("기울임 시차", style = MaterialTheme.typography.bodyMedium)
                            Text("폰을 기울이면 페이지 맵이 살짝 흐릅니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = tilt, onCheckedChange = { tilt = it; prefs.tilt = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("잠금화면처럼 미리보기", style = MaterialTheme.typography.bodyMedium)
                            Text("시계 자리(위쪽 40%)를 비워 둔 모습을 봅니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lockPreview, onCheckedChange = { lockPreview = it })
                    }
                }
            }
        }
    }
}
