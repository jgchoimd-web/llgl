package com.llgl.app.game

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llgl.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val PREFS_NAME = "llgl"
private const val KEY_BEST = "best_score"
private const val MAX_FRAME_DT = 0.05f

private object Palette {
    val SkyTop = Color(0xFF040511)
    val SkyMid = Color(0xFF1A0B33)
    val Horizon = Color(0xFFFF2D95)
    val Sun = Color(0xFFFF6A3D)
    val SunTop = Color(0xFFFFD166)
    val Asphalt = Color(0xFF0C0F24)
    val AsphaltNear = Color(0xFF161B3D)
    val Cyan = Color(0xFF19E3FF)
    val Magenta = Color(0xFFFF2DB4)
    val Yellow = Color(0xFFFFE94D)
    val Building = Color(0xFF0D0A24)
    val BuildingTop = Color(0xFF261857)
    val BarrierBody = Color(0xFF2A0B33)
    val Marble = Color(0xFF17C6FF)
    val MarbleLight = Color(0xFFE6FCFF)
    val MarbleDeep = Color(0xFF082B66)
    val White = Color.White
    val Dim = Color(0xFF9AA3C7)
    val Neon = listOf(Cyan, Magenta, Yellow)
}

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val radius: Float,
)

/** Render-only state (particles, screen shake) that the pure engine does not know about. */
private class RenderState {
    val particles = ArrayList<Particle>()
    val pending = ArrayList<GameEvent>()
    val random = Random(7)
    var shake = 0f
    var dt = 0f
    var time = 0f
}

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val engine = remember { GameEngine() }
    val render = remember { RenderState() }
    var frame by remember { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    LaunchedEffect(engine) {
        engine.best = prefs.getInt(KEY_BEST, 0)
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(MAX_FRAME_DT)
                last = now
                engine.update(dt)
                render.dt = dt
                render.time += dt
                render.shake = (render.shake - dt * 2.5f).coerceAtLeast(0f)
                for (event in engine.events) {
                    render.pending += event
                    if (event is GameEvent.Crashed) {
                        render.shake = 1f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (engine.isNewBest) prefs.edit().putInt(KEY_BEST, engine.best).apply()
                    }
                }
                frame++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.SkyTop)
            .pointerInput(engine) {
                detectTapGestures { engine.tap() }
            }
            .pointerInput(engine) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val camera = Camera(size.width.toFloat(), size.height.toFloat())
                    engine.steer(drag.x / camera.roadHalf(GameEngine.MARBLE_D))
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reading the frame counter here makes the canvas redraw every frame.
            if (frame >= 0) drawWorld(engine, render)
        }
        Hud(engine = engine, frame = frame)
    }
}

@Composable
private fun Hud(engine: GameEngine, frame: Long) {
    val blink = 0.55f + 0.45f * sin(frame * 0.08f)
    val mono = FontFamily.Monospace

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudValue(
                label = stringResource(R.string.score_label),
                value = engine.score.toString().padStart(6, '0'),
                color = Palette.Cyan,
                alignEnd = false,
            )
            HudValue(
                label = stringResource(R.string.best_label),
                value = engine.best.toString().padStart(6, '0'),
                color = Palette.Magenta,
                alignEnd = true,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${stringResource(R.string.speed_label)} ${(engine.speed * 14f).toInt()} km/h",
            color = Palette.Dim,
            fontSize = 13.sp,
            fontFamily = mono,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (engine.phase) {
            Phase.READY -> TitleCard(blink = blink)
            Phase.GAME_OVER -> GameOverCard(engine = engine, blink = blink)
            Phase.PLAYING -> Unit
        }
    }
}

