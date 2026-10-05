package com.llgl.app.world

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The whole box: sand, water, marbles, plants and creatures inside four walls, under a gravity
 * that tilts with the phone. Pure Kotlin; the view feeds it time, tilt, light and touches and
 * reads back positions and [events] to draw, sound and buzz.
 */
class Terrarium(val width: Float, val height: Float, val seed: Long, ageDays: Float = 0f) {
    private val rng = Rng(seed)
    val particles: Particles
    val plants: List<Plant>
    val obstacles: List<Circle>
    val creatures = ArrayList<Creature>()
    val crumbs = ArrayList<Crumb>()

    /** What happened in the last [step], for sounds and haptics. */
    val events = ArrayList<Event>()

    /** In-plane gravity in g: +x toward the right wall, +y toward the bottom wall. */
    var gravityX = 0f
    var gravityY = 0f
    val gravityMagnitude: Float get() = hypot(gravityX, gravityY)

    /** 0 = daylight, 1 = dark; fireflies and crickets come out above 0.3. */
    var nightness = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    var time = 0f
        private set
    var finger: Finger? = null
        private set

    /** Seconds of earthquake left. */
    var quake = 0f
        private set

    /** How hard the plants are swaying, 0..1. */
    var sway = 0f
        private set

    private var chirpTimer = 6f
    private var fireflyTimer = 0f

    init {
        val area = width * height
        val sand = (area * SAND_DENSITY).toInt()
        val water = (area * WATER_DENSITY).toInt()
        particles = Particles(sand + water + MARBLES + 16, width, height)
        plants = Plants.generate(rng, width, height, ageDays)
        obstacles = plants.mapNotNull { it.obstacle }
        // Grains touch at about two pixels apart, so a blob of n grains needs roughly 3.6·n square pixels.
        spawnBlob(Particles.SAND, sand, width * 0.30f, height * 0.72f, 1.45f, shades = 3)
        spawnBlob(Particles.WATER, water, width * 0.68f, height * 0.30f, 1.3f, shades = 1)
        spawnMarbles()
        spawnCreatures()
        // Let the fresh piles settle before the first frame.
        repeat(60) { particles.step(1f / 60f, 0f, 0f, 0f, null, obstacles) }
    }

    /** Scatters [count] grains in an ellipse ([aspect] = width / height) just large enough for them to touch. */
    private fun spawnBlob(type: Byte, count: Int, cx: Float, cy: Float, aspect: Float, shades: Int) {
        val area = count * 3.6f
        val ry = sqrt(area / (Math.PI.toFloat() * aspect))
        val rx = ry * aspect
        var placed = 0
        while (placed < count) {
            val u = rng.range(-1f, 1f)
            val v = rng.range(-1f, 1f)
            if (u * u + v * v > 1f) continue
            val x = (cx + u * rx).coerceIn(2f, width - 2f)
            val y = (cy + v * ry).coerceIn(2f, height - 2f)
            particles.add(type, x, y, 1f, if (shades > 1) rng.nextInt(shades) else 0)
            placed++
        }
    }

    private fun spawnMarbles() {
        var placed = 0
        var tries = 0
        while (placed < MARBLES && tries++ < 200) {
            val r = rng.range(3f, 6f)
            val x = rng.range(r + 6f, width - r - 6f)
            val y = rng.range(r + 6f, height - r - 6f)
            if (obstacles.any { hypot(it.x - x, it.y - y) < it.radius + r + 2f }) continue
            var overlaps = false
            for (i in 0 until particles.count) {
                if (particles.isBig(i) && hypot(particles.x[i] - x, particles.y[i] - y) < particles.radius[i] + r + 2f) {
                    overlaps = true
                    break
                }
            }
            if (overlaps) continue
            particles.add(Particles.MARBLE, x, y, r, placed % 3)
            placed++
        }
    }

    private fun spawnCreatures() {
        repeat(3) { creatures += Isopod(freeX(), freeY(), rng.range(0f, TWO_PI), rng) }
        repeat(2) { creatures += Snail(freeX(), freeY(), rng.range(0f, TWO_PI), rng) }
        creatures += Beetle(freeX(), freeY(), rng.range(0f, TWO_PI), rng)
    }

