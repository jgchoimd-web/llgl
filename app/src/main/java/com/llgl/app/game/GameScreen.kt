package com.llgl.app.game

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llgl.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val PREFS_NAME = "llgl"
private const val KEY_BEST = "best_score"
private const val MAX_FRAME_DT = 0.05f

/** Cube sizes of the skyline, as fractions of the screen width. */
private const val FRONT_CUBE = 0.062f
private const val BACK_CUBE = 0.046f

private object Palette {
    val Sky = Color.Black
    val Slab = Color(0xFF4C508F)
    val SlabSeam = Color(0xFF6A6EB8)
    val Lip = Color(0xFF9296E8)
    val LipLight = Color(0xFFC3C6FF)
    val LipShadow = Color(0xFF2A2D66)
    val Cube = Color(0xFF363A7C)
    val CubeBack = Color(0xFF2A2D64)
    val CubeTop = Color(0xFF4B4F9C)
    val CubeSide = Color(0xFF2B2E68)
    val CubeEdge = Color(0xFF9DA1FF)
    val Cyan = Color(0xFF1FFFFF)
    val CyanDeep = Color(0xFF0B6B78)
    val Magenta = Color(0xFFE31FA8)
    val MagentaLight = Color(0xFFFFB3E6)
    val MagentaDeep = Color(0xFF7A0E60)
    val MagentaDark = Color(0xFF3A0730)
    val Pink = Color(0xFFE05A9E)
    val Teal = Color(0xFF5DE0C8)
    val White = Color.White
    val Outline = Color(0xFF1D1F4A)
    val Dim = Color(0xFFB6B9E0)
    val Purple = Color(0xFF6A2BD9)
    val Violet = Color(0xFFD24BE8)
    val Barrier = Color(0xFF2B2A6A)
    val BarrierTop = Color(0xFF3E3C8C)
    val BarrierSide = Color(0xFF221F55)
    val BarrierEdge = Color(0xFFFF3DB8)
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

/** A voxel tower in the skyline. [x] is the left edge as a fraction of the screen width. */
private class Building(val x: Float, val cubesWide: Int, val cubesTall: Int, val back: Boolean)

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
    val city = remember { generateCity(Random(11)) }
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
                    when (event) {
                        is GameEvent.Crashed -> {
                            render.shake = 1f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (engine.isNewBest) prefs.edit().putInt(KEY_BEST, engine.best).apply()
                        }
                        GameEvent.Boosted -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        is GameEvent.Collected -> Unit
                    }
                }
                frame++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Sky)
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
            if (frame >= 0) drawWorld(engine, render, city)
        }
        Hud(engine = engine, frame = frame)
    }
}

// ---------------------------------------------------------------------------------------------
// HUD
// ---------------------------------------------------------------------------------------------

@Composable
private fun Hud(engine: GameEngine, frame: Long) {
    val blink = 0.55f + 0.45f * sin(frame * 0.08f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                if (engine.phase != Phase.READY) {
                    RoundButton(onClick = engine::backToTitle) { backIcon() }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(text = stringResource(R.string.score_label), fontSize = 14.sp, color = Palette.White, letterSpacing = 1.sp)
                OutlinedText(text = engine.score.toString(), fontSize = 30.sp, color = Palette.White)
            }
            Spacer(modifier = Modifier.width(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(text = stringResource(R.string.best_label), fontSize = 14.sp, color = Palette.Teal, letterSpacing = 1.sp)
                OutlinedText(text = engine.best.toString(), fontSize = 30.sp, color = Palette.Teal)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(52.dp)) {
                if (engine.phase == Phase.PLAYING || engine.phase == Phase.PAUSED) {
                    RoundButton(onClick = engine::togglePause) {
                        if (engine.phase == Phase.PAUSED) playIcon() else pauseIcon()
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (engine.phase) {
                Phase.READY -> TitleCard(blink = blink)
                Phase.PAUSED -> PausedCard(blink = blink)
                Phase.GAME_OVER -> GameOverCard(engine = engine, blink = blink)
                Phase.PLAYING -> Unit
            }
        }

        val showSpeedUp = engine.phase == Phase.PLAYING && engine.boostTime > GameEngine.BOOST_DURATION - 1.1f
        if (showSpeedUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 56.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                OutlinedText(
                    text = stringResource(R.string.speed_up),
                    fontSize = 46.sp,
                    color = Palette.White,
                    outline = Palette.CyanDeep,
                    outlineWidth = 4.dp,
                )
            }
        }
    }
}

/** Chunky game text: a rounded dark outline under a solid fill. */
@Composable
private fun OutlinedText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    outline: Color = Palette.Outline,
    outlineWidth: Dp = 3.dp,
    letterSpacing: TextUnit = 0.sp,
) {
    val strokePx = with(LocalDensity.current) { outlineWidth.toPx() }
    val base = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.SansSerif,
        letterSpacing = letterSpacing,
        textAlign = TextAlign.Center,
        lineHeight = fontSize * 1.15f,
    )
    Box(modifier = modifier) {
        Text(
            text = text,
            style = base.copy(
                color = outline,
                drawStyle = Stroke(width = strokePx * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round),
            ),
        )
        Text(text = text, style = base.copy(color = color))
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, icon: DrawScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Palette.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) { icon() }
    }
}

