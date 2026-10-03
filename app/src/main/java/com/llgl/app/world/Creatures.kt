package com.llgl.app.world

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Something alive in the box. Positions are world pixels, headings radians (screen y down). */
abstract class Creature(var x: Float, var y: Float, var heading: Float) {
    enum class Kind { ISOPOD, SNAIL, BEETLE, FIREFLY }

    abstract val kind: Kind

    abstract fun step(dt: Float, world: Terrarium)

    /** Tapped right on it, from ([fromX], [fromY]). */
    open fun poke(world: Terrarium, fromX: Float, fromY: Float) {}

    /** The thumb landed nearby, or ([strong]) the whole box shook. */
    open fun startle(world: Terrarium, strong: Boolean) {}

    /** Walks ahead with a random wobble, turning away from walls and plant bases. */
    protected fun wander(dt: Float, world: Terrarium, rng: Rng, speed: Float, wobble: Float) {
        heading += (rng.nextFloat() - 0.5f) * wobble * dt * 2f
        steerClear(dt, world)
        x += cos(heading) * speed * dt
        y += sin(heading) * speed * dt
        clampInside(world, 3f)
    }

    protected fun steerClear(dt: Float, world: Terrarium) {
        var ax = 0f
        var ay = 0f
        val m = WALL_MARGIN
        if (x < m) ax += m - x
        if (x > world.width - m) ax -= x - (world.width - m)
        if (y < m) ay += m - y
        if (y > world.height - m) ay -= y - (world.height - m)
        for (o in world.obstacles) {
            val dx = x - o.x
            val dy = y - o.y
            val d = hypot(dx, dy)
            val keep = o.radius + 5f
            if (d < keep && d > 1e-3f) {
                ax += dx / d * (keep - d)
                ay += dy / d * (keep - d)
            }
        }
        if (ax != 0f || ay != 0f) heading = turnToward(heading, atan2(ay, ax), 8f * dt)
    }

    protected fun clampInside(world: Terrarium, margin: Float) {
        x = x.coerceIn(margin, world.width - margin)
        y = y.coerceIn(margin, world.height - margin)
    }

    protected fun steerToward(tx: Float, ty: Float, dt: Float, rate: Float) {
        heading = turnToward(heading, atan2(ty - y, tx - x), rate * dt)
    }

    protected fun fleeFrom(fx: Float, fy: Float) {
        heading = atan2(y - fy, x - fx)
    }

    companion object {
        const val WALL_MARGIN = 12f

        fun turnToward(from: Float, to: Float, maxDelta: Float): Float {
            var d = to - from
            val twoPi = (2 * PI).toFloat()
            while (d > PI) d -= twoPi
            while (d < -PI) d += twoPi
            return from + d.coerceIn(-maxDelta, maxDelta)
        }
    }
}

/** A pill bug: wanders, pauses, flees the thumb, nibbles crumbs, and curls into a rolling ball when poked. */
class Isopod(x: Float, y: Float, heading: Float, private val rng: Rng) : Creature(x, y, heading) {
    enum class State { WALK, PAUSE, CURLED }

    override val kind = Kind.ISOPOD
    var state = State.WALK
        private set

    /** Index of this isopod's BALL particle while curled, kept current by [Terrarium.removeParticle]. */
    var ballIndex = -1
    var timer = rng.range(2f, 5f)
        private set
    var legPhase = 0f
        private set
    var fleeTimer = 0f
        private set
    var nibbling = false
        private set

    override fun step(dt: Float, world: Terrarium) {
        when (state) {
            State.CURLED -> {
                val p = world.particles
                if (ballIndex in 0 until p.count) {
                    x = p.x[ballIndex]
                    y = p.y[ballIndex]
                    heading += p.vx[ballIndex] * dt / BALL_RADIUS
                }
                timer -= dt
                // Unrolls once the box is flat enough, or anyway after a while on a propped-up phone.
                if (timer <= 0f && (world.gravityMagnitude < UNCURL_G || timer < -MAX_EXTRA_CURL)) uncurl(world)
            }
            State.PAUSE -> {
                timer -= dt
                if (timer <= 0f) {
                    state = State.WALK
                    timer = rng.range(2f, 6f)
                }
            }
            State.WALK -> {
                timer -= dt
                if (fleeTimer > 0f) fleeTimer -= dt
                val fleeing = fleeTimer > 0f
                val crumb = if (fleeing) null else world.nearestCrumb(x, y, CRUMB_RANGE)
                nibbling = false
                if (crumb != null) {
                    if (hypot(crumb.x - x, crumb.y - y) < 4f) {
                        crumb.amount -= dt * NIBBLE_RATE
                        legPhase += dt * 4f
                        nibbling = true
                        return
                    }
                    steerToward(crumb.x, crumb.y, dt, 4f)
                }
                val speed = if (fleeing) SPEED * 2.6f else SPEED
                wander(dt, world, rng, speed, wobble = if (fleeing) 1f else if (crumb != null) 0.4f else 3f)
                legPhase += dt * speed * 0.9f
                if (world.gravityMagnitude > SLIDE_G) {
                    x += world.gravityX * SLIDE_SPEED * dt
                    y += world.gravityY * SLIDE_SPEED * dt
                    clampInside(world, 3f)
                }
                if (timer <= 0f && !fleeing && crumb == null) {
                    state = State.PAUSE
                    timer = rng.range(0.5f, 2f)
                }
            }
        }
    }

