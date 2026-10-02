package com.llgl.app.pet

/** Everything about the turtle that survives a restart. */
data class PetState(
    /** 0 = stuffed, 100 = starving. */
    val hunger: Float = 25f,
    /** 0 = miserable, 100 = delighted. */
    val happiness: Float = 70f,
    /** When the stats were last brought up to date; 0 means never (first launch, no decay). */
    val lastSeenEpochMs: Long = 0L,
    /** Local day key of the last morning greeting, so it happens once per day. */
    val lastGreetingDay: String = "",
    /** Position along the walkable width, 0..1. */
    val x01: Float = 0.5f,
) {
    fun toMap(): Map<String, String> = mapOf(
        KEY_HUNGER to hunger.toString(),
        KEY_HAPPINESS to happiness.toString(),
        KEY_LAST_SEEN to lastSeenEpochMs.toString(),
        KEY_GREETING_DAY to lastGreetingDay,
        KEY_X to x01.toString(),
    )

    companion object {
        const val KEY_HUNGER = "hunger"
        const val KEY_HAPPINESS = "happiness"
        const val KEY_LAST_SEEN = "lastSeenEpochMs"
        const val KEY_GREETING_DAY = "lastGreetingDay"
        const val KEY_X = "x01"

        /** Returns null when nothing was stored yet; unparsable fields fall back to defaults. */
        fun fromMap(map: Map<String, String>): PetState? {
            if (map[KEY_LAST_SEEN] == null && map[KEY_HUNGER] == null) return null
            val defaults = PetState()
            return PetState(
                hunger = (map[KEY_HUNGER]?.toFloatOrNull() ?: defaults.hunger).coerceIn(0f, 100f),
                happiness = (map[KEY_HAPPINESS]?.toFloatOrNull() ?: defaults.happiness).coerceIn(0f, 100f),
                lastSeenEpochMs = map[KEY_LAST_SEEN]?.toLongOrNull() ?: 0L,
                lastGreetingDay = map[KEY_GREETING_DAY] ?: "",
                x01 = (map[KEY_X]?.toFloatOrNull() ?: defaults.x01).coerceIn(0f, 1f),
            )
        }
    }
}

/** Tunables for the turtle's behaviour. Distances are fractions of the walkable width. */
data class PetConfig(
    val walkSpeed: Float = 0.05f,
    val hungerPerHour: Float = 6f,
    val happinessLossPerHour: Float = 4f,
    val feedAmount: Float = 30f,
    val feedHappiness: Float = 10f,
    val petHappiness: Float = 5f,
    /** Below this hunger the turtle refuses food. */
    val fullThreshold: Float = 10f,
    val feedCooldownSeconds: Float = 3f,
    val eatDurationSeconds: Float = 2.4f,
    val hideDurationSeconds: Float = 2f,
    val hideChance: Float = 0.12f,
    val minWalkDistance: Float = 0.15f,
    val lettuceOffset: Float = 0.12f,
    val eatDistance: Float = 0.07f,
    val sleepStartHour: Int = 23,
    val sleepEndHour: Int = 7,
    val wakeGraceSeconds: Float = 60f,
    val grumpyPenalty: Float = 2f,
    val bubbleSeconds: Float = 4f,
    val bubbleCooldownSeconds: Float = 20f,
    val chatterMinSeconds: Float = 45f,
    val chatterMaxSeconds: Float = 120f,
    val maxOfflineMs: Long = 7L * 24 * 60 * 60 * 1000,
    val maxFrameDt: Float = 0.1f,
    /** Fall acceleration in sprite heights per second squared. */
    val gravity: Float = 9f,
    /** Released lower than this (in sprite heights) counts as being put down. */
    val landingThreshold: Float = 0.05f,
)

object PetRules {
    /**
     * Brings [state] up to [nowEpochMs]: hunger grows and happiness fades with real time,
     * capped at [PetConfig.maxOfflineMs]. A state that was never seen, or a clock that went
     * backwards, only gets its timestamp set.
     */
    fun decayed(state: PetState, nowEpochMs: Long, config: PetConfig = PetConfig()): PetState {
        if (state.lastSeenEpochMs <= 0L || nowEpochMs <= state.lastSeenEpochMs) {
            return state.copy(lastSeenEpochMs = nowEpochMs)
        }
        val elapsedMs = (nowEpochMs - state.lastSeenEpochMs).coerceAtMost(config.maxOfflineMs)
        val hours = elapsedMs / 3_600_000f
        return state.copy(
            hunger = (state.hunger + config.hungerPerHour * hours).coerceIn(0f, 100f),
            happiness = (state.happiness - config.happinessLossPerHour * hours).coerceIn(0f, 100f),
            lastSeenEpochMs = nowEpochMs,
        )
    }

    /** Whether [hour] falls inside the sleep window, which may wrap past midnight. */
    fun isSleepHour(hour: Int, config: PetConfig = PetConfig()): Boolean =
        if (config.sleepStartHour > config.sleepEndHour) {
            hour >= config.sleepStartHour || hour < config.sleepEndHour
        } else {
            hour >= config.sleepStartHour && hour < config.sleepEndHour
        }
}