private fun DrawScope.backIcon() {
    val path = Path().apply {
        moveTo(size.width * 0.7f, 0f)
        lineTo(size.width * 0.3f, size.height / 2f)
        lineTo(size.width * 0.7f, size.height)
    }
    drawPath(path, Palette.Pink, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.pauseIcon() {
    val barW = size.width * 0.26f
    val radius = CornerRadius(barW / 2f)
    drawRoundRect(Palette.Pink, topLeft = Offset(size.width * 0.16f, 0f), size = Size(barW, size.height), cornerRadius = radius)
    drawRoundRect(Palette.Pink, topLeft = Offset(size.width * 0.58f, 0f), size = Size(barW, size.height), cornerRadius = radius)
}

private fun DrawScope.playIcon() {
    val path = Path().apply {
        moveTo(size.width * 0.25f, 0f)
        lineTo(size.width * 0.95f, size.height / 2f)
        lineTo(size.width * 0.25f, size.height)
        close()
    }
    drawPath(path, Palette.Pink, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    drawPath(path, Palette.Pink)
}

@Composable
private fun TitleCard(blink: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText(
            text = stringResource(R.string.game_title),
            fontSize = 42.sp,
            color = Palette.White,
            outline = Palette.Magenta,
            outlineWidth = 4.dp,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedText(
            text = stringResource(R.string.game_subtitle),
            fontSize = 15.sp,
            color = Palette.Cyan,
            outlineWidth = 2.dp,
        )
        Spacer(modifier = Modifier.height(44.dp))
        OutlinedText(
            text = stringResource(R.string.tap_to_start),
            fontSize = 22.sp,
            color = Palette.White.copy(alpha = blink),
            outline = Palette.Outline.copy(alpha = blink),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.how_to_play),
            color = Palette.Dim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun PausedCard(blink: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText(text = stringResource(R.string.paused), fontSize = 38.sp, color = Palette.White, outlineWidth = 4.dp)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedText(
            text = stringResource(R.string.tap_to_resume),
            fontSize = 20.sp,
            color = Palette.White.copy(alpha = blink),
            outline = Palette.Outline.copy(alpha = blink),
        )
    }
}

@Composable
private fun GameOverCard(engine: GameEngine, blink: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText(
            text = stringResource(R.string.game_over),
            fontSize = 42.sp,
            color = Palette.White,
            outline = Palette.Magenta,
            outlineWidth = 4.dp,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedText(
            text = "${stringResource(R.string.score_label)} ${engine.score}",
            fontSize = 26.sp,
            color = Palette.White,
        )
        if (engine.isNewBest) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedText(text = stringResource(R.string.new_best), fontSize = 18.sp, color = Palette.Teal, outlineWidth = 2.dp)
        }
        Spacer(modifier = Modifier.height(40.dp))
        if (engine.canRestart) {
            OutlinedText(
                text = stringResource(R.string.tap_to_retry),
                fontSize = 20.sp,
                color = Palette.White.copy(alpha = blink),
                outline = Palette.Outline.copy(alpha = blink),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// World rendering
// ---------------------------------------------------------------------------------------------

private fun generateCity(random: Random): List<Building> {
    val result = ArrayList<Building>()
    for ((start, end) in listOf(-0.08f to 0.40f, 0.60f to 1.08f)) {
        var x = start + random.nextFloat() * 0.04f
        while (x < end) {
            val wide = 1 + random.nextInt(3)
            result += Building(x, wide, 2 + random.nextInt(4), back = true)
            x += wide * BACK_CUBE + random.nextFloat() * 0.03f
        }
        x = start
        while (x < end) {
            val wide = 1 + random.nextInt(3)
            result += Building(x, wide, 1 + random.nextInt(4), back = false)
            x += wide * FRONT_CUBE + 0.01f + random.nextFloat() * 0.025f
        }
    }
    // Back row first, then outer towers before inner ones so the side faces that point at the
    // road are covered by their neighbours.
    return result.sortedWith(compareByDescending<Building> { it.back }.thenByDescending { abs(it.x - 0.5f) })
}

private fun DrawScope.drawWorld(engine: GameEngine, render: RenderState, city: List<Building>) {
    val camera = Camera(size.width, size.height)
    val shakeX = if (render.shake > 0f) (render.random.nextFloat() - 0.5f) * render.shake * size.width * 0.04f else 0f
    val shakeY = if (render.shake > 0f) (render.random.nextFloat() - 0.5f) * render.shake * size.width * 0.04f else 0f

    translate(left = shakeX, top = shakeY) {
        drawRect(color = Palette.Sky, size = size)
        drawCornerAccent()
        drawCity(camera, city, engine)
        drawSlab(camera, engine.distance)
        drawBoostGlow(engine)
        drawObstacles(camera, engine.obstacles, render.time, beyondMarble = true)
        drawMarble(camera, engine)
        drawObstacles(camera, engine.obstacles, render.time, beyondMarble = false)
        updateAndDrawParticles(camera, render)
    }
}

/** The purple wedge in the top-left corner of the reference look. */
private fun DrawScope.drawCornerAccent() {
    val w = size.width
    val h = size.height
    val wedge = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.13f, 0f)
        lineTo(0f, h * 0.16f)
        close()
    }
    drawPath(
        wedge,
        brush = Brush.linearGradient(
            colors = listOf(Palette.Violet, Palette.Purple),
            start = Offset(0f, 0f),
            end = Offset(w * 0.13f, h * 0.16f),
        ),
    )
}

private fun DrawScope.drawCity(camera: Camera, city: List<Building>, engine: GameEngine) {
    val w = size.width
    val h = size.height
    // A little parallax: the skyline leans away from the steering and sways slowly with distance.
    val shift = -engine.marbleU * w * 0.03f + sin(engine.distance * 0.02f) * w * 0.012f
    val edge = 1.2f * density

    for (b in city) {
        val cube = (if (b.back) BACK_CUBE else FRONT_CUBE) * w
        val x0 = b.x * w + shift * (if (b.back) 0.6f else 1f)
        val width = b.cubesWide * cube
        val height = b.cubesTall * cube
        val baseY = camera.lipY + if (b.back) -h * 0.015f else h * 0.004f
        val top = baseY - height
        val xMid = x0 + width / 2f
        val sx = (camera.centerX - xMid) / camera.centerX * cube * 0.55f
        val sy = cube * 0.42f
        val front = if (b.back) Palette.CubeBack else Palette.Cube
        val edgeAlpha = if (b.back) 0.55f else 0.95f

        // Top face.
        val topFace = Path().apply {
            moveTo(x0, top)
            lineTo(x0 + width, top)
            lineTo(x0 + width + sx, top - sy)
            lineTo(x0 + sx, top - sy)
            close()
        }
        drawPath(topFace, Palette.CubeTop)
        // Side face that points at the road.
        val sideX = if (sx > 0f) x0 + width else x0
        val sideFace = Path().apply {
            moveTo(sideX, top)
            lineTo(sideX + sx, top - sy)
            lineTo(sideX + sx, baseY - sy)
            lineTo(sideX, baseY)
            close()
        }
        drawPath(sideFace, Palette.CubeSide)
        // Front face.
        drawRect(front, topLeft = Offset(x0, top), size = Size(width, height))

        // Wireframe edges: every cube boundary on each face.
        for (i in 0..b.cubesWide) {
            val x = x0 + i * cube
            drawLine(Palette.CubeEdge, Offset(x, top), Offset(x, baseY), strokeWidth = edge, alpha = edgeAlpha)
            drawLine(Palette.CubeEdge, Offset(x, top), Offset(x + sx, top - sy), strokeWidth = edge, alpha = edgeAlpha * 0.8f)
        }
        for (j in 0..b.cubesTall) {
            val y = top + j * cube
            drawLine(Palette.CubeEdge, Offset(x0, y), Offset(x0 + width, y), strokeWidth = edge, alpha = edgeAlpha)
            drawLine(Palette.CubeEdge, Offset(sideX, y), Offset(sideX + sx, y - sy), strokeWidth = edge, alpha = edgeAlpha * 0.8f)
        }
        drawLine(Palette.CubeEdge, Offset(x0 + sx, top - sy), Offset(x0 + width + sx, top - sy), strokeWidth = edge, alpha = edgeAlpha * 0.8f)
        drawLine(Palette.CubeEdge, Offset(sideX + sx, top - sy), Offset(sideX + sx, baseY - sy), strokeWidth = edge, alpha = edgeAlpha * 0.8f)
    }
}

private fun DrawScope.drawSlab(camera: Camera, distance: Float) {
    val near = GameEngine.NEAR
    val far = GameEngine.FAR

    val slab = Path().apply {
        moveTo(camera.x(near, -1f), camera.y(near))
        lineTo(camera.x(far, -1f), camera.y(far))
        lineTo(camera.x(far, 1f), camera.y(far))
        lineTo(camera.x(near, 1f), camera.y(near))
        close()
    }
    drawPath(slab, Palette.Slab)

    // Faint panel seams sliding toward the camera give a sense of speed on the plain slab.
    val range = far - near
    val spacing = 1.25f
    var k = 0
    while (k * spacing < range) {
        val d = near + (((k * spacing - distance) % range) + range) % range
        val y = camera.y(d)
        drawLine(
            Palette.SlabSeam,
            Offset(camera.x(d, -1f), y),
            Offset(camera.x(d, 1f), y),
            strokeWidth = 1.5f * density,
            alpha = 0.22f + 0.18f * camera.scale(d),
        )
        k++
    }

    // The lit far edge of the slab and the shadow it casts.
    val lipFar = far - 0.9f
    val shadowFar = far - 1.25f
    val lip = Path().apply {
        moveTo(camera.x(far, -1f), camera.y(far))
        lineTo(camera.x(far, 1f), camera.y(far))
        lineTo(camera.x(lipFar, 1f), camera.y(lipFar))
        lineTo(camera.x(lipFar, -1f), camera.y(lipFar))
        close()
    }
    drawPath(lip, Palette.Lip)
    val shadow = Path().apply {
        moveTo(camera.x(lipFar, -1f), camera.y(lipFar))
        lineTo(camera.x(lipFar, 1f), camera.y(lipFar))
        lineTo(camera.x(shadowFar, 1f), camera.y(shadowFar))
        lineTo(camera.x(shadowFar, -1f), camera.y(shadowFar))
        close()
    }
    drawPath(shadow, Palette.LipShadow, alpha = 0.55f)
    drawLine(
        Palette.LipLight,
        Offset(camera.x(far, -1f), camera.y(far)),
        Offset(camera.x(far, 1f), camera.y(far)),
        strokeWidth = 2f * density,
    )
}

/** The cyan pad glow under the marble while a boost is active. */
private fun DrawScope.drawBoostGlow(engine: GameEngine) {
    if (!engine.isBoosting) return
    val a = (engine.boostTime / GameEngine.BOOST_DURATION).coerceIn(0f, 1f)
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h * 1.02f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Palette.Cyan.copy(alpha = 0.9f * a), Palette.Cyan.copy(alpha = 0.75f * a), Color.Transparent),
            center = center,
            radius = w * 0.7f,
        ),
        topLeft = Offset(center.x - w * 0.7f, center.y - h * 0.3f),
        size = Size(w * 1.4f, h * 0.6f),
    )
}

private fun DrawScope.drawObstacles(camera: Camera, obstacles: List<Obstacle>, time: Float, beyondMarble: Boolean) {
    for (o in obstacles.sortedByDescending { it.d }) {
        if ((o.d >= GameEngine.MARBLE_D) != beyondMarble) continue
        when (o.kind) {
            ObstacleKind.BOOST -> drawBoostPad(camera, o)
            ObstacleKind.BARRIER -> drawBarrier(camera, o)
            ObstacleKind.ORB -> drawOrb(camera, o, time)
        }
    }
}

/** A full-width cyan chevron pointing down the slope. */
private fun DrawScope.drawBoostPad(camera: Camera, o: Obstacle) {
    val thickness = 0.45f
    val apex = 0.8f
    val d0 = o.d
    val d1 = o.d - thickness
    val chevron = Path().apply {
        moveTo(camera.x(d0, -1f), camera.y(d0))
        lineTo(camera.x(d0 + apex, 0f), camera.y(d0 + apex))
        lineTo(camera.x(d0, 1f), camera.y(d0))
        lineTo(camera.x(d1, 1f), camera.y(d1))
        lineTo(camera.x(d1 + apex, 0f), camera.y(d1 + apex))
        lineTo(camera.x(d1, -1f), camera.y(d1))
        close()
    }
    val alpha = if (o.consumed) 0.55f else 0.95f
    drawPath(chevron, Palette.Cyan, alpha = alpha * 0.35f, style = Stroke(width = 10f * density, join = StrokeJoin.Round))
    drawPath(chevron, Palette.Cyan, alpha = alpha)
}

/** A wireframe block in the style of the skyline, with hot-pink edges to read as danger. */
private fun DrawScope.drawBarrier(camera: Camera, o: Obstacle) {
    val halfW = GameEngine.BARRIER_HALF_U * camera.roadHalf(o.d)
    val width = halfW * 2f
    val height = width * 0.85f
    val cx = camera.x(o.d, o.u)
    val baseY = camera.y(o.d)
    val x0 = cx - halfW
    val top = baseY - height
    val sx = (camera.centerX - cx) / camera.centerX * width * 0.3f
    val sy = height * 0.3f
    val edge = 2f * density

    drawRect(
        Palette.BarrierEdge,
        topLeft = Offset(x0 - width * 0.2f - (if (sx < 0f) -sx else 0f), top - sy - height * 0.2f),
        size = Size(width * 1.4f + abs(sx), height + sy + height * 0.4f),
        alpha = 0.14f,
    )
    val topFace = Path().apply {
        moveTo(x0, top)
        lineTo(x0 + width, top)
        lineTo(x0 + width + sx, top - sy)
        lineTo(x0 + sx, top - sy)
        close()
    }
    drawPath(topFace, Palette.BarrierTop)
    val sideX = if (sx > 0f) x0 + width else x0
    val sideFace = Path().apply {
        moveTo(sideX, top)
        lineTo(sideX + sx, top - sy)
        lineTo(sideX + sx, baseY - sy)
        lineTo(sideX, baseY)
        close()
    }
    drawPath(sideFace, Palette.BarrierSide)
    drawRect(Palette.Barrier, topLeft = Offset(x0, top), size = Size(width, height))

    // Edges and a warning cross on the front face.
    drawPath(topFace, Palette.BarrierEdge, style = Stroke(width = edge, join = StrokeJoin.Round))
    drawPath(sideFace, Palette.BarrierEdge, style = Stroke(width = edge, join = StrokeJoin.Round), alpha = 0.8f)
    drawRect(Palette.BarrierEdge, topLeft = Offset(x0, top), size = Size(width, height), style = Stroke(width = edge))
    drawLine(Palette.BarrierEdge, Offset(x0, top), Offset(x0 + width, baseY), strokeWidth = edge, alpha = 0.7f)
    drawLine(Palette.BarrierEdge, Offset(x0 + width, top), Offset(x0, baseY), strokeWidth = edge, alpha = 0.7f)
}

private fun DrawScope.drawOrb(camera: Camera, o: Obstacle, time: Float) {
    val pulse = 0.92f + 0.08f * sin(time * 6f + o.u * 10f)
    val r = GameEngine.ORB_HALF_U * camera.roadHalf(o.d) * pulse
    val cx = camera.x(o.d, o.u)
    val groundY = camera.y(o.d)
    val center = Offset(cx, groundY - r * 1.3f)

    drawOval(Color.Black, topLeft = Offset(cx - r * 0.9f, groundY - r * 0.22f), size = Size(r * 1.8f, r * 0.44f), alpha = 0.3f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.55f), Color.Transparent), center = center, radius = r * 2.4f),
        radius = r * 2.4f,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Palette.White, Palette.Cyan, Palette.CyanDeep),
            center = center - Offset(r * 0.35f, r * 0.4f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = center,
    )
    drawOval(Palette.White, topLeft = Offset(cx - r * 0.6f, center.y - r * 0.7f), size = Size(r * 0.45f, r * 0.28f), alpha = 0.85f)
}

