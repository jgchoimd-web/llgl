package com.llgl.app.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

enum class Phase { READY, PLAYING, GAME_OVER }

enum class ObstacleKind { BARRIER, ORB }

/**
 * Something on the road. [d] is the distance from the camera in road units
 * ([GameEngine.NEAR] is right in front of the player, [GameEngine.FAR] is where things spawn),
 * [u] is the lateral position where -1 and 1 are the road edges.
 */
class Obstacle(var d: Float, val u: Float, val kind: ObstacleKind)

/** A neon building beside the road. [side] is -1 for left, 1 for right. Sizes are in road units. */
class Pillar(var d: Float, val side: Int, val height: Float, val width: Float, val palette: Int)

sealed interface GameEvent {
    data class Collected(val u: Float, val d: Float) : GameEvent
    data class Crashed(val u: Float, val d: Float) : GameEvent
}

/**
 * Pure game logic for the downhill marble run. It has no Android dependencies so it runs in
 * plain JVM unit tests. Call [update] once per frame and read the public state to render it.
 */
class GameEngine(private val random: Random = Random(System.nanoTime())) {

    var phase: Phase = Phase.READY
        private set
    var marbleU: Float = 0f
        private set
    var speed: Float = IDLE_SPEED
        private set
    var distance: Float = 0f
        private set
    var elapsed: Float = 0f
        private set
    var orbs: Int = 0
        private set
    var score: Int = 0
        private set
    var best: Int = 0
    var isNewBest: Boolean = false
        private set

    /** Seconds since the crash. Taps are ignored for a moment so a frantic tap does not restart by accident. */
    var gameOverTime: Float = 0f
        private set

    val obstacles = ArrayList<Obstacle>()
    val pillars = ArrayList<Pillar>()

    /** Events raised by the latest [update] call. Cleared at the start of the next one. */
    val events = ArrayList<GameEvent>()

    private var obstacleTravel = 0f
    private var pillarTravel = 0f

    init {
        var d = NEAR
        while (d < FAR) {
            spawnPillars(d)
            d += PILLAR_SPACING
        }
    }

    val canRestart: Boolean
        get() = phase == Phase.GAME_OVER && gameOverTime >= RESTART_DELAY

    fun tap() {
        when (phase) {
            Phase.READY -> start()
            Phase.GAME_OVER -> if (canRestart) start()
            Phase.PLAYING -> Unit
        }
    }

    /** Moves the marble sideways by [deltaU] road units (positive = right). */
    fun steer(deltaU: Float) {
        if (phase == Phase.PLAYING) marbleU = (marbleU + deltaU).coerceIn(-MAX_U, MAX_U)
    }

    fun update(dt: Float) {
        events.clear()
        when (phase) {
            Phase.READY -> {
                speed = IDLE_SPEED
                advance(dt)
            }
            Phase.PLAYING -> {
                elapsed += dt
                speed = START_SPEED + (MAX_SPEED - START_SPEED) * min(1f, distance / FULL_SPEED_DISTANCE)
                advance(dt)
                score = (distance * DISTANCE_POINTS).toInt() + orbs * ORB_POINTS
                spawnObstacles()
                checkCollisions()
            }
            Phase.GAME_OVER -> gameOverTime += dt
        }
    }

    internal fun addObstacle(obstacle: Obstacle) {
        obstacles += obstacle
    }

    private fun start() {
        obstacles.clear()
        marbleU = 0f
        distance = 0f
        elapsed = 0f
        orbs = 0
        score = 0
        isNewBest = false
        gameOverTime = 0f
        obstacleTravel = 0f
        phase = Phase.PLAYING
    }

    private fun advance(dt: Float) {
        val step = speed * dt
        distance += step

        for (o in obstacles) o.d -= step
        obstacles.removeAll { it.d < NEAR * 0.5f }

        for (p in pillars) p.d -= step
        pillars.removeAll { it.d < PILLAR_DESPAWN_D }
        pillarTravel += step
        while (pillarTravel >= PILLAR_SPACING) {
            pillarTravel -= PILLAR_SPACING
            spawnPillars(FAR)
        }

        obstacleTravel += step
    }

    private fun spawnObstacles() {
        val difficulty = (speed - START_SPEED) / (MAX_SPEED - START_SPEED)
        val gap = MAX_WAVE_GAP - (MAX_WAVE_GAP - MIN_WAVE_GAP) * difficulty
        if (obstacleTravel < gap) return
        obstacleTravel = 0f

        val lanes = LANES.indices.shuffled(random)
        val roll = random.nextFloat()
        when {
            roll < 0.25f -> obstacles += Obstacle(FAR, LANES[lanes[0]], ObstacleKind.ORB)
            roll < 0.75f -> obstacles += Obstacle(FAR, LANES[lanes[0]], ObstacleKind.BARRIER)
            else -> {
                // Two lanes blocked, one lane free (sometimes with a reward in it).
                obstacles += Obstacle(FAR, LANES[lanes[0]], ObstacleKind.BARRIER)
                obstacles += Obstacle(FAR, LANES[lanes[1]], ObstacleKind.BARRIER)
                if (random.nextFloat() < 0.5f) obstacles += Obstacle(FAR, LANES[lanes[2]], ObstacleKind.ORB)
            }
        }
    }

    private fun spawnPillars(d: Float) {
        for (side in SIDES) {
            if (random.nextFloat() < 0.85f) {
                pillars += Pillar(
                    d = d + random.nextFloat() * 0.5f,
                    side = side,
                    height = 0.35f + random.nextFloat() * 0.8f,
                    width = 0.25f + random.nextFloat() * 0.3f,
                    palette = random.nextInt(3),
                )
            }
        }
    }

    private fun checkCollisions() {
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val o = iterator.next()
            if (abs(o.d - MARBLE_D) > HIT_WINDOW) continue
            val reach = MARBLE_HALF_U + if (o.kind == ObstacleKind.BARRIER) BARRIER_HALF_U else ORB_HALF_U
            if (abs(o.u - marbleU) >= reach) continue
            when (o.kind) {
                ObstacleKind.ORB -> {
                    iterator.remove()
                    orbs++
                    score += ORB_POINTS
                    events += GameEvent.Collected(o.u, o.d)
                }
                ObstacleKind.BARRIER -> {
                    crash(o)
                    return
                }
            }
        }
    }

    private fun crash(o: Obstacle) {
        phase = Phase.GAME_OVER
        gameOverTime = 0f
        if (score > best) {
            best = score
            isNewBest = true
        }
        events += GameEvent.Crashed(o.u, o.d)
    }

    companion object {
        const val NEAR = 1f
        const val FAR = 10f
        const val MARBLE_D = 1.6f
        const val MARBLE_HALF_U = 0.1f
        const val BARRIER_HALF_U = 0.17f
        const val ORB_HALF_U = 0.11f
        const val MAX_U = 0.8f
        const val IDLE_SPEED = 1.2f
        const val START_SPEED = 3f
        const val MAX_SPEED = 9f
        const val FULL_SPEED_DISTANCE = 600f
        const val RESTART_DELAY = 0.7f
        const val ORB_POINTS = 50
        const val DISTANCE_POINTS = 10f
        const val PILLAR_SPACING = 1.5f
        const val PILLAR_DESPAWN_D = 0.3f
        val LANES = floatArrayOf(-0.55f, 0f, 0.55f)

        private const val HIT_WINDOW = 0.28f
        private const val MAX_WAVE_GAP = 2.6f
        private const val MIN_WAVE_GAP = 1.5f
        private val SIDES = intArrayOf(-1, 1)
    }
}