@Composable
private fun HudValue(label: String, value: String, color: Color, alignEnd: Boolean) {
    val align = if (alignEnd) Alignment.End else Alignment.Start
    Column(horizontalAlignment = align) {
        Text(text = label, color = Palette.Dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Text(
            text = value,
            color = color,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            style = TextStyle(shadow = Shadow(color = color.copy(alpha = 0.8f), blurRadius = 16f)),
        )
    }
}

@Composable
private fun TitleCard(blink: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.game_title),
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Palette.Magenta,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                shadow = Shadow(color = Palette.Cyan, blurRadius = 28f),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.game_subtitle),
            color = Palette.Cyan,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.tap_to_start),
            color = Palette.White.copy(alpha = blink),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.how_to_play),
            color = Palette.Dim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GameOverCard(engine: GameEngine, blink: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.game_over),
            style = TextStyle(
                color = Palette.Magenta,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                shadow = Shadow(color = Palette.Magenta, blurRadius = 24f),
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${stringResource(R.string.score_label)} ${engine.score}",
            color = Palette.White,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        if (engine.isNewBest) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.new_best),
                color = Palette.Yellow,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        if (engine.canRestart) {
            Text(
                text = stringResource(R.string.tap_to_retry),
                color = Palette.White.copy(alpha = blink),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// World rendering
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawWorld(engine: GameEngine, render: RenderState) {
    val curve = sin(engine.distance * 0.045f) * 0.6f
    val camera = Camera(size.width, size.height, curve)
    val shakeX = if (render.shake > 0f) (render.random.nextFloat() - 0.5f) * render.shake * size.width * 0.04f else 0f
    val shakeY = if (render.shake > 0f) (render.random.nextFloat() - 0.5f) * render.shake * size.width * 0.04f else 0f

    translate(left = shakeX, top = shakeY) {
        drawSky(camera, render.time)
        drawSkyline(camera)
        drawGroundAndRoad(camera, engine.distance)
        drawPillars(camera, engine.pillars, render.time)
        drawFog(camera)
        drawObstacles(camera, engine.obstacles, render.time, beyondMarble = true)
        drawMarble(camera, engine)
        drawObstacles(camera, engine.obstacles, render.time, beyondMarble = false)
        updateAndDrawParticles(camera, render)
    }
}

private fun DrawScope.drawSky(camera: Camera, time: Float) {
    val w = size.width
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Palette.SkyTop, Palette.SkyMid, Palette.Horizon.copy(alpha = 0.5f)),
            startY = 0f,
            endY = camera.horizonY,
        ),
        size = Size(w, camera.horizonY),
    )

    // Stars: fixed pseudo-random positions, gentle twinkle.
    for (i in 0 until 70) {
        val sx = ((i * 7919) % 1000) / 1000f * w
        val sy = ((i * 104729) % 1000) / 1000f * camera.horizonY * 0.85f
        val twinkle = 0.35f + 0.65f * abs(sin(time * 1.3f + i))
        drawCircle(
            color = Palette.White,
            radius = (0.8f + (i % 3) * 0.5f) * density,
            center = Offset(sx, sy),
            alpha = twinkle * 0.8f,
        )
    }

    // Synthwave sun at the vanishing point, with the classic horizontal cuts.
    val sunR = w * 0.17f
    val sunCenter = Offset(camera.centerX + camera.bend(40f), camera.horizonY - sunR * 0.15f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Palette.Horizon.copy(alpha = 0.55f), Color.Transparent),
            center = sunCenter,
            radius = sunR * 2.4f,
        ),
        radius = sunR * 2.4f,
        center = sunCenter,
    )
    val sunPath = Path().apply { addOval(Rect(sunCenter - Offset(sunR, sunR), Size(sunR * 2f, sunR * 2f))) }
    clipPath(sunPath) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Palette.SunTop, Palette.Sun, Palette.Horizon),
                startY = sunCenter.y - sunR,
                endY = sunCenter.y + sunR,
            ),
            topLeft = Offset(sunCenter.x - sunR, sunCenter.y - sunR),
            size = Size(sunR * 2f, sunR * 2f),
        )
        var y = sunCenter.y + sunR * 0.05f
        var gap = sunR * 0.07f
        while (y < sunCenter.y + sunR) {
            drawRect(color = Palette.SkyMid, topLeft = Offset(sunCenter.x - sunR, y), size = Size(sunR * 2f, gap * 0.45f))
            y += gap
            gap *= 1.28f
        }
    }
}

private fun DrawScope.drawSkyline(camera: Camera) {
    val w = size.width
    val shift = camera.bend(40f) * 0.5f
    val windowW = 2.dp.toPx()
    val windowH = 3.dp.toPx()
    val rowStep = 6.dp.toPx()
    var x = -w * 0.2f + shift
    var i = 0
    while (x < w * 1.2f) {
        val bw = w * (0.035f + ((i * 37) % 7) / 100f)
        val bh = camera.horizonY * (0.08f + ((i * 53) % 11) / 40f)
        val top = camera.horizonY - bh
        drawRect(
            brush = Brush.verticalGradient(listOf(Palette.BuildingTop, Palette.Building), startY = top, endY = camera.horizonY),
            topLeft = Offset(x, top),
            size = Size(bw, bh),
        )
        var wy = top + bh * 0.15f
        var row = 0
        while (wy < camera.horizonY - rowStep) {
            var wx = x + bw * 0.15f
            var col = 0
            while (wx < x + bw - windowW) {
                if ((i * 31 + col * 17 + row * 7) % 5 == 0) {
                    drawRect(
                        color = Palette.Neon[(i + col) % 3],
                        topLeft = Offset(wx, wy),
                        size = Size(windowW, windowH),
                        alpha = 0.9f,
                    )
                }
                wx += bw * 0.3f
                col++
            }
            wy += rowStep
            row++
        }
        x += bw + w * 0.012f
        i++
    }
}

