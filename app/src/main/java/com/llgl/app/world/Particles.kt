package com.llgl.app.world

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Everything loose in the box: sand grains, water droplets, marbles and curled-up isopods, all in one
 * position-based solver. Each step integrates velocities under the tilted gravity, keeps everything
 * inside the walls, lets the thumb and plant bases push things aside, then relaxes overlapping pairs
 * through a spatial hash. Sand and water take their velocity from how far they actually moved (so
 * piles settle and puddles slosh); marbles keep a real velocity so they bounce and click.
 *
 * Units are world pixels and seconds; gravity is in g (1 = the phone held vertical).
 */
class Particles(val capacity: Int, val width: Float, val height: Float) {
    var count = 0
        private set

    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val radius = FloatArray(capacity)
    val type = ByteArray(capacity)

    /** A per-particle colour variation for the renderer. */
    val shade = ByteArray(capacity)

    private val px = FloatArray(capacity)
    private val py = FloatArray(capacity)
    private val lastHit = FloatArray(capacity)

    private val cellSize = 4f
    private val cols = (width / cellSize).toInt() + 2
    private val rows = (height / cellSize).toInt() + 2
    private val heads = IntArray(cols * rows)
    private val next = IntArray(capacity)

    /** Marble impacts found in the last step. */
    val impacts = ArrayList<Event.Impact>()

    /** Relaxation passes per step; the view lowers it to 1 on a phone that cannot keep up. */
    var iterations = ITERATIONS

    /** Mean speed of the sand and of the water in the last step, for the flowing sounds. */
    var sandMotion = 0f
        private set
    var waterMotion = 0f
        private set

    fun add(type: Byte, x: Float, y: Float, radius: Float, shade: Int = 0): Int {
        require(count < capacity) { "particle capacity exceeded" }
        val i = count++
        this.type[i] = type
        this.x[i] = x
        this.y[i] = y
        this.radius[i] = radius
        this.shade[i] = shade.toByte()
        vx[i] = 0f
        vy[i] = 0f
        px[i] = x
        py[i] = y
        lastHit[i] = -1f
        return i
    }

    /** Removes particle [i] by moving the last particle into its slot. */
    fun remove(i: Int) {
        val last = count - 1
        if (i != last) {
            type[i] = type[last]
            x[i] = x[last]
            y[i] = y[last]
            vx[i] = vx[last]
            vy[i] = vy[last]
            radius[i] = radius[last]
            shade[i] = shade[last]
            px[i] = px[last]
            py[i] = py[last]
            lastHit[i] = lastHit[last]
        }
        count = last
    }

    fun clear() {
        count = 0
    }

    fun isBig(i: Int): Boolean = type[i] == MARBLE || type[i] == BALL

    fun step(dt: Float, gx: Float, gy: Float, time: Float, finger: Finger?, obstacles: List<Circle>) {
        impacts.clear()
        val gMag = sqrt(gx * gx + gy * gy)
        integrate(dt, gx, gy, gMag)
        walls(time)
        if (finger != null) pushFinger(finger)
        if (obstacles.isNotEmpty()) pushObstacles(obstacles)
        buildHash()
        repeat(iterations) { solvePairs(time) }
        finish(dt, gMag)
    }

    /** Gives every particle a random kick, for an earthquake. */
    fun jolt(rng: Rng, strength: Float) {
        for (i in 0 until count) {
            vx[i] += rng.range(-1f, 1f) * strength
            vy[i] += rng.range(-1f, 1f) * strength
        }
    }

    private fun integrate(dt: Float, gx: Float, gy: Float, gMag: Float) {
        for (i in 0 until count) {
            val t = type[i]
            val gk: Float
            val damping: Float
            when (t) {
                SAND -> {
                    gk = 0.55f
                    damping = 0.88f
                }
                WATER -> {
                    gk = 0.85f
                    damping = 0.975f
                }
                MARBLE -> {
                    gk = 1f
                    damping = 0.992f
                }
                else -> {
                    gk = 0.9f
                    damping = 0.985f
                }
            }
            var nvx = (vx[i] + gx * G * gk * dt) * damping
            var nvy = (vy[i] + gy * G * gk * dt) * damping
            if (t == SAND && gMag < SAND_STATIC_G) {
                nvx *= 0.6f
                nvy *= 0.6f
            }
            vx[i] = nvx
            vy[i] = nvy
            px[i] = x[i]
            py[i] = y[i]
            x[i] += nvx * dt
            y[i] += nvy * dt
        }
    }