private fun DrawScope.drawMarble(camera: Camera, engine: GameEngine) {
    val d = GameEngine.MARBLE_D
    val r = GameEngine.MARBLE_HALF_U * camera.roadHalf(d)
    val cx = camera.x(d, engine.marbleU)
    val groundY = camera.y(d)
    val cy = groundY - r * 1.02f
    val center = Offset(cx, cy)

    drawOval(
        color = Color.Black,
        topLeft = Offset(cx - r * 1.05f, groundY - r * 0.24f),
        size = Size(r * 2.1f, r * 0.5f),
        alpha = 0.35f,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Palette.MagentaLight, Palette.Magenta, Palette.MagentaDeep, Palette.MagentaDark),
            center = center - Offset(r * 0.38f, r * 0.42f),
            radius = r * 1.6f,
        ),
        radius = r,
        center = center,
    )

    // Rolling bands: the face we see (the back of the marble) moves upward as it rolls downhill.
    val circle = Path().apply { addOval(Rect(center - Offset(r, r), Size(r * 2f, r * 2f))) }
    clipPath(circle) {
        val period = r * 2f
        val roll = engine.distance * r * 1.6f
        for (i in 0 until 3) {
            val yy = cy - r + (((-roll + i * period / 3f) % period) + period) % period
            val f = ((yy - cy) / r).coerceIn(-1f, 1f)
            val chord = sqrt(1f - f * f)
            drawLine(
                color = Palette.MagentaDark,
                start = Offset(cx - r * chord, yy),
                end = Offset(cx + r * chord, yy),
                strokeWidth = r * 0.16f * chord + density,
                alpha = 0.16f,
            )
        }
    }

    // Rim light on the far side, big soft highlight up top.
    drawArc(
        color = Palette.White,
        startAngle = 15f,
        sweepAngle = 75f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.9f, cy - r * 0.9f),
        size = Size(r * 1.8f, r * 1.8f),
        alpha = 0.22f,
        style = Stroke(width = r * 0.09f, cap = StrokeCap.Round),
    )
    drawOval(
        color = Palette.White,
        topLeft = Offset(cx - r * 0.66f, cy - r * 0.76f),
        size = Size(r * 0.5f, r * 0.3f),
        alpha = 0.9f,
    )
    drawCircle(color = Palette.White, radius = r * 0.07f, center = Offset(cx - r * 0.2f, cy - r * 0.55f), alpha = 0.6f)
}

private fun DrawScope.updateAndDrawParticles(camera: Camera, render: RenderState) {
    for (event in render.pending) {
        when (event) {
            is GameEvent.Collected -> burst(
                render = render,
                x = camera.x(event.d, event.u),
                y = camera.y(event.d) - 14.dp.toPx(),
                count = 16,
                colors = listOf(Palette.Cyan, Palette.White),
                speed = size.width * 0.5f,
                life = 0.55f,
            )
            is GameEvent.Crashed -> burst(
                render = render,
                x = camera.x(GameEngine.MARBLE_D, event.u),
                y = camera.y(GameEngine.MARBLE_D) - 20.dp.toPx(),
                count = 40,
                colors = listOf(Palette.Magenta, Palette.MagentaLight, Palette.White),
                speed = size.width * 0.9f,
                life = 0.9f,
            )
            GameEvent.Boosted -> burst(
                render = render,
                x = size.width / 2f,
                y = size.height * 0.86f,
                count = 24,
                colors = listOf(Palette.Cyan, Palette.White),
                speed = size.width * 0.7f,
                life = 0.6f,
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