/** Distance for sample [i] of [samples] between NEAR and [dFar], spaced logarithmically. */
private fun sampleD(i: Int, samples: Int, dFar: Float): Float {
    val t = i / samples.toFloat()
    return GameEngine.NEAR * exp(t * ln(dFar / GameEngine.NEAR))
}

private fun DrawScope.roadPolyline(camera: Camera, u: Float, dFar: Float, samples: Int): Path = Path().apply {
    moveTo(camera.x(GameEngine.NEAR, u), camera.y(GameEngine.NEAR))
    for (i in 1..samples) {
        val d = sampleD(i, samples, dFar)
        lineTo(camera.x(d, u), camera.y(d))
    }
}

private fun DrawScope.drawGroundAndRoad(camera: Camera, distance: Float) {
    val w = size.width
    val h = size.height
    val dFar = 40f
    val samples = 32

    // Ground.
    drawRect(
        brush = Brush.verticalGradient(listOf(Palette.SkyMid, Palette.Asphalt), startY = camera.horizonY, endY = h),
        topLeft = Offset(0f, camera.horizonY),
        size = Size(w, h - camera.horizonY),
    )
    // Ground grid: lines converging on the vanishing point.
    for (k in -5..5) {
        if (k in -1..1) continue
        val u = k * 1.1f
        drawPath(roadPolyline(camera, u, dFar, samples), color = Palette.Magenta, alpha = 0.16f, style = Stroke(width = density))
    }

    // Road body.
    val road = Path().apply {
        moveTo(camera.x(GameEngine.NEAR, -1f), camera.y(GameEngine.NEAR))
        for (i in 1..samples) {
            val d = sampleD(i, samples, dFar)
            lineTo(camera.x(d, -1f), camera.y(d))
        }
        for (i in samples downTo 0) {
            val d = sampleD(i, samples, dFar)
            lineTo(camera.x(d, 1f), camera.y(d))
        }
        close()
    }
    drawPath(road, brush = Brush.verticalGradient(listOf(Palette.Asphalt, Palette.AsphaltNear), startY = camera.horizonY, endY = h))

    // Moving cross lines: they slide toward the camera as distance grows.
    val range = GameEngine.FAR - GameEngine.NEAR
    val spacing = 0.75f
    var k = 0
    while (k * spacing < range) {
        val d = GameEngine.NEAR + (((k * spacing - distance) % range) + range) % range
        val s = camera.scale(d)
        val y = camera.y(d)
        drawLine(Palette.Magenta, Offset(0f, y), Offset(w, y), strokeWidth = (0.5f + 1.5f * s) * density, alpha = 0.08f + 0.2f * s)
        drawLine(Palette.Cyan, Offset(camera.x(d, -1f), y), Offset(camera.x(d, 1f), y), strokeWidth = (0.8f + 2.5f * s) * density, alpha = 0.2f + 0.5f * s)
        k++
    }

    // Dashed lane dividers.
    for (u in floatArrayOf(-0.28f, 0.28f)) {
        var d = GameEngine.NEAR
        val step = 0.25f
        while (d < GameEngine.FAR) {
            val on = (((d + distance) / 0.5f).toInt() % 2 == 0)
            if (on) {
                val d2 = d + step
                drawLine(
                    Palette.Yellow,
                    Offset(camera.x(d, u), camera.y(d)),
                    Offset(camera.x(d2, u), camera.y(d2)),
                    strokeWidth = (0.6f + 2f * camera.scale(d)) * density,
                    alpha = 0.15f + 0.4f * camera.scale(d),
                )
            }
            d += step
        }
    }

    // Neon road edges: a soft wide glow under a bright thin line.
    for (u in floatArrayOf(-1f, 1f)) {
        val edge = roadPolyline(camera, u, dFar, samples)
        drawPath(edge, color = Palette.Cyan, alpha = 0.25f, style = Stroke(width = 7f * density, cap = StrokeCap.Round))
        drawPath(edge, color = Palette.Cyan, alpha = 0.95f, style = Stroke(width = 2f * density, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawPillars(camera: Camera, pillars: List<Pillar>, time: Float) {
    val h = size.height
    val w = size.width
    for (p in pillars.sortedByDescending { it.d }) {
        val s = camera.scale(p.d)
        val baseY = camera.y(p.d)
        val xInner = camera.x(p.d, p.side * 1.25f)
        val xOuter = camera.x(p.d, p.side * (1.25f + p.width * 2f))
        val left = minOf(xInner, xOuter)
        val right = maxOf(xInner, xOuter)
        if (right < -w * 0.5f || left > w * 1.5f) continue
        val top = baseY - h * p.height * s
        val width = right - left
        val neon = Palette.Neon[p.palette]

        drawRect(
            brush = Brush.verticalGradient(listOf(Palette.BuildingTop, Palette.Building), startY = top, endY = baseY),
            topLeft = Offset(left, top),
            size = Size(width, baseY - top),
        )
        val edgeX = if (p.side < 0) right else left
        drawLine(neon, Offset(edgeX, top), Offset(edgeX, baseY), strokeWidth = (1f + 3f * s) * density, alpha = 0.35f + 0.55f * s)
        drawLine(neon, Offset(left, top), Offset(right, top), strokeWidth = (1f + 2f * s) * density, alpha = 0.3f + 0.4f * s)

        if (s > 0.22f) {
            val rows = (p.height * 9f).toInt().coerceIn(3, 10)
            val cols = (p.width * 8f).toInt().coerceIn(2, 5)
            val cellH = (baseY - top) / rows
            val cellW = width / cols
            val flicker = (time * 5f).toInt()
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if ((row * 7 + col * 3 + p.palette) % 4 != 0) continue
                    if ((row + col + flicker) % 29 == 0) continue
                    drawRect(
                        color = neon,
                        topLeft = Offset(left + cellW * (col + 0.3f), top + cellH * (row + 0.25f)),
                        size = Size(cellW * 0.4f, cellH * 0.45f),
                        alpha = 0.75f,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawFog(camera: Camera) {
    val top = camera.horizonY - size.height * 0.04f
    val bottom = camera.horizonY + size.height * 0.12f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Palette.Horizon.copy(alpha = 0.4f), Color.Transparent),
            startY = top,
            endY = bottom,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top),
    )
}

private fun DrawScope.drawObstacles(camera: Camera, obstacles: List<Obstacle>, time: Float, beyondMarble: Boolean) {
    for (o in obstacles.sortedByDescending { it.d }) {
        if ((o.d >= GameEngine.MARBLE_D) != beyondMarble) continue
        val s = camera.scale(o.d)
        val cx = camera.x(o.d, o.u)
        val cy = camera.y(o.d)
        when (o.kind) {
            ObstacleKind.BARRIER -> {
                val halfW = GameEngine.BARRIER_HALF_U * camera.roadHalf(o.d)
                val height = size.height * 0.085f * s
                val top = cy - height
                drawRect(
                    color = Palette.Magenta,
                    topLeft = Offset(cx - halfW * 1.2f, top - height * 0.2f),
                    size = Size(halfW * 2.4f, height * 1.4f),
                    alpha = 0.18f,
                )
                drawRoundRect(
                    color = Palette.BarrierBody,
                    topLeft = Offset(cx - halfW, top),
                    size = Size(halfW * 2f, height),
                    cornerRadius = CornerRadius(3f * density),
                )
                val stripes = 4
                val stripeW = halfW * 2f / stripes
                for (i in 0 until stripes step 2) {
                    drawRect(
                        color = Palette.Yellow,
                        topLeft = Offset(cx - halfW + stripeW * i, top + height * 0.35f),
                        size = Size(stripeW, height * 0.3f),
                        alpha = 0.85f,
                    )
                }
                drawRoundRect(
                    color = Palette.Magenta,
                    topLeft = Offset(cx - halfW, top),
                    size = Size(halfW * 2f, height),
                    cornerRadius = CornerRadius(3f * density),
                    style = Stroke(width = (1f + 3f * s) * density),
                )
            }
            ObstacleKind.ORB -> {
                val pulse = 0.9f + 0.1f * sin(time * 6f + o.u * 10f)
                val r = GameEngine.ORB_HALF_U * camera.roadHalf(o.d) * pulse
                val center = Offset(cx, cy - r * 1.2f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.6f), Color.Transparent), center = center, radius = r * 2.6f),
                    radius = r * 2.6f,
                    center = center,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.White, Palette.Cyan, Palette.Cyan.copy(alpha = 0.7f)),
                        center = center - Offset(r * 0.25f, r * 0.25f),
                        radius = r * 1.2f,
                    ),
                    radius = r,
                    center = center,
                )
            }
        }
    }
}

