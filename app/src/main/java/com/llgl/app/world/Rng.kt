package com.llgl.app.world

/** A small seeded xorshift generator, so a terrarium can be rebuilt exactly from its seed. */
class Rng(seed: Long) {
    private var state: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        var s = state
        s = s xor (s shl 13)
        s = s xor (s ushr 7)
        s = s xor (s shl 17)
        state = s
        return s
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = ((nextLong() ushr 40).toInt() and 0xFFFFFF) / 16777216f

    fun range(from: Float, until: Float): Float = from + (until - from) * nextFloat()

    fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt().coerceIn(0, bound - 1)

    fun chance(probability: Float): Boolean = nextFloat() < probability
}
