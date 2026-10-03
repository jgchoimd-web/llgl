package com.llgl.app.world

import kotlin.math.hypot
import kotlin.math.min

/** Something rooted in the soil. [size] is in pixels and grows with the terrarium's age. */
class Plant(val kind: Kind, val x: Float, val y: Float, val size: Float, val seed: Int) {
    enum class Kind { MOSS, FERN, MUSHROOM, SPROUT }

    /** The base marbles and creatures must go around (moss is soft and has none). */
    val obstacle: Circle? = when (kind) {
        Kind.FERN -> Circle(x, y, 2.5f + size * 0.25f)
        Kind.MUSHROOM -> Circle(x, y, size * 0.8f)
        Kind.SPROUT -> Circle(x, y, 1.5f)
        Kind.MOSS -> null
    }

    /** How much room the plant takes visually, used to spread plants out. */
    val footprint: Float
        get() = when (kind) {
            Kind.MOSS -> size
            Kind.FERN -> size * 0.9f
            Kind.MUSHROOM -> size * 1.5f
            Kind.SPROUT -> 4f
        }
}

object Plants {
    /** 0.6 at install, growing to full size over a week. */
    fun growth(ageDays: Float): Float = 0.6f + 0.4f * min(1f, ageDays / 7f).coerceAtLeast(0f)

    fun generate(rng: Rng, width: Float, height: Float, ageDays: Float): List<Plant> {
        val out = ArrayList<Plant>()
        val growth = growth(ageDays)
        val wanted = 7 + rng.nextInt(3) + min(3, ageDays.toInt().coerceAtLeast(0))
        var tries = 0
        while (out.size < wanted && tries++ < 400) {
            val roll = rng.nextFloat()
            val kind = when {
                roll < 0.38f -> Plant.Kind.MOSS
                roll < 0.66f -> Plant.Kind.FERN
                roll < 0.86f -> Plant.Kind.MUSHROOM
                else -> Plant.Kind.SPROUT
            }
            val size = when (kind) {
                Plant.Kind.MOSS -> rng.range(10f, 20f)
                Plant.Kind.FERN -> rng.range(12f, 20f)
                Plant.Kind.MUSHROOM -> rng.range(3f, 5f)
                Plant.Kind.SPROUT -> 3f
            } * growth
            val margin = 18f
            val x = rng.range(margin, width - margin)
            val y = rng.range(margin, height - margin)
            val candidate = Plant(kind, x, y, size, rng.nextInt(1 shl 20))
            if (out.any { hypot(it.x - x, it.y - y) < it.footprint + candidate.footprint + 4f }) continue
            out += candidate
        }
        return out
    }
}
