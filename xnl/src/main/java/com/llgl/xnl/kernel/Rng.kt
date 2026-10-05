package com.llgl.xnl.kernel

/** A small seeded xorshift, so a scene is reproducible in tests. */
class Rng(seed: Int) {
    private var s = if (seed == 0) 0x3779B97F else seed

    fun nextInt(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** 0 ≤ value < 1. */
    fun nextFloat(): Float = (nextInt() ushr 8) / 16777216f

    fun range(from: Float, to: Float): Float = from + (to - from) * nextFloat()

    fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt().coerceIn(0, bound - 1)
}