    private fun freeX(): Float = rng.range(30f, width - 30f)
    private fun freeY(): Float = rng.range(30f, height - 30f)

    fun step(dt: Float) {
        time += dt
        events.clear()
        if (quake > 0f) {
            quake -= dt
            particles.jolt(rng, QUAKE_KICK)
        }
        sway = max(0f, sway - dt * 2.2f)
        particles.step(dt, gravityX, gravityY, time, finger, obstacles)
        events.addAll(particles.impacts)
        for (i in creatures.indices) creatures[i].step(dt, this)
        updateFireflies(dt)
        crumbs.removeAll { it.amount <= 0f }
        if (nightness > 0.6f) {
            chirpTimer -= dt
            if (chirpTimer <= 0f) {
                events += Event.Chirp
                chirpTimer = rng.range(4f, 12f)
            }
        }
    }

    private fun updateFireflies(dt: Float) {
        val target = if (nightness < 0.3f) 0 else (nightness * MAX_FIREFLIES + 0.5f).toInt()
        var active = 0
        for (c in creatures) if (c is Firefly && !c.fading) active++
        fireflyTimer -= dt
        if (fireflyTimer <= 0f) {
            if (active < target) {
                creatures += Firefly(freeX(), freeY(), rng.range(0f, TWO_PI), rng)
                fireflyTimer = 0.6f
            } else if (active > target) {
                for (c in creatures) {
                    if (c is Firefly && !c.fading) {
                        c.fading = true
                        break
                    }
                }
                fireflyTimer = 0.6f
            }
        }
        creatures.removeAll { it is Firefly && it.gone }
    }

    fun fireflyCount(): Int = creatures.count { it is Firefly && !it.fading }

    // ---------------------------------------------------------------------------------------
    // Touch and motion
    // ---------------------------------------------------------------------------------------

    fun fingerDown(x: Float, y: Float) {
        finger = Finger(x, y, FINGER_RADIUS)
        for (c in creatures) if (hypot(c.x - x, c.y - y) < STARTLE_RANGE) c.startle(this, strong = false)
    }

    fun fingerMove(x: Float, y: Float, dt: Float) {
        val f = finger ?: return fingerDown(x, y)
        if (dt > 0f) {
            f.vx = (x - f.x) / dt
            f.vy = (y - f.y) / dt
        }
        f.x = x
        f.y = y
    }

    fun fingerUp() {
        finger = null
    }

    /** A quick touch: pokes whatever is under it, puffs the sand and ripples the water around it. */
    fun tap(x: Float, y: Float) {
        for (i in creatures.indices) {
            val c = creatures[i]
            if (hypot(c.x - x, c.y - y) < TAP_RANGE) c.poke(this, x, y)
        }
        val p = particles
        for (i in 0 until p.count) {
            if (p.isBig(i)) continue
            val dx = p.x[i] - x
            val dy = p.y[i] - y
            val d2 = dx * dx + dy * dy
            if (d2 >= PUFF_RANGE * PUFF_RANGE) continue
            val d = max(sqrt(d2), 0.5f)
            val k = (1f - d / PUFF_RANGE) * PUFF_SPEED
            p.vx[i] += dx / d * k
            p.vy[i] += dy / d * k
        }
        events += Event.Tap(x, y, waterNear(x, y))
    }

    fun longPress(x: Float, y: Float) {
        if (crumbs.size < MAX_CRUMBS) crumbs += Crumb(x, y)
    }

    /** The phone was shaken: an earthquake of [strength] (1 = a firm shake). */
    fun shake(strength: Float) {
        quake = 0.45f * strength.coerceIn(0.5f, 2f)
        sway = 1f
        for (i in creatures.indices) creatures[i].startle(this, strong = true)
        events += Event.Shake(strength)
    }

    fun waterNear(x: Float, y: Float): Boolean {
        val p = particles
        for (i in 0 until p.count) {
            if (p.type[i] != Particles.WATER) continue
            if (hypot(p.x[i] - x, p.y[i] - y) < WATER_NEAR) return true
        }
        return false
    }

