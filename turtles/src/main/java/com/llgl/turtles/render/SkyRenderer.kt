package com.llgl.turtles.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.llgl.turtles.sky.Palette
import com.llgl.turtles.sky.SkyScene
import com.llgl.turtles.sky.Turtle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Draws a [SkyScene] on a canvas: gradient sky, stars and moon, clouds, turtles by depth, sparkles. */
class SkyRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skyPaint = Paint()
    private var skyTop = 0
    private var skyBottom = 0
    private var skyHeight = 0f
    private val rect = RectF()
    private val path = Path()
    private val order = ArrayList<Turtle>()

    private class Shell(val rim: Int, val shell: Int, val scute: Int, val skin: Int)

    private val shells = arrayOf(
        Shell(0xFF2E7A55.toInt(), 0xFF3C9D6E.toInt(), 0xFF8CDBB0.toInt(), 0xFF7FC08A.toInt()),
        Shell(0xFF2F7C9E.toInt(), 0xFF4DA3C9.toInt(), 0xFFA6E2F5.toInt(), 0xFF86C9D9.toInt()),
        Shell(0xFF9E7C2F.toInt(), 0xFFC9A24D.toInt(), 0xFFF5E2A6.toInt(), 0xFFD9C07A.toInt()),
        Shell(0xFF6B4FA0.toInt(), 0xFF9C7BD1.toInt(), 0xFFD9C8F5.toInt(), 0xFFB9A6E0.toInt()),
    )

    /** [zoom] 0..1 is the launcher's "zoomed out" amount; the sky pulls back a little. */
    fun draw(canvas: Canvas, scene: SkyScene, zoom: Float) {
        val w = scene.width
        val h = scene.height
        val colors = Palette.at(scene.hour)
        if (colors.top != skyTop || colors.bottom != skyBottom || skyHeight != h) {
            skyTop = colors.top
            skyBottom = colors.bottom
            skyHeight = h
            skyPaint.shader = LinearGradient(0f, 0f, 0f, h, skyTop, skyBottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        canvas.save()
        if (zoom > 0f) {
            val s = 1f - 0.06f * zoom.coerceIn(0f, 1f)
            canvas.scale(s, s, w / 2f, h / 2f)
        }
        val px = -scene.tiltX * w * 0.03f
        val py = scene.tiltY * h * 0.015f

        if (colors.stars > 0.01f) drawStars(canvas, scene, colors.stars, px * 0.25f, py * 0.25f)
        if (colors.night > 0.2f) drawMoon(canvas, scene, colors.night, px * 0.2f, py * 0.2f)

        for (c in scene.clouds) if (c.depth < 0.6f) drawCloud(canvas, c.x + px * c.depth, c.y + py * c.depth, c.puffs, c.depth, colors.night)

        order.clear()
        order.addAll(scene.turtles)
        order.sortBy { it.depth }
        for (t in order) drawTurtle(canvas, t, scene.turtleSize(t), t.x + px * t.depth, t.y + py * t.depth, colors.night)

        for (c in scene.clouds) if (c.depth >= 0.6f) drawCloud(canvas, c.x + px * c.depth, c.y + py * c.depth, c.puffs, c.depth, colors.night)

        for (s in scene.sparkles) {
            val a = (s.life / 1.1f).coerceIn(0f, 1f)
            fill.color = withAlpha(Palette.lerpColor(shells[s.hue % shells.size].scute, 0xFFFFFFFF.toInt(), 0.5f), (255 * a).toInt())
            val r = scene.width * 0.004f * (0.6f + a)
            canvas.drawCircle(s.x, s.y, r, fill)
        }
        canvas.restore()
    }

    private fun drawStars(canvas: Canvas, scene: SkyScene, strength: Float, ox: Float, oy: Float) {
        val w = scene.width
        val h = scene.height
        for (s in scene.stars) {
            val twinkle = 0.55f + 0.45f * sin(scene.time * 1.7f + s.phase)
            fill.color = withAlpha(0xFFFFF4C2.toInt(), (255 * strength * twinkle).toInt())
            canvas.drawCircle(s.x * w + ox, s.y * h + oy, s.size * w / 1080f * 1.6f, fill)
        }
    }

    private fun drawMoon(canvas: Canvas, scene: SkyScene, night: Float, ox: Float, oy: Float) {
        val w = scene.width
        val h = scene.height
        val cx = w * 0.80f + ox
        val cy = h * 0.16f + oy
        val r = w * 0.045f
        val a = ((night - 0.2f) / 0.8f).coerceIn(0f, 1f)
        fill.color = withAlpha(0xFFFFF1C8.toInt(), (40 * a).toInt())
        canvas.drawCircle(cx, cy, r * 1.9f, fill)
        fill.color = withAlpha(0xFFFFF6DA.toInt(), (235 * a).toInt())
        canvas.drawCircle(cx, cy, r, fill)
        // A bite out of the moon, in sky colour, makes a crescent.
        fill.color = withAlpha(Palette.at(scene.hour).top, (235 * a).toInt())
        canvas.drawCircle(cx + r * 0.42f, cy - r * 0.18f, r * 0.82f, fill)
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, puffs: FloatArray, depth: Float, night: Float) {
        val tint = Palette.lerpColor(0xFFFFFFFF.toInt(), 0xFF5F6BA6.toInt(), night)
        val alpha = (255 * (0.28f + 0.22f * depth) * (1f - 0.35f * night)).toInt()
        fill.color = withAlpha(tint, alpha)
        var i = 0
        while (i < puffs.size) {
            val r = puffs[i + 2]
            rect.set(x + puffs[i] - r * 1.3f, y + puffs[i + 1] - r, x + puffs[i] + r * 1.3f, y + puffs[i + 1] + r)
            canvas.drawOval(rect, fill)
            i += 3
        }
    }

    private fun drawTurtle(canvas: Canvas, t: Turtle, size: Float, x: Float, y: Float, night: Float) {
        val c = shells[t.hue % shells.size]
        val dim = 1f - 0.35f * night
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(sin(t.bob) * 5f * t.facing)
        val roll = if (t.spin > 0f) cos(t.spin * 2.0 * PI).toFloat() else 1f
        canvas.scale(if (t.facing == 0f) 0.02f else t.facing, if (roll == 0f) 0.02f else roll)
        val belly = roll < 0f
        val stroke = sin(t.phase)
        val skin = dimmed(c.skin, dim)

        // Back flippers, then front ones, then tail and head; the shell goes on top.
        flipper(canvas, -0.30f * size, -0.20f * size, 0.30f * size, 0.11f * size, -150f + stroke * 18f, skin)
        flipper(canvas, -0.30f * size, 0.20f * size, 0.30f * size, 0.11f * size, 150f - stroke * 18f, skin)
        flipper(canvas, 0.12f * size, -0.26f * size, 0.46f * size, 0.14f * size, -38f - stroke * 32f, skin)
        flipper(canvas, 0.12f * size, 0.26f * size, 0.46f * size, 0.14f * size, 38f + stroke * 32f, skin)
        fill.color = skin
        path.reset()
        path.moveTo(-0.42f * size, -0.05f * size)
        path.lineTo(-0.58f * size, 0f)
        path.lineTo(-0.42f * size, 0.05f * size)
        path.close()
        canvas.drawPath(path, fill)
        rect.set(0.33f * size, -0.12f * size, 0.67f * size, 0.12f * size)
        canvas.drawOval(rect, fill)
        fill.color = 0xFF1B2550.toInt()
        canvas.drawCircle(0.57f * size, -0.035f * size, 0.026f * size, fill)

        fill.color = dimmed(if (belly) 0xFFB9A26A.toInt() else c.rim, dim)
        rect.set(-0.44f * size, -0.33f * size, 0.44f * size, 0.33f * size)
        canvas.drawOval(rect, fill)
        fill.color = dimmed(if (belly) 0xFFE8D7A4.toInt() else c.shell, dim)
        rect.set(-0.40f * size, -0.29f * size, 0.40f * size, 0.29f * size)
        canvas.drawOval(rect, fill)
        if (!belly) {
            fill.color = dimmed(c.scute, dim)
            rect.set(-0.13f * size, -0.11f * size, 0.13f * size, 0.11f * size)
            canvas.drawOval(rect, fill)
            for (k in 0 until 6) {
                val a = k * PI / 3.0
                val sx = (cos(a) * 0.27 * size).toFloat()
                val sy = (sin(a) * 0.19 * size).toFloat()
                rect.set(sx - 0.06f * size, sy - 0.045f * size, sx + 0.06f * size, sy + 0.045f * size)
                canvas.drawOval(rect, fill)
            }
        }
        canvas.restore()
    }

    private fun flipper(canvas: Canvas, x: Float, y: Float, length: Float, thickness: Float, angle: Float, color: Int) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        fill.color = color
        rect.set(0f, -thickness / 2f, length, thickness / 2f)
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, fill)
        canvas.restore()
    }

    private fun dimmed(color: Int, factor: Float): Int = Palette.lerpColor(0xFF000000.toInt(), color, factor)

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
