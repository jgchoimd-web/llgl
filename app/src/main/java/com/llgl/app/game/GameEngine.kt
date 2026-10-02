package com.llgl.app.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

enum class Phase { READY, PLAYING, PAUSED, GAME_OVER }

enum class ObstacleKind { BARRIER, ORB, BOOST }

/**
 * Something on the road. [d] is the distance from the camera in road units
 * ([GameEngine.NEAR] is the bottom of the screen, [GameEngine.FAR] the far edge where things
 * appear), [u] is the lateral position where -1 and 1 are the road edges.
 */
class Obstacle(var d: Float, val u: Float, val kind: ObstacleKind) {
    /** A boost pad fires once; it stays visible while it passes under the marble. */
    var consumed: Boolean = false
}

sealed interface GameEvent {
    data class Collected(val u: Float, val d: Float) : GameEvent
    data class Crashed(val u: Float, val d: Float) : GameEvent
    data object Boosted : GameEvent
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

    /** Current speed in road units per second, boost included. */
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

    /** Seconds of speed boost left after rolling over a boost pad. */
    var boostTime: Float = 0f
        private set

    val obstacles = ArrayList<Obstacle>()

    /** Events raised by the latest [update] call. Cleared at the start of the next one. */
    val events = ArrayList<GameEvent>()

    private var obstacleTravel = 0f

    /** Tests switch this off to run scripted scenarios without random waves getting in the way. */
    internal var spawnEnabled = true

    val isBoosting: Boolean
        get() = boostTime > 0f

    val canRestart: Boolean
        get() = phase == Phase.GAME_OVER && gameOverTime >= RESTART_DELAY

    fun tap() {
        when (phase) {
            Phase.READY -> start()
            Phase.PAUSED -> phase = Phase.PLAYING
            Phase.GAME_OVER -> if (canRestart) start()
            Phase.PLAYING -> Unit
        }
    }

    fun togglePause() {
        phase = when (phase) {
            Phase.PLAYING -> Phase.PAUSED
            Phase.PAUSED -> Phase.PLAYING
            else -> phase
        }
    }

    /** Abandons the current run (the best score is kept) and shows the title screen. */
    fun backToTitle() {
        obstacles.clear()
        marbleU = 0f
        distance = 0f
        orbs = 0
        score = 0
        boostTime = 0f
        obstacleTravel = 0f
        phase = Phase.READY
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
                boostTime = (boostTime - dt).coerceAtLeast(0f)
                val base = START_SPEED + (MAX_SPEED - START_SPEED) * difficulty()
                speed = if (boostTime > 0f) min(base * BOOST_MULTIPLIER, BOOSTED_SPEED_CAP) else base
                advance(dt)
                score = (distance * DISTANCE_POINTS).toInt() + orbs * ORB_POINTS
                spawnObstacles()
                checkCollisions()
            }
            Phase.PAUSED -> Unit
            Phase.GAME_OVER -> gameOverTime += dt
        }
    }

    internal fun addObstacle(obstacle: Obstacle) {
        obstacles += obstacle
    }

    private fun difficulty(): Float = min(1f, distance / FULL_SPEED_DISTANCE)

    private fun start() {
        obstacles.clear()
        marbleU = 0f
        distance = 0f
        elapsed = 0f
        orbs = 0
        score = 0
        isNewBest = false
        gameOverTime = 0f
        boostTime = 0f
        obstacleTravel = 0f
        phase = Phase.PLAYING
    }

    private fun advance(dt: Float) {
        val step = speed * dt
        distance += step
        for (o in obstacles) o.d -= step
        obstacles.removeAll { it.d < DESPAWN_D }
        obstacleTravel += step
    }

    private fun spawnObstacles() {
        if (!spawnEnabled) return
        val gap = MAX_WAVE_GAP - (MAX_WAVE_GAP - MIN_WAVE_GAP) * difficulty()
        if (obstacleTravel < gap) return
        obstacleTravel = 0f

        val lanes = LANES.indices.shuffled(random)
        val roll = random.nextFloat()
        when {
            roll < 0.12f -> obstacles += Obstacle(FAR, 0f, ObstacleKind.BOOST)
            roll < 0.35f -> obstacles += Obstacle(FAR, LANES[lanes[0]], ObstacleKind.ORB)
            roll < 0.78f -> obstacles += Obstacle(FAR, LANES[lanes[0]], ObstacleKind.BARRIER)
            else -> {
                // Two lanes blocked, one lane free (sometimes with a reward in it).
                obstacles += Obstacle(FAR, LANES[lanes[0]], ObstacleKind.BARRIER)
                obstacles += Obstacle(FAR, LANES[lanes[1]], ObstacleKind.BARRIER)
                if (random.nextFloat() < 0.5f) obstacles += Obstacle(FAR, LANES[lanes[2]], ObstacleKind.ORB)
            }
        }
    }

    private fun checkCollisions() {
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val o = iterator.next()
            if (abs(o.d - MARBLE_D) > HIT_WINDOW) continue
            when (o.kind) {
                ObstacleKind.BOOST -> if (!o.consumed) {
                    o.consumed = true
                    boostTime = BOOST_DURATION
                    events += GameEvent.Boosted
                }
                ObstacleKind.ORB -> if (abs(o.u - marbleU) < MARBLE_HALF_U + ORB_HALF_U) {
                    iterator.remove()
                    orbs++
                    score += ORB_POINTS
                    events += GameEvent.Collected(o.u, o.d)
                }
                ObstacleKind.BARRIER -> if (abs(o.u - marbleU) < MARBLE_HALF_U + BARRIER_HALF_U) {
                    crash(o)
                    return
                }
            }
        }
    }

    private fun crash(o: Obstacle) {
        phase = Phase.GAME_OVER
        gameOverTime = 0f
        boostTime = 0f
        speed = 0f
        if (score > best) {
            best = score
            isNewBest = true
        }
        events += GameEvent.Crashed(o.u, o.d)
    }

    companion object {
        const val NEAR = 1f
        const val FAR = 11f
        const val MARBLE_D = 3f
        const val DESPAWN_D = 0.4f
        const val MARBLE_HALF_U = 0.21f
        const val BARRIER_HALF_U = 0.17f
        const val ORB_HALF_U = 0.11f
        const val MAX_U = 0.52f
        const val IDLE_SPEED = 1.5f
        const val START_SPEED = 3.2f
        const val MAX_SPEED = 8.5f
        const val BOOST_MULTIPLIER = 1.6f
        const val BOOSTED_SPEED_CAP = 12f
        const val BOOST_DURATION = 2.2f
        const val FULL_SPEED_DISTANCE = 600f
        const val RESTART_DELAY = 0.7f
        const val ORB_POINTS = 50
        const val DISTANCE_POINTS = 10f
        val LANES = floatArrayOf(-0.55f, 0f, 0.55f)

        private const val HIT_WINDOW = 0.35f
        private const val MAX_WAVE_GAP = 2.8f
        private const val MIN_WAVE_GAP = 1.6f
    }
}
