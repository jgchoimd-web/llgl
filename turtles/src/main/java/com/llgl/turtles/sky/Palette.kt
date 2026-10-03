package com.llgl.turtles.sky

/**
 * The sky for one moment of the day. [top] and [bottom] are the gradient's ARGB ends, [stars]
 * 0..1 is how visible the stars are, [night] 0..1 is how dark things in the sky should look.
 */
class SkyColors(val top: Int, val bottom: Int, val stars: Float, val night: Float)

/** Sky colours by the hour (0..24), interpolated between a handful of keyframes. */
object Palette {
    private class Key(val hour: Float, val top: Int, val bottom: Int, val stars: Float, val night: Float)

    private val keys = listOf(
        Key(0f, 0xFF0B1030.toInt(), 0xFF1B2550.toInt(), 1f, 1f),
        Key(4.5f, 0xFF10163A.toInt(), 0xFF2B2F6A.toInt(), 0.9f, 0.95f),
        Key(6f, 0xFF3A4A8F.toInt(), 0xFFE39A7A.toInt(), 0.25f, 0.4f),
        Key(7.5f, 0xFF5B8FD8.toInt(), 0xFFF7C9A0.toInt(), 0f, 0.1f),
        Key(10f, 0xFF4F8FE0.toInt(), 0xFFBFE3FF.toInt(), 0f, 0f),
        Key(15f, 0xFF3F86E8.toInt(), 0xFFA9DBFF.toInt(), 0f, 0f),
        Key(18f, 0xFF4E6FC4.toInt(), 0xFFFFB07A.toInt(), 0f, 0.1f),
        Key(19.5f, 0xFF2A2B6A.toInt(), 0xFFC06A6A.toInt(), 0.35f, 0.5f),
        Key(21f, 0xFF10163A.toInt(), 0xFF2B2F6A.toInt(), 0.9f, 0.95f),
        Key(24f, 0xFF0B1030.toInt(), 0xFF1B2550.toInt(), 1f, 1f),
    )

    fun at(hourIn: Float): SkyColors {
        val hour = ((hourIn % 24f) + 24f) % 24f
        var i = 0
        while (i < keys.size - 2 && keys[i + 1].hour <= hour) i++
        val a = keys[i]
        val b = keys[i + 1]
        val t = ((hour - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
        return SkyColors(
            lerpColor(a.top, b.top, t),
            lerpColor(a.bottom, b.bottom, t),
            a.stars + (b.stars - a.stars) * t,
            a.night + (b.night - a.night) * t,
        )
    }

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
