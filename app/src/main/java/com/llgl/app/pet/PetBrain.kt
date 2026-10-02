package com.llgl.app.pet

import kotlin.math.abs
import kotlin.random.Random

enum class Activity { WALK, IDLE, HIDE, SLEEP, EAT, CARRIED, FALL }

enum class Facing { LEFT, RIGHT }

sealed interface PetEvent {
    /** Something worth writing to storage changed. */
    data object Persist : PetEvent
    data class Spoke(val line: Line) : PetEvent
    data object Landed : PetEvent
}

/**
 * The turtle's behaviour: a small state machine plus the hunger/happiness bookkeeping.
 * Pure Kotlin (no Android types) so it runs in JVM unit tests with a fake clock and seeded random.
 *
 * Positions: [x01] is 0..1 across the walkable width, [yUnits] is the height above the ground
 * in sprite heights (0 = on the ground). Call [advance] once per frame.
 */
class PetBrain(
    private val random: Random,
    private val clock: PetClock,
    private val config: PetConfig = PetConfig(),
    restored: PetState? = null,
) {
    var activity: Activity = Activity.IDLE
        private set
    var facing: Facing = Facing.RIGHT
        private set
    var x01: Float = restored?.x01 ?: 0.5f
        private set
    var yUnits: Float = 0f
        private set
    var hunger: Float = restored?.hunger ?: PetState().hunger
        private set
    var happiness: Float = restored?.happiness ?: PetState().happiness
        private set
    var bubble: Line? = null
        private set
    var lettuceX01: Float? = null
        private set

    /** Seconds of animation time; the renderer picks sprite frames from it. */
    var animTime: Float = 0f
        private set

    private var lastSeenEpochMs: Long = restored?.lastSeenEpochMs ?: 0L
    private var lastGreetingDay: String = restored?.lastGreetingDay ?: ""
    private var stateTimer = 1f
    private var walkTarget = x01
    private var awakeUntilMs = 0L
    private var bubbleRemaining = 0f
    private var bubbleCooldown = 0f
    private var chatterIn = nextChatterDelay()
    private var feedCooldown = 0f
    private var fallVelocity = 0f
    private val pendingEvents = ArrayList<PetEvent>()

    val isAsleep: Boolean
        get() = activity == Activity.SLEEP

    /** Events raised since the last call; the caller owns them afterwards. */
    fun drainEvents(): List<PetEvent> {
        if (pendingEvents.isEmpty()) return emptyList()
        val out = ArrayList(pendingEvents)
        pendingEvents.clear()
        return out
    }

    fun exportState(): PetState = PetState(
        hunger = hunger,
        happiness = happiness,
        lastSeenEpochMs = lastSeenEpochMs,
        lastGreetingDay = lastGreetingDay,
        x01 = x01,
    )

    fun advance(frameDt: Float) {
        val dt = frameDt.coerceIn(0f, config.maxFrameDt)
        applyWallClock(clock.nowEpochMs())
        animTime += dt
        bubbleCooldown = (bubbleCooldown - dt).coerceAtLeast(0f)
        feedCooldown = (feedCooldown - dt).coerceAtLeast(0f)
        if (bubble != null) {
            bubbleRemaining -= dt
            if (bubbleRemaining <= 0f) bubble = null
        }
        when (activity) {
            Activity.WALK -> walk(dt)
            Activity.IDLE -> {
                stateTimer -= dt
                if (stateTimer <= 0f) startWalking()
            }
            Activity.HIDE -> {
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    activity = Activity.IDLE
                    stateTimer = 0.6f
                }
            }
            Activity.EAT -> {
                stateTimer -= dt
                if (stateTimer <= 0f) finishEating()
            }
            Activity.FALL -> fall(dt)
            Activity.SLEEP, Activity.CARRIED -> Unit
        }
        chatter(dt)
    }

    /** A tap startles the turtle into its shell, or wakes it (grumpily) at night. */
    fun onTap() {
        when (activity) {
            Activity.SLEEP -> wakeUp(grumpy = true)
            Activity.WALK, Activity.IDLE -> {
                activity = Activity.HIDE
                stateTimer = config.hideDurationSeconds
                happiness = (happiness + 1f).coerceAtMost(100f)
                if (random.nextFloat() < 0.6f) say(Line.STARTLED)
            }
            Activity.HIDE -> stateTimer = config.hideDurationSeconds
            Activity.EAT, Activity.CARRIED, Activity.FALL -> Unit
        }
    }

    fun onDragStart() {
        if (activity == Activity.SLEEP) wakeUp(grumpy = true)
        if (activity == Activity.EAT) lettuceX01 = null
        activity = Activity.CARRIED
        bubble = null
        fallVelocity = 0f
    }

    fun onDrag(x01: Float, yUnits: Float) {
        this.x01 = x01.coerceIn(0f, 1f)
        this.yUnits = yUnits.coerceAtLeast(0f)
    }

    fun onDragEnd(x01: Float, yUnits: Float) {
        onDrag(x01, yUnits)
        walkTarget = this.x01
        if (this.yUnits > config.landingThreshold) {
            activity = Activity.FALL
            fallVelocity = 0f
        } else {
            this.yUnits = 0f
            land()
        }
        pendingEvents += PetEvent.Persist
    }

    /** Puts a lettuce in front of the turtle. Returns false when it will not eat right now. */
    fun feed(): Boolean {
        when (activity) {
            Activity.SLEEP -> {
                say(Line.ZZZ, force = true)
                return false
            }
            Activity.CARRIED, Activity.FALL, Activity.EAT -> return false
            else -> Unit
        }
        if (hunger < config.fullThreshold) {
            say(Line.FULL, force = true)
            return false
        }
        if (feedCooldown > 0f) return false
        feedCooldown = config.feedCooldownSeconds

        var direction = if (facing == Facing.RIGHT) 1f else -1f
        var lettuce = x01 + direction * config.lettuceOffset
        if (lettuce < 0f || lettuce > 1f) {
            direction = -direction
            lettuce = x01 + direction * config.lettuceOffset
        }
        lettuceX01 = lettuce.coerceIn(0f, 1f)
        walkTarget = (lettuce - direction * config.eatDistance).coerceIn(0f, 1f)
        activity = Activity.WALK
        return true
    }

    /** A gentle pat; a sleeping turtle just snores. */
    fun pet(): Boolean {
        when (activity) {
            Activity.SLEEP -> {
                say(Line.ZZZ, force = true)
                return false
            }
            Activity.CARRIED, Activity.FALL -> return false
            else -> Unit
        }
        happiness = (happiness + config.petHappiness).coerceAtMost(100f)
        say(Line.PETTED, force = true)
        pendingEvents += PetEvent.Persist
        return true
    }

    private fun applyWallClock(now: Long) {
        // Real-time decay runs on the same rule as the setup screen's preview (PetRules.decayed),
        // applied at most once a second so float precision stays intact.
        if (lastSeenEpochMs <= 0L) {
            lastSeenEpochMs = now
        } else if (now - lastSeenEpochMs >= 1000L) {
            val decayed = PetRules.decayed(exportState(), now, config)
            hunger = decayed.hunger
            happiness = decayed.happiness
            lastSeenEpochMs = now
        }

        val hour = clock.hourOfDay()
        if (PetRules.isSleepHour(hour, config)) {
            val canSleep = activity == Activity.WALK || activity == Activity.IDLE || activity == Activity.HIDE
            if (canSleep && now >= awakeUntilMs) fallAsleep()
        } else {
            if (activity == Activity.SLEEP) wakeUp(grumpy = false)
            if (hour < 12) {
                val day = clock.localDayKey()
                if (day != lastGreetingDay) {
                    lastGreetingDay = day
                    say(Line.MORNING, force = true)
                    pendingEvents += PetEvent.Persist
                }
            }
        }
    }

    private fun walk(dt: Float) {
        val speed = config.walkSpeed * (0.8f + 0.4f * happiness / 100f)
        val direction = if (walkTarget >= x01) 1f else -1f
        facing = if (direction > 0f) Facing.RIGHT else Facing.LEFT
        val step = speed * dt
        if (abs(walkTarget - x01) <= step) {
            x01 = walkTarget
            arrived()
        } else {
            x01 += direction * step
        }
    }

    private fun arrived() {
        if (lettuceX01 != null) {
            activity = Activity.EAT
            stateTimer = config.eatDurationSeconds
            return
        }
        if (random.nextFloat() < config.hideChance) {
            activity = Activity.HIDE
            stateTimer = config.hideDurationSeconds
        } else {
            activity = Activity.IDLE
            stateTimer = 1f + random.nextFloat() * 2f
        }
    }

    private fun startWalking() {
        var target: Float
        var attempts = 0
        do {
            target = random.nextFloat()
            attempts++
        } while (abs(target - x01) < config.minWalkDistance && attempts < 20)
        walkTarget = target
        activity = Activity.WALK
    }

    private fun finishEating() {
        lettuceX01 = null
        hunger = (hunger - config.feedAmount).coerceIn(0f, 100f)
        happiness = (happiness + config.feedHappiness).coerceIn(0f, 100f)
        activity = Activity.IDLE
        stateTimer = 1.5f
        say(Line.YUM, force = true)
        pendingEvents += PetEvent.Persist
    }

    private fun fall(dt: Float) {
        fallVelocity += config.gravity * dt
        yUnits -= fallVelocity * dt
        if (yUnits <= 0f) {
            yUnits = 0f
            fallVelocity = 0f
            land()
            if (random.nextFloat() < 0.5f) say(Line.LANDED)
        }
    }

    private fun land() {
        activity = Activity.IDLE
        stateTimer = 0.8f
        pendingEvents += PetEvent.Landed
    }

    private fun fallAsleep() {
        activity = Activity.SLEEP
        lettuceX01 = null
        bubble = null
        pendingEvents += PetEvent.Persist
    }

    private fun wakeUp(grumpy: Boolean) {
        activity = Activity.IDLE
        stateTimer = if (grumpy) 3f else 1f
        if (grumpy) {
            happiness = (happiness - config.grumpyPenalty).coerceIn(0f, 100f)
            awakeUntilMs = clock.nowEpochMs() + (config.wakeGraceSeconds * 1000f).toLong()
            say(Line.SLEEPY, force = true)
        }
    }

    private fun chatter(dt: Float) {
        if (activity != Activity.WALK && activity != Activity.IDLE) return
        chatterIn -= dt
        if (chatterIn > 0f) return
        chatterIn = nextChatterDelay()
        say(pickChatterLine())
    }

    private fun pickChatterLine(): Line {
        val hour = clock.hourOfDay()
        return when {
            hunger > 70f && random.nextFloat() < 0.6f -> Line.HUNGRY
            happiness < 30f && random.nextFloat() < 0.5f -> Line.BORED
            hour in 12..13 && random.nextFloat() < 0.4f -> Line.LUNCH
            hour in 18..21 && random.nextFloat() < 0.4f -> Line.EVENING
            happiness > 85f && random.nextFloat() < 0.3f -> Line.HAPPY
            else -> Line.IDLE_LINES[random.nextInt(Line.IDLE_LINES.size)]
        }
    }

    private fun nextChatterDelay(): Float =
        config.chatterMinSeconds + random.nextFloat() * (config.chatterMaxSeconds - config.chatterMinSeconds)

    private fun say(line: Line, force: Boolean = false) {
        if (activity == Activity.CARRIED || activity == Activity.FALL) return
        if (!force && bubbleCooldown > 0f) return
        bubble = line
        bubbleRemaining = config.bubbleSeconds
        bubbleCooldown = config.bubbleCooldownSeconds
        pendingEvents += PetEvent.Spoke(line)
    }
}
