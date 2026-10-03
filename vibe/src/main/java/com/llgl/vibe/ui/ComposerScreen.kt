package com.llgl.vibe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.llgl.vibe.Deps
import com.llgl.vibe.haptics.Pattern
import com.llgl.vibe.haptics.Sequencer
import kotlin.math.roundToInt

/** A one-bar vibration drum machine: three lanes, sixteen steps, four memory slots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(deps: Deps, onBack: () -> Unit) {
    val engine = deps.session.engine
    var slot by remember { mutableIntStateOf(0) }
    var pattern by remember { mutableStateOf(deps.prefs.pattern(0)?.let { Pattern.load(it) } ?: Pattern()) }
    var version by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var intensity by remember { mutableFloatStateOf(deps.prefs.intensity) }
    val laneColors = listOf(Pink, Amber, Mint)

    fun changed() {
        version++
        deps.prefs.setPattern(slot, pattern.save())
        if (playing) engine.loop(Sequencer.render(pattern, intensity))
    }

    fun stop() {
        playing = false
        engine.stop()
    }

    BackHandler {
        stop()
        onBack()
    }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("진동 작곡기") },
                navigationIcon = {
                    IconButton(onClick = {
                        stop()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
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
            Text("칸을 켜고 ▶를 누르면 한 마디가 계속 반복됩니다. 쿵은 길고 세게, 탁은 중간, 틱은 짧게.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            key(version) {
                for (lane in 0 until pattern.lanes) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(Pattern.LANE_NAMES[lane], modifier = Modifier.width(28.dp), color = laneColors[lane], style = MaterialTheme.typography.labelLarge)
                        for (step in 0 until pattern.steps) {
                            val on = pattern.grid[lane][step]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(if (on) laneColors[lane] else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
                                    .border(if (step % 4 == 0) 1.dp else 0.dp, if (step % 4 == 0) Color.White.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(5.dp))
                                    .clickable {
                                        pattern.toggle(lane, step)
                                        changed()
                                    },
                            )
                        }
                    }
                }
            }
            Text("템포 ${pattern.bpm} BPM", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = pattern.bpm.toFloat(),
                onValueChange = {
                    pattern.bpm = it.roundToInt()
                    version++
                },
                onValueChangeFinished = { changed() },
                valueRange = 60f..180f,
            )
            Text("세기 ${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                onValueChangeFinished = {
                    deps.prefs.intensity = intensity
                    engine.intensity = intensity
                    if (playing) engine.loop(Sequencer.render(pattern, intensity))
                },
                valueRange = 0.3f..1.5f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (playing) {
                            stop()
                        } else {
                            playing = true
                            engine.loop(Sequencer.render(pattern, intensity))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !pattern.isEmpty || playing,
                ) { Text(if (playing) "■ 멈춤" else "▶ 반복 재생") }
                OutlinedButton(
                    onClick = {
                        pattern.clear()
                        changed()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("비우기") }
            }
            Text("저장 칸", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 4) {
                    FilterChip(
                        selected = slot == i,
                        onClick = {
                            if (slot != i) {
                                deps.prefs.setPattern(slot, pattern.save())
                                slot = i
                                pattern = deps.prefs.pattern(i)?.let { Pattern.load(it) } ?: Pattern()
                                version++
                                if (playing) engine.loop(Sequencer.render(pattern, intensity))
                            }
                        },
                        label = { Text("ABCD"[i].toString()) },
                    )
                }
            }
            Text(engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