    fun curl(world: Terrarium) {
        if (state == State.CURLED) return
        state = State.CURLED
        nibbling = false
        timer = rng.range(2.5f, 4.5f)
        ballIndex = world.particles.add(Particles.BALL, x, y, BALL_RADIUS)
        world.events += Event.Curl(x, y)
    }

    private fun uncurl(world: Terrarium) {
        if (ballIndex >= 0) world.removeParticle(ballIndex)
        ballIndex = -1
        state = State.WALK
        timer = rng.range(2f, 5f)
        fleeTimer = 1.2f
    }

    override fun poke(world: Terrarium, fromX: Float, fromY: Float) = curl(world)

    override fun startle(world: Terrarium, strong: Boolean) {
        if (strong) {
            curl(world)
        } else if (state != State.CURLED) {
            world.finger?.let { fleeFrom(it.x, it.y) }
            state = State.WALK
            fleeTimer = 1.5f
        }
    }

    companion object {
        const val SPEED = 14f
        const val BALL_RADIUS = 3f
        const val CRUMB_RANGE = 70f
        const val NIBBLE_RATE = 0.4f
        const val SLIDE_G = 0.45f
        const val SLIDE_SPEED = 25f
        const val UNCURL_G = 0.35f
        const val MAX_EXTRA_CURL = 10f
    }
}

/** Where a snail has been: a ring of points that the renderer fades out. */
class Trail(val capacity: Int) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val time = FloatArray(capacity)
    var count = 0
        private set
    private var head = 0

    fun add(px: Float, py: Float, t: Float) {
        x[head] = px
        y[head] = py
        time[head] = t
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /** Array index of the k-th oldest point. */
    fun index(k: Int): Int = if (count < capacity) k else (head + k) % capacity
}

/** Slow, leaves a slime trail, and pulls into its shell when poked. */
class Snail(x: Float, y: Float, heading: Float, private val rng: Rng) : Creature(x, y, heading) {
    override val kind = Kind.SNAIL
    val trail = Trail(TRAIL_POINTS)
    var hideTimer = 0f
        private set
    private var trailTimer = 0f

    val hiding: Boolean get() = hideTimer > 0f

    override fun step(dt: Float, world: Terrarium) {
        if (hideTimer > 0f) {
            hideTimer -= dt
            return
        }
        wander(dt, world, rng, SPEED, wobble = 1.2f)
        trailTimer += dt
        if (trailTimer >= TRAIL_INTERVAL) {
            trailTimer = 0f
            trail.add(x, y, world.time)
        }
    }

    override fun poke(world: Terrarium, fromX: Float, fromY: Float) {
        hideTimer = 3f
    }

    override fun startle(world: Terrarium, strong: Boolean) {
        hideTimer = max(hideTimer, if (strong) 2f else 1f)
    }

    companion object {
        const val SPEED = 4.5f
        const val TRAIL_POINTS = 80
        const val TRAIL_INTERVAL = 0.25f
        const val TRAIL_FADE = 20f
    }
}

/** Dashes and stops; bolts away from anything that comes near. */
class Beetle(x: Float, y: Float, heading: Float, private val rng: Rng) : Creature(x, y, heading) {
    override val kind = Kind.BEETLE
    var dashTimer = 0f
        private set
    var pauseTimer = rng.range(0.5f, 2f)
        private set
    var legPhase = 0f
        private set

    val dashing: Boolean get() = pauseTimer <= 0f

    override fun step(dt: Float, world: Terrarium) {
        if (pauseTimer > 0f) {
            pauseTimer -= dt
            if (pauseTimer <= 0f) {
                dashTimer = rng.range(0.3f, 0.8f)
                heading += rng.range(-1.2f, 1.2f)
            }
            return
        }
        wander(dt, world, rng, SPEED, wobble = 0.8f)
        legPhase += dt * 30f
        dashTimer -= dt
        if (dashTimer <= 0f) pauseTimer = rng.range(0.8f, 3f)
    }

    override fun poke(world: Terrarium, fromX: Float, fromY: Float) {
        fleeFrom(fromX, fromY)
        pauseTimer = 0f
        dashTimer = 0.7f
    }

    override fun startle(world: Terrarium, strong: Boolean) {
        world.finger?.let { fleeFrom(it.x, it.y) } ?: run { heading += rng.range(-2f, 2f) }
        pauseTimer = 0f
        dashTimer = if (strong) 1f else 0.6f
    }

    companion object {
        const val SPEED = 45f
    }
}

/** A night visitor: drifts, blinks, and is a little drawn to the thumb. */
class Firefly(x: Float, y: Float, heading: Float, private val rng: Rng) : Creature(x, y, heading) {
    override val kind = Kind.FIREFLY
    private var phase = rng.range(0f, (2 * PI).toFloat())
    private val period = rng.range(1.4f, 3f)

    /** Brightness of the blink, 0..1. */
    var glow = 0f
        private set

    /** Fades in on arrival and out when told to leave. */
    var alpha = 0f
        private set
    var fading = false

    val gone: Boolean get() = fading && alpha <= 0f

    override fun step(dt: Float, world: Terrarium) {
        phase += dt * (2 * PI).toFloat() / period
        val s = (sin(phase) + 1f) / 2f
        glow = s * s * s
        alpha = if (fading) max(0f, alpha - dt) else minOf(1f, alpha + dt)
        heading += (rng.nextFloat() - 0.5f) * 2.5f * dt
        world.finger?.let { steerToward(it.x, it.y, dt, 1.2f) }
        steerClear(dt, world)
        x += cos(heading) * SPEED * dt
        y += sin(heading) * SPEED * dt
        clampInside(world, 4f)
    }

    companion object {
        const val SPEED = 10f
    }
}
