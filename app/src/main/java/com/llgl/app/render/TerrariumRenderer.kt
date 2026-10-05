package com.llgl.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.llgl.app.world.Beetle
import com.llgl.app.world.Firefly
import com.llgl.app.world.Isopod
import com.llgl.app.world.Particles
import com.llgl.app.world.Plant
import com.llgl.app.world.Rng
import com.llgl.app.world.Snail
import com.llgl.app.world.Terrarium
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the box onto a small bitmap, one world unit per pixel, with anti-aliasing off so the
 * view can blow it up into chunky pixels. Nothing is allocated per frame: point buffers are
 * reused and plant details are cached.
 */
class TerrariumRenderer(private val world: Terrarium, seed: Long) {

    private class Flash(val x: Float, val y: Float, val wall: Boolean, val strength: Float, val start: Float)
    private class Ripple(val x: Float, val y: Float, val onWater: Boolean, val start: Float)

    private val w = world.width
    private val h = world.height
    private val soil: Bitmap = renderSoil(seed)
    private val capacity = world.particles.capacity
    private val sandBuffers = Array(3) { FloatArray(capacity * 2) }
    private val sandCounts = IntArray(3)
    private val waterBuffer = FloatArray(capacity * 2)
    private val glintBuffer = FloatArray(capacity * 2)
    private val flashes = ArrayList<Flash>()
    private val ripples = ArrayList<Ripple>()
    private val mossDots = HashMap<Plant, Pair<FloatArray, FloatArray>>()
    private val rect = RectF()