    private fun restitution(t: Byte): Float = when (t) {
        SAND -> 0.05f
        WATER -> 0.2f
        MARBLE -> 0.45f
        else -> 0.35f
    }

    private fun walls(time: Float) {
        for (i in 0 until count) {
            val r = radius[i]
            val t = type[i]
            val e = restitution(t)
            val big = t == MARBLE || t == BALL
            if (x[i] < r) {
                x[i] = r
                if (vx[i] < 0f) {
                    hitWall(i, -vx[i], time, big)
                    vx[i] = -vx[i] * e
                }
            } else if (x[i] > width - r) {
                x[i] = width - r
                if (vx[i] > 0f) {
                    hitWall(i, vx[i], time, big)
                    vx[i] = -vx[i] * e
                }
            }
            if (y[i] < r) {
                y[i] = r
                if (vy[i] < 0f) {
                    hitWall(i, -vy[i], time, big)
                    vy[i] = -vy[i] * e
                }
            } else if (y[i] > height - r) {
                y[i] = height - r
                if (vy[i] > 0f) {
                    hitWall(i, vy[i], time, big)
                    vy[i] = -vy[i] * e
                }
            }
        }
    }

    private fun hitWall(i: Int, speed: Float, time: Float, big: Boolean) {
        if (!big || speed < HIT_SPEED || time - lastHit[i] < HIT_GAP) return
        lastHit[i] = time
        impacts += Event.Impact(x[i], y[i], speed, radius[i], wall = true)
    }

    private fun pushFinger(f: Finger) {
        for (i in 0 until count) {
            val dx = x[i] - f.x
            val dy = y[i] - f.y
            val minD = f.radius + radius[i]
            val d2 = dx * dx + dy * dy
            if (d2 >= minD * minD) continue
            val d = max(sqrt(d2), 0.01f)
            val nx = dx / d
            val ny = dy / d
            val push = (minD - d) * (if (isBig(i)) 0.3f else 1f)
            x[i] += nx * push
            y[i] += ny * push
            vx[i] += nx * 30f + f.vx * 0.5f
            vy[i] += ny * 30f + f.vy * 0.5f
        }
    }

    private fun pushObstacles(obstacles: List<Circle>) {
        for (i in 0 until count) {
            if (!isBig(i)) continue
            for (o in obstacles) {
                val dx = x[i] - o.x
                val dy = y[i] - o.y
                val minD = o.radius + radius[i]
                val d2 = dx * dx + dy * dy
                if (d2 >= minD * minD) continue
                val d = max(sqrt(d2), 0.01f)
                val nx = dx / d
                val ny = dy / d
                x[i] += nx * (minD - d)
                y[i] += ny * (minD - d)
                val along = vx[i] * nx + vy[i] * ny
                if (along < 0f) {
                    vx[i] -= nx * along * 1.4f
                    vy[i] -= ny * along * 1.4f
                }
            }
        }
    }

    private fun cellX(v: Float): Int = (v / cellSize).toInt().coerceIn(0, cols - 1)
    private fun cellY(v: Float): Int = (v / cellSize).toInt().coerceIn(0, rows - 1)

    private fun buildHash() {
        heads.fill(-1)
        for (i in 0 until count) {
            val c = cellY(y[i]) * cols + cellX(x[i])
            next[i] = heads[c]
            heads[c] = i
        }
    }

    private fun solvePairs(time: Float) {
        for (i in 0 until count) {
            val big = isBig(i)
            val reach = if (big) ((radius[i] + 1f) / cellSize).toInt() + 1 else 1
            val cx = cellX(x[i])
            val cy = cellY(y[i])
            for (oy in -reach..reach) {
                val ry = cy + oy
                if (ry < 0 || ry >= rows) continue
                for (ox in -reach..reach) {
                    val rx = cx + ox
                    if (rx < 0 || rx >= cols) continue
                    var j = heads[ry * cols + rx]
                    while (j >= 0) {
                        if (j != i) {
                            val bigJ = isBig(j)
                            // Small pairs once (j > i); big-small from the big side; big pairs once.
                            val handle = if (big) (!bigJ || j > i) else (!bigJ && j > i)
                            if (handle) pair(i, j, time)
                        }
                        j = next[j]
                    }
                }
            }
        }
    }

    private fun mass(i: Int): Float = if (isBig(i)) radius[i] * radius[i] else 1f

