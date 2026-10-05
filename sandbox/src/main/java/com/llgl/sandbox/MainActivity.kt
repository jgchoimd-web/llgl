package com.llgl.sandbox

import android.os.Bundle
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.llgl.sandbox.render.Palette
import com.llgl.sandbox.sim.Elements
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var view: SandboxView? = null
    private var resumed = false
    private lateinit var prefs: SandboxPrefs
    private lateinit var store: GridStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = SandboxPrefs(this)
        store = GridStore(this)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        view?.resume()
    }

    override fun onPause() {
        resumed = false
        view?.let { v ->
            v.pause()
            v.grid?.let(store::save)
        }
        super.onPause()
    }

    @Composable
    private fun Screen() {
        var element by remember { mutableStateOf(prefs.element) }
        var tool by remember { mutableStateOf(SandboxView.Tool.PAINT) }
        var brush by remember { mutableIntStateOf(prefs.brush) }
        var paused by remember { mutableStateOf(false) }
        var speed by remember { mutableIntStateOf(1) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(Palette.BACKGROUND))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { context ->
                        SandboxView(context).also { v ->
                            v.onGridReady = { g -> store.load(g) }
                            view = v
                            if (resumed) v.resume()
                        }
                    },
                    update = { v ->
                        v.tool = tool
                        v.element = element
                        v.brush = brush
                        v.paused = paused
                        v.speed = speed
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Controls(
                element = element,
                tool = tool,
                brush = brush,
                paused = paused,
                speed = speed,
                onElement = {
                    element = it
                    tool = SandboxView.Tool.PAINT
                    prefs.element = it
                },
                onTool = { tool = it },
                onBrush = {
                    brush = it
                    prefs.brush = it
                },
                onPause = { paused = !paused },
                onStep = { view?.stepOnce() },
                onSpeed = { speed = if (speed == 1) 2 else 1 },
                onClear = { view?.clear() },
            )
        }
    }

    private companion object {
        val Scheme = darkColorScheme(
            primary = Color(0xFFFFC94A),
            surface = Color(0xFF14171C),
            background = Color(0xFF101418),
            onSurface = Color(0xFFE8ECF3),
        )
    }
}

@Composable
private fun Controls(
    element: Byte,
    tool: SandboxView.Tool,
    brush: Int,
    paused: Boolean,
    speed: Int,
    onElement: (Byte) -> Unit,
    onTool: (SandboxView.Tool) -> Unit,
    onBrush: (Int) -> Unit,
    onPause: () -> Unit,
    onStep: () -> Unit,
    onSpeed: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14171C))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Elements.palette) { e ->
                Chip(
                    label = Elements.label[e.toInt()],
                    swatch = Color(Palette.base(e)),
                    selected = tool == SandboxView.Tool.PAINT && element == e,
                    onClick = { onElement(e) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            item { Chip(stringResource(R.string.tool_eraser), Color(0xFF2A2E36), tool == SandboxView.Tool.ERASE) { onTool(SandboxView.Tool.ERASE) } }
            item { Chip(stringResource(R.string.tool_spark), Color(0xFFFFF9B0), tool == SandboxView.Tool.SPARK) { onTool(SandboxView.Tool.SPARK) } }
            item { Chip(stringResource(R.string.tool_switch), Color(0xFF40A070), tool == SandboxView.Tool.TOGGLE) { onTool(SandboxView.Tool.TOGGLE) } }
            item { Chip(stringResource(if (paused) R.string.tool_play else R.string.tool_pause), null, paused, onPause) }
            item { Chip(stringResource(R.string.tool_step), null, false, onStep) }
            item { Chip(stringResource(if (speed == 1) R.string.speed_1x else R.string.speed_2x), null, speed == 2, onSpeed) }
            item { Chip(stringResource(R.string.tool_clear), null, false, onClear) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.brush_label, brush), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
            Slider(
                value = brush.toFloat(),
                onValueChange = { onBrush(it.roundToInt().coerceIn(1, 12)) },
                valueRange = 1f..12f,
                steps = 10,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Chip(label: String, swatch: Color?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF2E3A52) else Color(0xFF1C2028))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFFFC94A) else Color(0xFF2A2E36), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (swatch != null) {
            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(swatch))
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE8ECF3))
        Spacer(modifier = Modifier.width(0.dp))
    }
}