    private fun fill(color: Long) = Paint().apply {
        this.color = color.toInt()
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    private fun stroke(color: Long, width: Float = 1f, cap: Paint.Cap = Paint.Cap.SQUARE) = Paint().apply {
        this.color = color.toInt()
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = cap
        isAntiAlias = false
    }

    private val sandPaints = arrayOf(stroke(0xFFD9C08A), stroke(0xFFC9AE74), stroke(0xFFE6D2A2))
    private val waterPaint = stroke(0x963B8BD6, 2.6f, Paint.Cap.ROUND)
    private val glintPaint = stroke(0x78BFE6FF)
    private val marbleFill = arrayOf(fill(0xFF8F8A82), fill(0xFF4FA3E6), fill(0xFFE0A040))
    private val marbleHighlight = arrayOf(fill(0xFFB8B3AA), fill(0xFFD8F0FF), fill(0xFFFFE4B0))
    private val marbleShadow = fill(0x55000000)
    private val isopodBody = fill(0xFF6B6F75)
    private val isopodBack = fill(0xFF7E838A)
    private val isopodSegment = stroke(0xFF4B4F55)
    private val isopodLeg = stroke(0xFF3E4247)
    private val snailShell = fill(0xFFA97C50)
    private val snailSpiral = stroke(0xFF7A5230)
    private val snailBody = fill(0xFFC9A27A)
    private val snailFeeler = stroke(0xFF8E7355)
    private val beetleBody = fill(0xFF2B2B30)
    private val beetleHead = fill(0xFF45454D)
    private val beetleLeg = stroke(0xFF1D1D20)
    private val fireflyGlow = fill(0xFFFFE977)
    private val fireflyCore = fill(0xFFFFF7A8)
    private val moss = fill(0xFF3F7A3A)
    private val mossDark = stroke(0xFF2E5C2B)
    private val mossLight = stroke(0xFF5A9B52)
    private val fern = stroke(0xFF4B8E3E)
    private val leaflet = stroke(0xFF6DBA5A)
    private val mushroomStem = fill(0xFFE3D5B8)
    private val mushroomCap = fill(0xFFB8553A)
    private val mushroomSpot = fill(0xFFF0D9C0)
    private val sprout = stroke(0xFF7CC66B)
    private val trailPaint = stroke(0xFFC9D6D0)
    private val crumbPaint = fill(0xFFD9B27A)
    private val glassBand = stroke(0x5A6E7A86, 2f)
    private val glassLine = stroke(0x46FFFFFF)
    private val glassCorner = fill(0x80FFFFFF)
    private val flashPaint = stroke(0xFFFFFFFF, 2f)
    private val glowPaint = fill(0xFFFFFFFF)
    private val ripplePaint = stroke(0xFFFFFFFF)

    fun addImpact(x: Float, y: Float, wall: Boolean, strength: Float) {
        flashes += Flash(x, y, wall, strength, world.time)
        if (flashes.size > 12) flashes.removeAt(0)
    }

    fun addRipple(x: Float, y: Float, onWater: Boolean) {
        ripples += Ripple(x, y, onWater, world.time)
        if (ripples.size > 8) ripples.removeAt(0)
    }

    fun draw(canvas: Canvas, nightness: Float) {
        val time = world.time
        canvas.drawBitmap(soil, 0f, 0f, null)
        drawWater(canvas)
        drawSand(canvas)
        drawTrails(canvas, time)
        drawCrumbs(canvas)
        drawPlants(canvas, time, mossOnly = true)
        drawMarbles(canvas)
        drawPlants(canvas, time, mossOnly = false)
        drawCreatures(canvas)
        if (nightness > 0.01f) canvas.drawColor(Color.argb((nightness * 150f).toInt(), 8, 14, 40))
        drawFireflies(canvas)
        drawRipples(canvas, time)
        drawFlashes(canvas, time)
        drawGlass(canvas)
    }

    // ---------------------------------------------------------------------------------------
    // Loose contents
    // ---------------------------------------------------------------------------------------

    private fun snap(v: Float): Float = v.toInt() + 0.5f

    private fun drawSand(canvas: Canvas) {
        val p = world.particles
        sandCounts.fill(0)
        for (i in 0 until p.count) {
            if (p.type[i] != Particles.SAND) continue
            val s = p.shade[i].toInt().coerceIn(0, 2)
            val n = sandCounts[s]
            sandBuffers[s][n * 2] = snap(p.x[i])
            sandBuffers[s][n * 2 + 1] = snap(p.y[i])
            sandCounts[s] = n + 1
        }
        for (s in 0 until 3) if (sandCounts[s] > 0) canvas.drawPoints(sandBuffers[s], 0, sandCounts[s] * 2, sandPaints[s])
    }

    private fun drawWater(canvas: Canvas) {
        val p = world.particles
        var n = 0
        var g = 0
        for (i in 0 until p.count) {
            if (p.type[i] != Particles.WATER) continue
            waterBuffer[n * 2] = snap(p.x[i])
            waterBuffer[n * 2 + 1] = snap(p.y[i])
            n++
            if (i % 6 == 0) {
                glintBuffer[g * 2] = snap(p.x[i]) - 1f
                glintBuffer[g * 2 + 1] = snap(p.y[i]) - 1f
                g++
            }
        }
        if (n > 0) canvas.drawPoints(waterBuffer, 0, n * 2, waterPaint)
        if (g > 0) canvas.drawPoints(glintBuffer, 0, g * 2, glintPaint)
    }

    private fun drawMarbles(canvas: Canvas) {
        val p = world.particles
        for (i in 0 until p.count) {
            val t = p.type[i]
            if (t != Particles.MARBLE && t != Particles.BALL) continue
            val x = p.x[i]
            val y = p.y[i]
            val r = p.radius[i]
            canvas.drawCircle(x + 1f, y + 1f, r, marbleShadow)
            if (t == Particles.BALL) {
                canvas.drawCircle(x, y, r, isopodBody)
                canvas.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.35f, isopodBack)
                continue
            }
            val s = p.shade[i].toInt().coerceIn(0, 2)
            canvas.drawCircle(x, y, r, marbleFill[s])
            canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.3f, marbleHighlight[s])
        }
    }

    private fun drawTrails(canvas: Canvas, time: Float) {
        for (c in world.creatures) {
            if (c !is Snail) continue
            val trail = c.trail
            for (k in 0 until trail.count) {
                val idx = trail.index(k)
                val age = time - trail.time[idx]
                if (age > Snail.TRAIL_FADE) continue
                trailPaint.alpha = (110f * (1f - age / Snail.TRAIL_FADE)).toInt()
                canvas.drawPoint(snap(trail.x[idx]), snap(trail.y[idx]), trailPaint)
            }
        }
    }

    private fun drawCrumbs(canvas: Canvas) {
        for (c in world.crumbs) {
            val s = 1f + 1.5f * c.amount.coerceIn(0f, 1f)
            canvas.drawRect(c.x - s, c.y - s * 0.7f, c.x + s, c.y + s * 0.7f, crumbPaint)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Plants
    // ---------------------------------------------------------------------------------------

    private fun drawPlants(canvas: Canvas, time: Float, mossOnly: Boolean) {
        val swayX = world.gravityX * 2f
        val swayY = world.gravityY * 2f
        for (plant in world.plants) {
            if ((plant.kind == Plant.Kind.MOSS) != mossOnly) continue
            val quake = world.sway
            val sx = swayX + sin(time * 22f + plant.seed) * quake * 2f
            val sy = swayY + cos(time * 25f + plant.seed) * quake * 2f
            when (plant.kind) {
                Plant.Kind.MOSS -> drawMoss(canvas, plant)
                Plant.Kind.FERN -> drawFern(canvas, plant, sx, sy)
                Plant.Kind.MUSHROOM -> drawMushroom(canvas, plant, sx, sy)
                Plant.Kind.SPROUT -> drawSprout(canvas, plant, sx, sy)
            }
        }
    }

    private fun drawMoss(canvas: Canvas, plant: Plant) {
        canvas.drawCircle(plant.x, plant.y, plant.size, moss)
        val (dark, light) = mossDots.getOrPut(plant) {
            val rng = Rng(plant.seed.toLong() + 1)
            val n = (plant.size * plant.size * 0.25f).toInt().coerceIn(6, 120)
            val a = FloatArray(n * 2)
            val b = FloatArray(n * 2)
            fun fillDots(out: FloatArray) {
                var k = 0
                while (k < n) {
                    val u = rng.range(-1f, 1f)
                    val v = rng.range(-1f, 1f)
                    if (u * u + v * v > 0.9f) continue
                    out[k * 2] = snap(plant.x + u * plant.size)
                    out[k * 2 + 1] = snap(plant.y + v * plant.size)
                    k++
                }
            }
            fillDots(a)
            fillDots(b)
            a to b
        }
        canvas.drawPoints(dark, mossDark)
        canvas.drawPoints(light, mossLight)
    }

    private fun drawFern(canvas: Canvas, plant: Plant, sx: Float, sy: Float) {
        val fronds = 5 + plant.seed % 3
        val base = plant.seed * 0.37f
        for (k in 0 until fronds) {
            val angle = base + k * (2 * PI.toFloat()) / fronds
            val length = plant.size * (0.75f + 0.25f * ((plant.seed shr k) and 3) / 3f)
            val tx = plant.x + cos(angle) * length + sx
            val ty = plant.y + sin(angle) * length + sy
            canvas.drawLine(plant.x, plant.y, tx, ty, fern)
            val steps = (length / 3f).toInt()
            val px = -sin(angle)
            val py = cos(angle)
            for (s in 1 until steps) {
                val f = s / steps.toFloat()
                val lx = plant.x + (tx - plant.x) * f
                val ly = plant.y + (ty - plant.y) * f
                val side = if (s % 2 == 0) 1f else -1f
                val len = 2.2f * (1f - f * 0.6f)
                canvas.drawLine(lx, ly, lx + px * side * len, ly + py * side * len, leaflet)
            }
        }
    }

    private fun drawMushroom(canvas: Canvas, plant: Plant, sx: Float, sy: Float) {
        val top = plant.y - plant.size * 1.4f
        canvas.drawRect(plant.x - 0.9f, top, plant.x + 0.9f, plant.y, mushroomStem)
        val cx = plant.x + sx * 0.5f
        val cy = top + sy * 0.5f
        canvas.drawCircle(cx, cy, plant.size, mushroomCap)
        canvas.drawRect(cx - plant.size * 0.5f, cy - plant.size * 0.4f, cx - plant.size * 0.5f + 1f, cy - plant.size * 0.4f + 1f, mushroomSpot)
        canvas.drawRect(cx + plant.size * 0.2f, cy + plant.size * 0.1f, cx + plant.size * 0.2f + 1f, cy + plant.size * 0.1f + 1f, mushroomSpot)
    }

    private fun drawSprout(canvas: Canvas, plant: Plant, sx: Float, sy: Float) {
        canvas.drawLine(plant.x, plant.y, plant.x + sx * 0.3f, plant.y - 3f + sy * 0.3f, sprout)
        canvas.drawLine(plant.x + sx * 0.3f, plant.y - 2f, plant.x - 2.5f + sx * 0.5f, plant.y - 4f + sy * 0.5f, sprout)
        canvas.drawLine(plant.x + sx * 0.3f, plant.y - 2f, plant.x + 2.5f + sx * 0.5f, plant.y - 4.5f + sy * 0.5f, sprout)
    }

    // ---------------------------------------------------------------------------------------
    // Creatures
    // ---------------------------------------------------------------------------------------

    private fun drawCreatures(canvas: Canvas) {
        for (c in world.creatures) {
            when (c) {
                is Isopod -> if (c.state != Isopod.State.CURLED) drawIsopod(canvas, c)
                is Snail -> drawSnail(canvas, c)
                is Beetle -> drawBeetle(canvas, c)
                is Firefly -> Unit
            }
        }
    }

    private fun drawIsopod(canvas: Canvas, c: Isopod) {
        canvas.save()
        canvas.translate(c.x, c.y)
        canvas.rotate(Math.toDegrees(c.heading.toDouble()).toFloat())
        val swing = if (c.state == Isopod.State.WALK) sin(c.legPhase) * 1.2f else 0f
        for (k in 0 until 3) {
            val lx = -2.5f + k * 2.5f
            val s = if (k % 2 == 0) swing else -swing
            canvas.drawLine(lx, -2f, lx + s, -4f, isopodLeg)
            canvas.drawLine(lx, 2f, lx - s, 4f, isopodLeg)
        }
        rect.set(-4f, -2.5f, 4f, 2.5f)
        canvas.drawOval(rect, isopodBody)
        rect.set(-3f, -1.5f, 2.5f, 1.5f)
        canvas.drawOval(rect, isopodBack)
        for (k in 0 until 3) {
            val lx = -1.5f + k * 2f
            canvas.drawLine(lx, -2.5f, lx, 2.5f, isopodSegment)
        }
        canvas.restore()
    }

    private fun drawSnail(canvas: Canvas, c: Snail) {
        canvas.save()
        canvas.translate(c.x, c.y)
        canvas.rotate(Math.toDegrees(c.heading.toDouble()).toFloat())
        if (!c.hiding) {
            rect.set(-2f, -1.5f, 4.5f, 1.5f)
            canvas.drawOval(rect, snailBody)
            canvas.drawLine(4f, -1f, 6f, -2.5f, snailFeeler)
            canvas.drawLine(4f, 1f, 6f, 2.5f, snailFeeler)
        }
        canvas.drawCircle(-1f, 0f, 3.2f, snailShell)
        rect.set(-3f, -2f, 1f, 2f)
        canvas.drawArc(rect, 20f, 250f, false, snailSpiral)
        rect.set(-2f, -1f, 0f, 1f)
        canvas.drawArc(rect, 0f, 300f, false, snailSpiral)
        canvas.restore()
    }

    private fun drawBeetle(canvas: Canvas, c: Beetle) {
        canvas.save()
        canvas.translate(c.x, c.y)
        canvas.rotate(Math.toDegrees(c.heading.toDouble()).toFloat())
        val swing = if (c.dashing) sin(c.legPhase) * 1.5f else 0.6f
        for (k in 0 until 3) {
            val lx = -2.5f + k * 2.2f
            val s = if (k % 2 == 0) swing else -swing
            canvas.drawLine(lx, -1.5f, lx + s, -3.8f, beetleLeg)
            canvas.drawLine(lx, 1.5f, lx - s, 3.8f, beetleLeg)
        }
        rect.set(-3.5f, -2f, 3.5f, 2f)
        canvas.drawOval(rect, beetleBody)
        canvas.drawCircle(4f, 0f, 1.5f, beetleHead)
        canvas.drawLine(0f, -1.8f, 0f, 1.8f, beetleLeg)
        canvas.restore()
    }

    private fun drawFireflies(canvas: Canvas) {
        for (c in world.creatures) {
            if (c !is Firefly) continue
            val a = c.alpha
            if (a <= 0f) continue
            fireflyGlow.alpha = (60f * c.glow * a).toInt()
            canvas.drawCircle(c.x, c.y, 5f, fireflyGlow)
            fireflyGlow.alpha = (110f * c.glow * a).toInt()
            canvas.drawCircle(c.x, c.y, 3f, fireflyGlow)
            fireflyCore.alpha = (255f * (0.25f + 0.75f * c.glow) * a).toInt()
            canvas.drawRect(snap(c.x) - 0.5f, snap(c.y) - 0.5f, snap(c.x) + 0.5f, snap(c.y) + 0.5f, fireflyCore)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Glass and effects
    // ---------------------------------------------------------------------------------------

    private fun drawRipples(canvas: Canvas, time: Float) {
        val it = ripples.iterator()
        while (it.hasNext()) {
            val r = it.next()
            val age = time - r.start
            if (age > RIPPLE_LIFE) {
                it.remove()
                continue
            }
            val k = age / RIPPLE_LIFE
            ripplePaint.color = if (r.onWater) 0xFFBFE6FF.toInt() else 0xFFE6D2A2.toInt()
            ripplePaint.alpha = (160f * (1f - k)).toInt()
            canvas.drawCircle(r.x, r.y, 2f + k * 50f, ripplePaint)
            if (r.onWater) canvas.drawCircle(r.x, r.y, 1f + k * 28f, ripplePaint)
        }
    }

    private fun drawFlashes(canvas: Canvas, time: Float) {
        val it = flashes.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val age = time - f.start
            if (age > FLASH_LIFE) {
                it.remove()
                continue
            }
            val k = 1f - age / FLASH_LIFE
            glowPaint.alpha = (120f * k * f.strength).toInt()
            canvas.drawCircle(f.x, f.y, 3f + age * 30f, glowPaint)
            if (!f.wall) continue
            flashPaint.alpha = (255f * k * min(1f, 0.4f + f.strength)).toInt()
            val half = 12f + age * 60f
            val dl = f.x
            val dr = w - f.x
            val dtp = f.y
            val db = h - f.y
            val m = minOf(dl, dr, dtp, db)
            when (m) {
                dl -> canvas.drawLine(1f, f.y - half, 1f, f.y + half, flashPaint)
                dr -> canvas.drawLine(w - 1f, f.y - half, w - 1f, f.y + half, flashPaint)
                dtp -> canvas.drawLine(f.x - half, 1f, f.x + half, 1f, flashPaint)
                else -> canvas.drawLine(f.x - half, h - 1f, f.x + half, h - 1f, flashPaint)
            }
        }
    }

    private fun drawGlass(canvas: Canvas) {
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, glassBand)
        canvas.drawRect(2.5f, 2.5f, w - 2.5f, h - 2.5f, glassLine)
        val c = 3f
        canvas.drawRect(0f, 0f, c, c, glassCorner)
        canvas.drawRect(w - c, 0f, w, c, glassCorner)
        canvas.drawRect(0f, h - c, c, h, glassCorner)
        canvas.drawRect(w - c, h - c, w, h, glassCorner)
    }

    private fun renderSoil(seed: Long): Bitmap {
        val width = w.toInt()
        val height = h.toInt()
        val pixels = IntArray(width * height)
        val rng = Rng(seed xor 0x5011)
        val tones = intArrayOf(0xFF3B2A1E.toInt(), 0xFF352419.toInt(), 0xFF433022.toInt(), 0xFF2F2016.toInt())
        for (i in pixels.indices) {
            val r = rng.nextFloat()
            pixels[i] = when {
                r < 0.012f -> 0xFF5A4634.toInt()
                r < 0.016f -> 0xFF6B5540.toInt()
                else -> tones[rng.nextInt(4)]
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val FLASH_LIFE = 0.35f
        const val RIPPLE_LIFE = 0.5f
    }
}
