package com.llgl.tube

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.llgl.tube.yt.Channel
import com.llgl.tube.yt.PlayerPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            TubeTheme {
                SetupScreen(prefs, onSetWallpaper = { openWallpaperPicker() })
            }
        }
    }

    /** Opens the system's live wallpaper preview for this wallpaper, or the general chooser as a fallback. */
    private fun openWallpaperPicker() {
        val component = ComponentName(this, TubeWallpaper::class.java)
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
    var channel by remember { mutableStateOf(prefs.channel) }
    var channelId by remember { mutableStateOf(prefs.channelId) }
    var channelTitle by remember { mutableStateOf(prefs.channelTitle) }
    var muted by remember { mutableStateOf(prefs.muted) }
    var cover by remember { mutableStateOf(prefs.fit != PlayerPage.FIT_CONTAIN) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var nowPlaying by remember { mutableStateOf(prefs.nowPlaying) }
    var status by remember { mutableStateOf(prefs.status) }
    var resolving by remember { mutableStateOf(false) }
    var resolveMessage by remember { mutableStateOf("") }
    var previewMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Prefs.KEY_NOW_PLAYING -> nowPlaying = prefs.nowPlaying
                Prefs.KEY_STATUS -> status = prefs.status
            }
        }
        prefs.register(listener)
        onDispose { prefs.unregister(listener) }
    }

    fun resolve() {
        resolving = true
        resolveMessage = ""
        val input = channel
        scope.launch(Dispatchers.IO) {
            val result = ChannelResolver().resolve(input)
            withContext(Dispatchers.Main) {
                resolving = false
                result.onSuccess {
                    channelId = it.id
                    channelTitle = it.title
                    prefs.channel = input
                    prefs.channelTitle = it.title
                    prefs.channelId = it.id
                    resolveMessage = "확인: ${it.title} (${it.id})"
                }.onFailure {
                    resolveMessage = it.message ?: "확인 실패"
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("튜브 배경화면") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val previewList = Channel.uploadsPlaylist(channelId)
            if (previewList != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(
                            ctx,
                            object : PlayerView.Listener {
                                override fun onReady() {
                                    previewMessage = ""
                                }

                                override fun onState(state: Int) {}

                                override fun onTitle(title: String) {
                                    previewMessage = title
                                }

                                override fun onError(code: Int) {
                                    previewMessage = "유튜브 오류 $code, 다음 영상으로"
                                }
                            },
                        ).also {
                            it.load(previewList, PlayerPage.FIT_CONTAIN, muted = true)
                            it.play()
                        }
                    },
                    update = { it.setMuted(true) },
                    onRelease = { it.destroy() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Text(
                    if (previewMessage.isEmpty()) "미리보기 (소리 없음) · $channelTitle" else "미리보기 (소리 없음) · $previewMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "유튜브 채널의 영상이 홈·잠금화면 뒤에서 재생됩니다. 유튜브 공식 임베드 플레이어를 그대로 쓰고(광고가 나올 수 있고, 임베드가 막힌 영상은 자동으로 건너뜁니다), 채널의 업로드 목록을 섞어서 틉니다. 배경화면이 보일 때만 재생하고 다른 앱이 덮거나 화면이 꺼지면 멈춥니다. 톡 치면 멈춤/재생, 두 번 치면 다음 영상.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSetWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("배경화면으로 설정", style = MaterialTheme.typography.titleMedium) }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("채널", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = channel,
                        onValueChange = { channel = it },
                        singleLine = true,
                        label = { Text("@핸들, 채널 ID(UC…), 또는 채널 주소") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { resolve() }, enabled = !resolving) { Text(if (resolving) "확인 중…" else "확인하고 쓰기") }
                        Text(
                            if (resolveMessage.isNotEmpty()) resolveMessage else "지금: $channelTitle ($channelId)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "채널 페이지에서 ID를 읽고 유튜브 RSS 피드로 이름을 확인합니다. API 키는 필요 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("소리", style = MaterialTheme.typography.bodyMedium)
                            Text("끄면 영상만 흐릅니다. 켜면 미디어 볼륨으로 나옵니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = !muted, onCheckedChange = { muted = !it; prefs.muted = muted })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("화면 꽉 채우기", style = MaterialTheme.typography.bodyMedium)
                            Text("세로 화면에 맞춰 16:9 영상의 양옆을 잘라 채웁니다. 끄면 위아래가 비는 대신 다 보입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = cover, onCheckedChange = { cover = it; prefs.fit = if (it) PlayerPage.FIT_COVER else PlayerPage.FIT_CONTAIN })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi에서만 재생", style = MaterialTheme.typography.bodyMedium)
                            Text("모바일 데이터에서는 멈추고 안내만 보입니다. 영상은 데이터를 많이 씁니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; prefs.wifiOnly = it })
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("배경화면 상태", style = MaterialTheme.typography.titleSmall)
                    Text(if (status.isEmpty()) "아직 배경화면으로 설정되지 않았어요." else status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (nowPlaying.isNotEmpty()) Text("지금: $nowPlaying", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "알아 둘 것: 홈 화면이 보이는 동안 영상이 계속 돌아 배터리와 데이터를 꽤 씁니다. 유튜브는 백그라운드 재생을 프리미엄 기능으로 두므로 이 앱은 화면에 플레이어가 보일 때만 틉니다. 일부 기기에서는 가상 디스플레이에 영상이 검게 나올 수 있는데, 그러면 알려 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
