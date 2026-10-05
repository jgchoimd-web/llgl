package com.llgl.turtles.sky

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One sea turtle gliding through the sky. Positions in pixels; [depth] 0.45 (far, small, slow) .. 1 (near). */
class Turtle(
    var x: Float,
    var baseY: Float,
    var depth: Float,
    var dir: Int,
    var speed: Float,
    var phase: Float,
    var bob: Float,
    val hue: Int,
) {
    var y: Float = baseY

    /** Which way the drawing faces, +1 right .. -1 left, passing through 0 edge-on during a turn. */
    var facing: Float = dir.toFloat()

    /** Progress of a barrel roll, 0 when none. */
    var spin: Float = 0f

    /** Progress of a turnaround, 0 when none; [dir] flips halfway. */
    var turn: Float = 0f
    var untilTurn: Float = 15f

    /** Distance covered so far, for tests. */
    var travelled: Float = 0f
}

/** A cloud is a few soft puffs: ([dx], [dy], [radius]) triples in [puffs], relative to [x], [y]. */
class Cloud(var x: Float, val y: Float, val depth: Float, val width: Float, val puffs: FloatArray)

class Sparkle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val hue: Int)

/** Fixed in the sky; [x] and [y] are fractions of the screen. */
class Star(val x: Float, val y: Float, val size: Float, val phase: Float)

/**
 * The sky: turtles that swim across, clouds that drift, stars that wait for the night. Pure
 * Kotlin so the motion is unit-tested; the renderer only draws what is here.
 */
class SkyScene(seed: Int = 7) {
    private val rng = Rng(seed)

    var width = 1080f
        private set
    var height = 2400f
        private set

    val turtles = ArrayList<Turtle>()
    val clouds = ArrayList<Cloud>()
    val sparkles = ArrayList<Sparkle>()
    val stars: List<Star> = List(70) { Star(rng.nextFloat(), rng.nextFloat() * 0.75f, 1f + rng.nextFloat() * 1.6f, rng.nextFloat() * TWO_PI) }

    /** Multiplies every speed; 1 is the designed pace. */
    var speedFactor = 1f

    /** How many turtles are in the sky. */
    var count = 6
        set(value) {
            field = value.coerceIn(1, 14)
            populate()
        }

    /** On the lock screen the clock and notifications own the top of the screen; the turtles keep below. */
    var lockLayout = false

    /** Hour of the day, 0..24, for the sky colours. */
    var hour = 12f

    /** Tilt in g, already smoothed, for the parallax. */
    var tiltX = 0f
    var tiltY = 0f

    /** Seconds since the scene started, for twinkles. */
    var time = 0f
        private set

    /** How many turtles have left one edge and come back from the other, for tests. */
    var wraps = 0
        private set

    val laneTop: Float get() = height * (if (lockLayout) 0.40f else 0.10f)
    val laneBottom: Float get() = height * 0.92f

    init {
        populate()
        buildClouds()
    }

