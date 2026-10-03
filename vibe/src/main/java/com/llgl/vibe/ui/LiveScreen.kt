package com.llgl.vibe.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.vibe.Deps
import com.llgl.vibe.capture.CaptureService
import com.llgl.vibe.capture.LiveState
import com.llgl.vibe.haptics.Mode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Live mode: other apps' sound → this motor. Permissions, the capture consent, and the controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(deps: Deps, onBack: () -> Unit) {
    val context = LocalContext.current
    val live by LiveState.flow.collectAsStateWithLifecycle()
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var intensity by remember { mutableFloatStateOf(live.intensity) }
    var wantStart by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)
    LaunchedEffect(live.beats) {
        flash = 1
        delay(120)
        flash = 0
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            deps.session.pause()
            CaptureService.start(context, result.resultCode, data)
        } else {
            LiveState.update { it.copy(starting = false, error = "캡처 동의를 받지 못했어요") }
        }
    }
    fun launchProjection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        LiveState.update { it.copy(starting = true, error = null) }
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchProjection() }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            LiveState.update { it.copy(error = "오디오 캡처 권한(마이크 권한으로 표시됨)이 있어야 다른 앱의 소리를 받을 수 있어요.") }
        } else {
            wantStart = true
        }
    }
    fun start() {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchProjection()
    }
    LaunchedEffect(wantStart) {
        if (wantStart) {
            wantStart = false
            start()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("실시간 모드") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("다른 앱(YouTube, 브라우저, 음악 앱)이 내는 소리를 받아 바로 진동으로 바꿉니다. 소리는 끄고 진동만 느낄 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    Text("시작하면 화면 캡처 동의 창이 뜹니다(소리만 받고 화면은 저장하지 않습니다). 홈으로 나가도 알림에서 계속 조절할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("DRM이 걸린 앱(Spotify, Netflix 등)은 캡처를 막아 잡히지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!supported) {
                Text("Android 10 이상에서만 됩니다.", color = MaterialTheme.colorScheme.error)
            }
            live.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Meter(live.loud, live.bass, flash == 1, Modifier.fillMaxWidth().height(90.dp))
            Text(
                when {
                    live.running && live.silentMs >= 3000 -> "소리가 안 들어와요. 다른 앱에서 재생 중인지, 그 앱이 캡처를 막지 않는지 확인하세요. 볼륨을 한 칸 올려 보는 것도 방법이에요."
                    live.running -> "듣는 중 · 비트 ${live.beats}"
                    live.starting -> "시작하는 중…"
                    else -> "꺼짐"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (live.running && live.silentMs >= 3000) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!live.running) {
                    Button(onClick = { start() }, enabled = supported && !live.starting, modifier = Modifier.weight(1f)) { Text("▶ 시작") }
                } else {
                    OutlinedButton(onClick = { CaptureService.stop(context) }, modifier = Modifier.weight(1f)) { Text("■ 정지") }
                }
            }

            Text("진동 방식", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in Mode.entries) {
                    FilterChip(selected = live.mode == m, onClick = { LiveState.update { it.copy(mode = m) } }, label = { Text(m.label) })
                }
            }
            Text(
                if (live.mode == Mode.MELODY) {
                    if (deps.session.engine.envelopes) "음높이를 모터 주파수로 옮겨 실제 음으로 냅니다." else "이 폰은 주파수 제어가 없어 음이 높을수록 빠르게 톡톡거립니다."
                } else {
                    live.mode.blurb
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("세기 ${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = intensity,
                onValueChange = {
                    intensity = it
                    LiveState.update { s -> s.copy(intensity = it) }
                },
                onValueChangeFinished = {
                    deps.prefs.intensity = intensity
                    if (!live.running) deps.session.engine.preview()
                },
                valueRange = 0.3f..1.5f,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("소리 끄기 (미디어 볼륨 0)", style = MaterialTheme.typography.bodyMedium)
                    Text("정지하면 볼륨을 원래대로 돌려놓습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = live.muted,
                    onCheckedChange = { if (live.running) CaptureService.toggleMute(context) else LiveState.update { s -> s.copy(muted = it) } },
                )
            }
            Text(deps.session.engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Meter(loud: Float, bass: Float, flash: Boolean, modifier: Modifier) {
    val loudColor = Mint
    val bassColor = Pink
    val track = Color(0xFF2A2140)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = (h - 18.dp.toPx()) / 2f
        drawRect(track, Offset(0f, 0f), Size(w, bar))
        drawRect(loudColor, Offset(0f, 0f), Size(w * loud.coerceIn(0f, 1f), bar))
        drawRect(track, Offset(0f, bar + 6.dp.toPx()), Size(w, bar))
        drawRect(bassColor, Offset(0f, bar + 6.dp.toPx()), Size(w * bass.coerceIn(0f, 1f), bar))
        drawCircle(if (flash) Amber else track, radius = 5.dp.toPx(), center = Offset(w - 8.dp.toPx(), h - 6.dp.toPx()))
    }
}
