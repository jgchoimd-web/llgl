package com.llgl.turtles

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            TurtlesTheme {
                SetupScreen(prefs, onSetWallpaper = { openWallpaperPicker() })
            }
        }
    }

    /** Opens the system's live wallpaper preview for this sky, or the general chooser as a fallback. */
    private fun openWallpaperPicker() {
        val component = ComponentName(this, TurtleWallpaper::class.java)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(prefs: Prefs, onSetWallpaper: () -> Unit) {
    var count by remember { mutableIntStateOf(prefs.count) }
    var speed by remember { mutableFloatStateOf(prefs.speed) }
    var tilt by remember { mutableStateOf(prefs.tilt) }
    var lockPreview by remember { mutableStateOf(true) }

    Scaffold(topBar = { TopAppBar(title = { Text("하늘 거북이") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AndroidView(
                factory = { TurtleView(it) },
                update = { it.configure(count, speed, tilt, lockPreview) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Text(
                "거북이들이 잠금화면 위를 헤엄치듯 날아다닙니다. 시간에 따라 하늘빛이 바뀌고, 기울이면 살짝 흐르고, 톡 치면 한 바퀴 돕니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSetWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("배경화면으로 설정", style = MaterialTheme.typography.titleMedium) }
            Text(
                "Android 14 이상: 미리보기 화면의 ‘배경화면 설정’에서 **잠금화면**(또는 홈·잠금 모두)을 고르세요. 잠금화면에 놓이면 거북이들이 시계 아래로만 다닙니다. 그 아래 버전은 잠금화면에 라이브 배경화면을 둘 수 없어 홈 화면에만 됩니다.".replace("**", ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("거북이 $count 마리", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = count.toFloat(),
                        onValueChange = { count = it.roundToInt().coerceIn(1, 14) },
                        onValueChangeFinished = { prefs.count = count },
                        valueRange = 1f..14f,
                        steps = 12,
                    )
                    Text("속도 ${(speed * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        onValueChangeFinished = { prefs.speed = speed },
                        valueRange = 0.5f..2f,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("기울임 시차", style = MaterialTheme.typography.bodyMedium)
                            Text("폰을 기울이면 구름과 거북이가 깊이에 따라 다르게 흐릅니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = tilt, onCheckedChange = { tilt = it; prefs.tilt = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("잠금화면처럼 미리보기", style = MaterialTheme.typography.bodyMedium)
                            Text("시계 자리(위쪽 40%)를 비워 두는 모습을 봅니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lockPreview, onCheckedChange = { lockPreview = it })
                    }
                }
            }
        }
    }
}