    fun nearestCrumb(x: Float, y: Float, range: Float): Crumb? {
        var best: Crumb? = null
        var bestD = range
        for (c in crumbs) {
            val d = hypot(c.x - x, c.y - y)
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    /** Removes a particle and keeps every curled isopod pointing at its own ball. */
    fun removeParticle(index: Int) {
        val last = particles.count - 1
        particles.remove(index)
        if (index != last) {
            for (c in creatures) if (c is Isopod && c.ballIndex == last) c.ballIndex = index
        }
    }

    // ---------------------------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------------------------

    /** Writes the loose contents and where the creatures are; curled isopods are saved walking. */
    fun save(out: DataOutputStream) {
        val p = particles
        var n = 0
        for (i in 0 until p.count) if (p.type[i] != Particles.BALL) n++
        out.writeInt(n)
        for (i in 0 until p.count) {
            if (p.type[i] == Particles.BALL) continue
            out.writeByte(p.type[i].toInt())
            out.writeByte(p.shade[i].toInt())
            out.writeFloat(p.radius[i])
            out.writeFloat(p.x[i])
            out.writeFloat(p.y[i])
        }
        val walkers = creatures.filter { it !is Firefly }
        out.writeInt(walkers.size)
        for (c in walkers) {
            out.writeByte(c.kind.ordinal)
            out.writeFloat(c.x)
            out.writeFloat(c.y)
            out.writeFloat(c.heading)
        }
        out.writeFloat(time)
    }

    /** Restores a [save]; returns false (and leaves the box as it was) if the data does not fit. */
    fun load(input: DataInputStream): Boolean {
        val n = input.readInt()
        if (n < 0 || n > particles.capacity - 16) return false
        val type = ByteArray(n)
        val shade = ByteArray(n)
        val radius = FloatArray(n)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            type[i] = input.readByte()
            shade[i] = input.readByte()
            radius[i] = input.readFloat()
            xs[i] = input.readFloat()
            ys[i] = input.readFloat()
            if (type[i] !in Particles.SAND..Particles.MARBLE || radius[i] !in 0.5f..12f) return false
            if (xs[i].isNaN() || ys[i].isNaN()) return false
        }
        val m = input.readInt()
        if (m < 0 || m > 64) return false
        val kinds = IntArray(m)
        val cx = FloatArray(m)
        val cy = FloatArray(m)
        val ch = FloatArray(m)
        for (i in 0 until m) {
            kinds[i] = input.readByte().toInt()
            cx[i] = input.readFloat()
            cy[i] = input.readFloat()
            ch[i] = input.readFloat()
        }
        val savedTime = input.readFloat()

        particles.clear()
        for (i in 0 until n) particles.add(type[i], xs[i].coerceIn(radius[i], width - radius[i]), ys[i].coerceIn(radius[i], height - radius[i]), radius[i], shade[i].toInt())
        for (c in creatures) if (c is Isopod) c.ballIndex = -1
        val pool = creatures.filter { it !is Firefly }.toMutableList()
        for (i in 0 until m) {
            val kind = Creature.Kind.entries.getOrNull(kinds[i]) ?: continue
            val c = pool.firstOrNull { it.kind == kind } ?: continue
            pool.remove(c)
            c.x = cx[i].coerceIn(3f, width - 3f)
            c.y = cy[i].coerceIn(3f, height - 3f)
            c.heading = ch[i]
        }
        time = max(time, savedTime)
        return true
    }

    companion object {
        const val SAND_DENSITY = 0.0070f
        const val WATER_DENSITY = 0.0014f
        const val MARBLES = 7
        const val MAX_FIREFLIES = 8
        const val MAX_CRUMBS = 6
        const val FINGER_RADIUS = 10f
        const val STARTLE_RANGE = 45f
        const val TAP_RANGE = 9f
        const val PUFF_RANGE = 18f
        const val PUFF_SPEED = 120f
        const val WATER_NEAR = 6f
        const val QUAKE_KICK = 25f
        private const val TWO_PI = (2 * PI).toFloat()
    }
}
