package com.llgl.sandbox.render

import com.llgl.sandbox.sim.Elements

/** The colours of every element: a few shades each so a pile of cells reads as grains, not a flat fill. */
object Palette {
    const val BACKGROUND = 0xFF101418.toInt()
    const val SPARK = 0xFFFFF9B0.toInt()

    private val shades = arrayOfNulls<IntArray>(Elements.COUNT)

    private fun set(e: Byte, vararg colors: Long) {
        shades[e.toInt()] = IntArray(colors.size) { colors[it].toInt() }
    }

    init {
        set(Elements.EMPTY, 0xFF101418)
        set(Elements.SAND, 0xFFE2C275, 0xFFD4B265, 0xFFEFD08A, 0xFFDBBA6E)
        set(Elements.WATER, 0xFF3E86D8, 0xFF3B7FD0, 0xFF4A8EDF, 0xFF3F88DA)
        set(Elements.OIL, 0xFF8E6A2B, 0xFF7A5A22, 0xFF966F2E, 0xFF84622A)
        set(Elements.ACID, 0xFF8BE04A, 0xFF74C93A, 0xFF9AEA58, 0xFF80D644)
        set(Elements.LAVA, 0xFFFF6A1F, 0xFFE0501A, 0xFFFF7A2B, 0xFFF0601F)
        set(Elements.WALL, 0xFF6E6E76, 0xFF62626A, 0xFF74747C, 0xFF68686F)
        set(Elements.WOOD, 0xFF8B5A2B, 0xFF7C4F25, 0xFF94612F, 0xFF835528)
        set(Elements.PLANT, 0xFF3FA34D, 0xFF2E8B3A, 0xFF49B257, 0xFF379844)
        set(Elements.GLASS, 0xFFBFE3EC, 0xFFA9D3DF, 0xFFCDEBF2, 0xFFB5DCE6)
        set(Elements.STONE, 0xFF4F4A48, 0xFF46413F, 0xFF57514F, 0xFF4B4644)
        set(Elements.FIRE, 0xFFFF9A2B)
        set(Elements.SMOKE, 0xFF4A4A52)
        set(Elements.STEAM, 0xFFC7CFD6)
        set(Elements.GUNPOWDER, 0xFF3A3A3E, 0xFF2F2F33, 0xFF424246, 0xFF36363A)
        set(Elements.WIRE, 0xFFC0C4CC, 0xFFB4B8C0, 0xFFC8CCD4, 0xFFBABEC6)
        set(Elements.BATTERY, 0xFFD9A400, 0xFFE6C340)
        set(Elements.SWITCH_OFF, 0xFF8A4A4A, 0xFF7A4040)
        set(Elements.SWITCH_ON, 0xFF40A070, 0xFF389862)
        set(Elements.LAMP, 0xFF5A4A20, 0xFFFFE97A)
        set(Elements.HEATER, 0xFF7A3B3B, 0xFFFF4A3A)
        set(Elements.LIGHTNING, 0xFFFFFFFF, 0xFFCFEBFF)
    }

    /** The representative colour of an element, for palette chips. */
    fun base(e: Byte): Int = shades[e.toInt()]?.get(0) ?: 0xFFFF00FF.toInt()

    fun shade(e: Byte, index: Int): Int {
        val s = shades[e.toInt()] ?: return 0xFFFF00FF.toInt()
        return s[index % s.size]
    }

    /** Linear blend of two ARGB colours, [t] = 0 gives [a]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val ia = 1f - k
        val aa = ((a ushr 24) and 0xFF) * ia + ((b ushr 24) and 0xFF) * k
        val rr = ((a shr 16) and 0xFF) * ia + ((b shr 16) and 0xFF) * k
        val gg = ((a shr 8) and 0xFF) * ia + ((b shr 8) and 0xFF) * k
        val bb = (a and 0xFF) * ia + (b and 0xFF) * k
        return (aa.toInt() shl 24) or (rr.toInt() shl 16) or (gg.toInt() shl 8) or bb.toInt()
    }
}