private fun DrawScope.drawMarble(camera: Camera, engine: GameEngine) {
    val d = GameEngine.MARBLE_D
    val r = GameEngine.MARBLE_HALF_U * camera.roadHalf(d)
    val cx = camera.x(d, engine.marbleU)
    val groundY = camera.y(d)
    val cy = groundY - r * 1.05f
    val center = Offset(cx, cy)

    drawOval(
        color = Color.Black,
        topLeft = Offset(cx - r * 1.1f, groundY - r * 0.28f),
        size = Size(r * 2.2f, r * 0.56f),
        alpha = 0.55f,
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.45f), Color.Transparent), center = center, radius = r * 2.4f),
        radius = r * 2.4f,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Palette.MarbleLight, Palette.Marble, Palette.MarbleDeep),
            center = center - Offset(r * 0.35f, r * 0.4f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = center,
    )

    // Rolling bands: the face we see (the back of the marble) moves upward as it rolls downhill.
    val circle = Path().apply { addOval(Rect(center - Offset(r, r), Size(r * 2f, r * 2f))) }
    clipPath(circle) {
        val period = r * 2f
        val roll = engine.distance * r * 2.4f
        for (i in 0 until 3) {
            val yy = cy - r + (((-roll + i * period / 3f) % period) + period) % period
            val f = ((yy - cy) / r).coerceIn(-1f, 1f)
            val chord = sqrt(1f - f * f)
            drawLine(
                color = Palette.MarbleDeep,
                start = Offset(cx - r * chord, yy),
                end = Offset(cx + r * chord, yy),
                strokeWidth = r * 0.22f * chord + density,
                alpha = 0.5f,
            )
        }
    }

    drawCircle(color = Palette.White, radius = r * 0.22f, center = center - Offset(r * 0.4f, r * 0.45f), alpha = 0.85f)
    drawCircle(color = Palette.Cyan, radius = r, center = center, alpha = 0.5f, style = Stroke(width = r * 0.08f))
}