    /** Length of a turtle in pixels, from its depth. */
    fun turtleSize(t: Turtle): Float = 0.11f * width * t.depth

    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val sx = w / width
        val sy = h / height
        width = w.toFloat()
        height = h.toFloat()
        for (t in turtles) {
            t.x *= sx
            t.baseY *= sy
            t.y = t.baseY
            t.speed *= sx
        }
        buildClouds()
    }

    fun step(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        time += dt
        val top = laneTop
        val bottom = laneBottom
        for (t in turtles) {
            val size = turtleSize(t)
            // Stroke and glide: the flippers push, then the turtle coasts, like a sea turtle.
            val strokeRate = 1.1f + 0.5f * t.depth
            t.phase += dt * TWO_PI * strokeRate * (0.7f + 0.3f * speedFactor)
            if (t.phase > TWO_PI) t.phase -= TWO_PI
            val push = 0.65f + 0.35f * max(0f, sin(t.phase))
            var v = t.speed * t.depth * speedFactor * push
            if (t.turn > 0f) {
                v *= 0.35f
                val before = t.turn
                t.turn += dt / TURN_SECONDS
                if (before < 0.5f && t.turn >= 0.5f) t.dir = -t.dir
                if (t.turn >= 1f) t.turn = 0f
            }
            t.facing = when {
                t.turn == 0f -> t.dir.toFloat()
                t.turn < 0.5f -> t.dir * cos(t.turn * PI).toFloat()
                else -> -t.dir * cos(t.turn * PI).toFloat()
            }
            t.x += t.dir * v * dt
            t.travelled += v * dt
            t.bob += dt * (0.6f + 0.3f * t.depth)
            if (t.bob > TWO_PI) t.bob -= TWO_PI
            // Lanes follow the layout; a turtle outside the new band glides into it.
            if (t.baseY < top) t.baseY = min(top, t.baseY + size * 2f * dt)
            if (t.baseY > bottom) t.baseY = max(bottom, t.baseY - size * 2f * dt)
            t.y = t.baseY + sin(t.bob) * 0.35f * size
            // Leaving one edge means coming back from the other in a fresh lane: a new turtle, really.
            val margin = size * 1.6f
            if ((t.x < -margin && t.dir < 0) || (t.x > width + margin && t.dir > 0)) {
                t.x = if (t.dir < 0) width + margin else -margin
                t.depth = rng.range(0.45f, 1f)
                t.baseY = rng.range(top, bottom)
                t.y = t.baseY
                t.speed = baseSpeed()
                t.untilTurn = rng.range(12f, 30f)
                wraps++
            }
            // Now and then a turtle turns around, but not while half off the screen.
            if (t.turn == 0f) {
                t.untilTurn -= dt
                if (t.untilTurn <= 0f && t.x > size && t.x < width - size) {
                    t.turn = 0.0001f
                    t.untilTurn = rng.range(12f, 30f)
                }
            }
            if (t.spin > 0f) {
                t.spin += dt / SPIN_SECONDS
                if (t.spin >= 1f) t.spin = 0f
            }
        }
        for (c in clouds) {
            c.x += (width / 1080f) * 9f * c.depth * (0.6f + 0.4f * speedFactor) * dt
            if (c.x > width + c.width) c.x = -c.width
        }
        var i = 0
        while (i < sparkles.size) {
            val s = sparkles[i]
            s.life -= dt
            if (s.life <= 0f) {
                sparkles.removeAt(i)
                continue
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vy -= (height / 2400f) * 30f * dt
            s.vx *= 1f - 0.9f * dt
            i++
        }
    }

    /** A touch: the nearest turtle within reach does a barrel roll and scatters sparkles. False when none is near. */
    fun tap(px: Float, py: Float): Boolean {
        var best: Turtle? = null
        var bestD = Float.MAX_VALUE
        for (t in turtles) {
            val reach = turtleSize(t) * 1.3f + width * 0.03f
            val dx = t.x - px
            val dy = t.y - py
            val d = sqrt(dx * dx + dy * dy)
            if (d < reach && d < bestD) {
                best = t
                bestD = d
            }
        }
        val t = best ?: return false
        if (t.spin == 0f) t.spin = 0.0001f
        val size = turtleSize(t)
        for (k in 0 until 14) {
            val a = rng.range(0f, TWO_PI)
            val sp = rng.range(0.6f, 1.6f) * size
            sparkles += Sparkle(t.x, t.y, cos(a) * sp, sin(a) * sp - size * 0.4f, rng.range(0.6f, 1.1f), t.hue)
        }
        return true
    }

    private fun baseSpeed(): Float = rng.range(42f, 70f) * (width / 1080f)

    private fun populate() {
        while (turtles.size > count) turtles.removeAt(turtles.size - 1)
        while (turtles.size < count) {
            turtles += Turtle(
                x = rng.range(0f, width),
                baseY = rng.range(laneTop, laneBottom),
                depth = rng.range(0.45f, 1f),
                dir = if (rng.nextFloat() < 0.5f) -1 else 1,
                speed = baseSpeed(),
                phase = rng.range(0f, TWO_PI),
                bob = rng.range(0f, TWO_PI),
                hue = rng.nextInt(4),
            ).also { it.untilTurn = rng.range(6f, 24f) }
        }
    }

    private fun buildClouds() {
        clouds.clear()
        for (k in 0 until 7) {
            val depth = rng.range(0.3f, 0.85f)
            val w = width * rng.range(0.22f, 0.4f) * (0.6f + 0.4f * depth)
            val puffs = FloatArray(4 * 3)
            for (p in 0 until 4) {
                puffs[p * 3] = w * (p / 3f - 0.5f) * 0.9f + rng.range(-0.05f, 0.05f) * w
                puffs[p * 3 + 1] = rng.range(-0.08f, 0.08f) * w
                puffs[p * 3 + 2] = w * rng.range(0.16f, 0.26f) * (if (p == 1 || p == 2) 1.25f else 1f)
            }
            clouds += Cloud(rng.range(-w, width), height * rng.range(0.05f, 0.75f), depth, w, puffs)
        }
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
        const val TURN_SECONDS = 1.3f
        const val SPIN_SECONDS = 0.9f
    }
}