    private fun pair(i: Int, j: Int, time: Float) {
        var dx = x[j] - x[i]
        var dy = y[j] - y[i]
        val minD = radius[i] + radius[j]
        val d2 = dx * dx + dy * dy
        val ti = type[i]
        val tj = type[j]
        if (d2 < minD * minD) {
            var d = sqrt(d2)
            if (d < 1e-3f) {
                dx = 0.01f
                dy = 0f
                d = 0.01f
            }
            val nx = dx / d
            val ny = dy / d
            val wi = 1f / mass(i)
            val wj = 1f / mass(j)
            val s = (minD - d) / (wi + wj) * RELAX
            x[i] -= nx * s * wi
            y[i] -= ny * s * wi
            x[j] += nx * s * wj
            y[j] += ny * s * wj
            if ((ti == MARBLE || ti == BALL) && (tj == MARBLE || tj == BALL)) collideBig(i, j, nx, ny, time)
        } else if (ti == WATER && tj == WATER && d2 < COHESION_RANGE * COHESION_RANGE) {
            val d = sqrt(d2)
            val pull = (d - WATER_REST) * COHESION * 0.5f
            val nx = dx / d
            val ny = dy / d
            x[i] += nx * pull
            y[i] += ny * pull
            x[j] -= nx * pull
            y[j] -= ny * pull
        }
    }

    private fun collideBig(i: Int, j: Int, nx: Float, ny: Float, time: Float) {
        val closing = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny
        if (closing <= 0f) return
        val mi = mass(i)
        val mj = mass(j)
        val impulse = (1f + BIG_RESTITUTION) * closing / (1f / mi + 1f / mj)
        vx[i] -= impulse / mi * nx
        vy[i] -= impulse / mi * ny
        vx[j] += impulse / mj * nx
        vy[j] += impulse / mj * ny
        if (closing > HIT_SPEED && time - lastHit[i] > HIT_GAP && time - lastHit[j] > HIT_GAP) {
            lastHit[i] = time
            lastHit[j] = time
            impacts += Event.Impact((x[i] + x[j]) / 2f, (y[i] + y[j]) / 2f, closing, max(radius[i], radius[j]), wall = false)
        }
    }

    private fun finish(dt: Float, gMag: Float) {
        var sandSum = 0f
        var sandN = 0
        var waterSum = 0f
        var waterN = 0
        val inv = 1f / dt
        for (i in 0 until count) {
            val r = radius[i]
            if (x[i] < r) x[i] = r else if (x[i] > width - r) x[i] = width - r
            if (y[i] < r) y[i] = r else if (y[i] > height - r) y[i] = height - r
            val t = type[i]
            if (t != SAND && t != WATER) continue
            var nvx = (x[i] - px[i]) * inv
            var nvy = (y[i] - py[i]) * inv
            var sp2 = nvx * nvx + nvy * nvy
            if (sp2 > MAX_SPEED * MAX_SPEED) {
                val k = MAX_SPEED / sqrt(sp2)
                nvx *= k
                nvy *= k
                sp2 = MAX_SPEED * MAX_SPEED
            }
            if (t == SAND) {
                if (sp2 < SAND_SLEEP * SAND_SLEEP && gMag < SAND_STATIC_G) {
                    nvx = 0f
                    nvy = 0f
                    sp2 = 0f
                }
                sandSum += sqrt(sp2)
                sandN++
            } else {
                waterSum += sqrt(sp2)
                waterN++
            }
            vx[i] = nvx
            vy[i] = nvy
        }
        sandMotion = if (sandN > 0) sandSum / sandN else 0f
        waterMotion = if (waterN > 0) waterSum / waterN else 0f
    }

    companion object {
        const val SAND: Byte = 0
        const val WATER: Byte = 1
        const val MARBLE: Byte = 2

        /** A curled-up isopod rolling like a light marble. */
        const val BALL: Byte = 3

        /** Pixels per second squared at 1 g. */
        const val G = 900f
        const val ITERATIONS = 2
        const val RELAX = 0.8f
        const val HIT_SPEED = 90f
        const val HIT_GAP = 0.08f
        const val BIG_RESTITUTION = 0.5f
        const val MAX_SPEED = 600f
        const val SAND_SLEEP = 2f

        /** Below this much sideways gravity, sand holds whatever shape it was pushed into. */
        const val SAND_STATIC_G = 0.22f
        const val WATER_REST = 2.1f
        const val COHESION_RANGE = 3.2f
        const val COHESION = 0.03f
    }
}
