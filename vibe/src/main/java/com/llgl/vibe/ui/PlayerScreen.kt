package com.llgl.vibe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.vibe.Deps
import com.llgl.vibe.analysis.HapticTrack
import com.llgl.vibe.haptics.Mode
import com.llgl.vibe.player.Session
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(deps: Deps, onBack: () -> Unit) {
    val session = deps.session
    val state by session.state.collectAsStateWithLifecycle()
    val playing by session.playing.collectAsStateWithLifecycle()
    val prefsVersion by deps.prefs.version.collectAsStateWithLifecycle()
    val mode = remember(prefsVersion) { deps.prefs.mode }
    val muted = remember(prefsVersion) { deps.prefs.muted }
    var intensity by remember { mutableFloatStateOf(deps.prefs.intensity) }
    var position by remember { mutableIntStateOf(0) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val view = LocalView.current

    BackHandler(onBack = onBack)
    LaunchedEffect(playing, state) {
        position = session.playback.position
        while (playing) {
            position = session.playback.position
            delay(33)
        }
    }
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    val song = session.song
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(song?.title ?: "플레이어", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    if (state is Session.State.Ready) {
                        IconButton(onClick = { song?.let { session.open(it, reanalyze = true) } }) { Icon(Icons.Default.Refresh, contentDescription = "다시 분석") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                Session.State.Empty -> Text("고른 노래가 없어요.")

                is Session.State.Analyzing -> {
                    Text(s.song.title, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { s.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${s.step} 중… ${(s.progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("한 곡당 한 번만 합니다. 다음부터는 바로 열려요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                is Session.State.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onBack) { Text("돌아가기") }
                }

                is Session.State.Ready -> {
                    val track = s.track
                    val duration = max(1, session.playback.duration)
                    Text(
                        listOfNotNull(s.song.artist.takeIf { it.isNotEmpty() }, if (track.bpm > 0f) "♩ ${track.bpm.roundToInt()} BPM" else null, "비트 ${track.beats.size}개").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Visualizer(track, (scrub?.toInt() ?: position), duration, Modifier.fillMaxWidth().height(150.dp))
                    Slider(
                        value = (scrub ?: position.toFloat()).coerceIn(0f, duration.toFloat()),
                        onValueChange = { scrub = it },
                        onValueChangeFinished = {
                            val target = scrub?.toInt() ?: position
                            session.seekTo(target)
                            position = target
                            scrub = null
                        },
                        valueRange = 0f..duration.toFloat(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime((scrub?.toLong() ?: position.toLong())), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration.toLong()), style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = { session.togglePlay() }, modifier = Modifier.size(76.dp)) {
                            if (playing) Text("❚❚", style = MaterialTheme.typography.titleLarge) else Icon(Icons.Default.PlayArrow, contentDescription = "재생", modifier = Modifier.size(40.dp))
                        }
                    }
                    Text("진동 방식", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in Mode.entries) {
                            FilterChip(selected = mode == m, onClick = { session.setMode(m) }, label = { Text(m.label) })
                        }
                    }
                    Text(
                        when (mode) {
                            Mode.MELODY -> if (session.engine.envelopes) "음높이를 모터 주파수(${session.engine.minHz.toInt()}–${session.engine.maxHz.toInt()} Hz)로 옮겨 실제 음으로 냅니다." else "이 폰은 주파수 제어가 없어 음이 높을수록 빠르게 톡톡거립니다."
                            else -> mode.blurb
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("세기 ${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        onValueChangeFinished = {
                            session.setIntensity(intensity)
                            if (!playing) session.engine.preview()
                        },
                        valueRange = 0.3f..1.5f,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("소리 끄고 진동만", style = MaterialTheme.typography.bodyMedium)
                            Text("음악은 조용히 흐르고 모터만 연주합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = muted, onCheckedChange = { session.setMuted(it) })
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(session.engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("화면이 꺼지면 진동도 멈춥니다(안드로이드는 뒤에서 도는 앱의 진동을 막습니다).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Loudness and bass across the whole song, beat ticks on top, the playhead over it. */
@Composable
private fun Visualizer(track: HapticTrack, positionMs: Int, durationMs: Int, modifier: Modifier) {
    val bassColor = Pink.copy(alpha = 0.75f)
    val loudColor = Mint.copy(alpha = 0.55f)
    val beatColor = Color(0xFFF2EEFF).copy(alpha = 0.5f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val frames = track.frames
        if (frames == 0) return@Canvas
        val columns = (w / 3.dp.toPx()).toInt().coerceAtLeast(1)
        val colW = w / columns
        for (c in 0 until columns) {
            val f0 = (c.toLong() * frames / columns).toInt()
            val f1 = max(f0 + 1, ((c + 1).toLong() * frames / columns).toInt()).coerceAtMost(frames)
            var loud = 0f
            var bass = 0f
            for (f in f0 until f1) {
                if (track.loud[f] > loud) loud = track.loud[f]
                if (track.bass[f] > bass) bass = track.bass[f]
            }
            val x = c * colW
            drawRect(loudColor, topLeft = Offset(x, h * (1f - loud)), size = androidx.compose.ui.geometry.Size(colW * 0.7f, h * loud))
            drawRect(bassColor, topLeft = Offset(x, h * (1f - bass * 0.7f)), size = androidx.compose.ui.geometry.Size(colW * 0.7f, h * bass * 0.7f))
        }
        val tick = 6.dp.toPx()
        for (b in track.beats) {
            val x = b.toFloat() / frames * w
            drawLine(beatColor, Offset(x, 0f), Offset(x, tick), strokeWidth = 1.5f)
        }
        val px = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) * w
        drawLine(Amber, Offset(px, 0f), Offset(px, h), strokeWidth = 2.dp.toPx())
        val f = min(frames - 1, track.frameAt(positionMs.toLong()))
        val glow = max(track.loud[f], track.bass[f])
        drawCircle(Amber.copy(alpha = 0.25f + 0.5f * glow), radius = 4.dp.toPx() + 10.dp.toPx() * glow, center = Offset(px, h - 10.dp.toPx()))
    }
}
