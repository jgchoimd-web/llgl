package com.llgl.vibe.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.vibe.Deps
import com.llgl.vibe.capture.CaptureService
import com.llgl.vibe.capture.LiveState
import com.llgl.vibe.haptics.Mode
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** The whole app: other apps' sound → this motor. Permissions, the capture consent, and the controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(deps: Deps) {
    val context = LocalContext.current
    val live by LiveState.flow.collectAsStateWithLifecycle()
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var intensity by remember { mutableFloatStateOf(live.intensity) }
    var wantStart by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }

    LaunchedEffect(live.beats) {
        if (live.beats > 0) {
            flash = true
            delay(120)
            flash = false
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.start(context, result.resultCode, data)
        } else {
            LiveState.update { it.copy(starting = false, error = "캡처 동의를 받지 못했어요. 시작을 다시 눌러 주세요.") }
        }
    }
    fun launchProjection() {
        if (!supported) return
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        LiveState.update { it.copy(starting = true, error = null) }
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchProjection() }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            wantStart = true
        } else {
            LiveState.update { it.copy(error = "오디오 캡처 권한(마이크 권한으로 표시됨)이 있어야 다른 앱의 소리를 받을 수 있어요.") }
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

    val quiet = live.running && live.silentMs >= 3000
    val status = when {
        quiet -> "소리가 안 들어와요. 다른 앱에서 재생 중인지, 그 앱이 캡처를 막지 않는지 확인하세요."
        live.running -> "듣는 중 · 비트 ${live.beats}"
        live.starting -> "시작하는 중…"
        else -> "꺼짐 · 시작을 누르고 다른 앱에서 소리를 재생하세요"
    }

    Scaffold(topBar = { TopAppBar(title = { Text("진동 뮤직") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("다른 앱의 소리를 진동으로", style = MaterialTheme.typography.titleMedium)
            Text(
                "YouTube·브라우저·음악 앱이 내는 소리를 그 자리에서 받아 모터로 연주합니다. 소리는 끄고 진동만 느낄 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!supported) Text("Android 10 이상에서만 됩니다.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            live.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

            Pulse(live.loud, live.bass, flash, live.running, Modifier.size(220.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (quiet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (!live.running) {
                Button(
                    onClick = { start() },
                    enabled = supported && !live.starting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("▶ 시작", style = MaterialTheme.typography.titleMedium) }
            } else {
                OutlinedButton(
                    onClick = { CaptureService.stop(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("■ 정지", style = MaterialTheme.typography.titleMedium) }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("진동 방식", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in Mode.entries) {
                            FilterChip(
                                selected = live.mode == m,
                                onClick = {
                                    LiveState.update { it.copy(mode = m) }
                                    deps.prefs.mode = m
                                },
                                label = { Text(m.label) },
                            )
                        }
                    }
                    Text(
                        if (live.mode == Mode.MELODY) {
                            if (deps.engine.envelopes) "음높이를 모터 주파수로 옮겨 실제 음으로 냅니다." else "이 폰은 주파수 제어가 없어 음이 높을수록 빠르게 톡톡거립니다."
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
                            if (!live.running) deps.engine.preview(intensity)
                        },
                        valueRange = 0.3f..1.5f,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("소리 끄기", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "미디어 볼륨을 0으로 두고 진동만 느낍니다. 정지하면 볼륨을 원래대로 돌려놓아요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = live.muted,
                            onCheckedChange = { on ->
                                if (live.running) {
                                    CaptureService.toggleMute(context)
                                } else {
                                    LiveState.update { s -> s.copy(muted = on) }
                                    deps.prefs.muted = on
                                }
                            },
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("시작하면 화면 캡처 동의 창이 뜹니다. 소리만 받고 화면은 저장하지 않아요. 홈으로 나가도 알림에서 소리 켜기/끄기와 정지를 할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("DRM이 걸린 앱(Spotify, Netflix 등)은 캡처를 막아 잡히지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("진동이 약하면 시스템 설정 › 소리 및 진동 › ‘미디어 진동’을 올리세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(deps.engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A disc that breathes with the loudness, a halo that swells with the bass, a ring that flashes on each beat. */
@Composable
private fun Pulse(loud: Float, bass: Float, flash: Boolean, running: Boolean, modifier: Modifier) {
    val l by animateFloatAsState(if (running) loud.coerceIn(0f, 1f) else 0f, tween(90), label = "loud")
    val b by animateFloatAsState(if (running) bass.coerceIn(0f, 1f) else 0f, tween(120), label = "bass")
    val track = Color(0xFF2A2140)
    val ring = Color(0xFF3A2D55)
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val rMax = min(size.width, size.height) / 2f
        drawCircle(track, radius = rMax, center = c)
        drawCircle(ring, radius = rMax - 1.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
        drawCircle(Pink.copy(alpha = 0.25f + 0.6f * b), radius = rMax * (0.4f + 0.58f * b), center = c)
        drawCircle(Mint, radius = rMax * (0.12f + 0.6f * l), center = c)
        if (flash) drawCircle(Amber, radius = rMax - 3.dp.toPx(), center = c, style = Stroke(width = 6.dp.toPx()))
    }
}