private fun DrawScope.updateAndDrawParticles(camera: Camera, render: RenderState) {
    for (event in render.pending) {
        when (event) {
            is GameEvent.Collected -> burst(
                render = render,
                x = camera.x(event.d, event.u),
                y = camera.y(event.d) - 12.dp.toPx(),
                count = 16,
                colors = listOf(Palette.Cyan, Palette.White),
                speed = size.width * 0.5f,
                life = 0.55f,
            )
            is GameEvent.Crashed -> burst(
                render = render,
                x = camera.x(GameEngine.MARBLE_D, event.u),
                y = camera.y(GameEngine.MARBLE_D) - 16.dp.toPx(),
                count = 36,
                colors = listOf(Palette.Magenta, Palette.Yellow, Palette.White),
                speed = size.width * 0.9f,
                life = 0.9f,
            )
        }
    }
    render.pending.clear()

    val dt = render.dt
    val gravity = size.height * 1.2f
    val iterator = render.particles.iterator()
    while (iterator.hasNext()) {
        val p = iterator.next()
        p.life -= dt
        if (p.life <= 0f) {
            iterator.remove()
            continue
        }
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vy += gravity * dt
        val fade = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(color = p.color, radius = p.radius * fade, center = Offset(p.x, p.y), alpha = fade)
    }
}

private fun DrawScope.burst(render: RenderState, x: Float, y: Float, count: Int, colors: List<Color>, speed: Float, life: Float) {
    repeat(count) {
        val angle = render.random.nextFloat() * 2f * PI.toFloat()
        val v = speed * (0.3f + render.random.nextFloat() * 0.7f)
        val l = life * (0.6f + render.random.nextFloat() * 0.4f)
        render.particles += Particle(
            x = x,
            y = y,
            vx = cos(angle) * v,
            vy = sin(angle) * v - speed * 0.3f,
            life = l,
            maxLife = l,
            color = colors[render.random.nextInt(colors.size)],
            radius = (1.5f + render.random.nextFloat() * 2.5f) * density,
        )
    }
}
