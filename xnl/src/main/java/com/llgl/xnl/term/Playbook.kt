package com.llgl.xnl.term

/**
 * Picks what runs next. Live commands (uptime, load, clocks) have short periods and come round often,
 * static ones (kernel version, cpu info) have long periods, nothing repeats within its period while
 * something else is due, the same command never follows itself, and a command that failed is dropped.
 */
class Playbook(
    private val rng: Rng,
    commands: List<Command>,
    broken: Set<String> = emptySet(),
    opening: List<String> = Commands.OPENING,
) {
    private val pool = commands.filter { it.text !in broken }.toMutableList()
    private val lastRun = HashMap<String, Float>()
    private val recent = ArrayDeque<String>()
    private val opening = ArrayDeque(opening)

    /** Told when a command is dropped, so the host can remember it across sessions. */
    var onBroken: ((Command) -> Unit)? = null

    val commands: List<Command> get() = pool

    fun next(now: Float): Command? {
        if (pool.isEmpty()) return null
        while (opening.isNotEmpty()) {
            val text = opening.removeFirst()
            val c = pool.firstOrNull { it.text == text } ?: continue
            return pick(c, now)
        }
        var candidates = pool.filter { due(it, now) && it.text !in recent }
        if (candidates.isEmpty()) candidates = pool.filter { due(it, now) }
        if (candidates.isEmpty()) candidates = pool.filter { it.text !in recent }
        if (candidates.isEmpty()) candidates = pool
        var r = rng.nextFloat() * candidates.sumOf { it.weight.toDouble() }.toFloat()
        for (c in candidates) {
            r -= c.weight
            if (r <= 0f) return pick(c, now)
        }
        return pick(candidates.last(), now)
    }

    fun markBroken(command: Command) {
        if (pool.remove(command)) onBroken?.invoke(command)
    }

    private fun due(c: Command, now: Float): Boolean {
        val last = lastRun[c.text] ?: return true
        return now - last >= c.period
    }

    private fun pick(c: Command, now: Float): Command {
        lastRun[c.text] = now
        recent.addLast(c.text)
        val keep = minOf(RECENT, pool.size - 1)
        while (recent.size > keep && recent.isNotEmpty()) recent.removeFirst()
        return c
    }

    private companion object {
        const val RECENT = 3
    }
}
