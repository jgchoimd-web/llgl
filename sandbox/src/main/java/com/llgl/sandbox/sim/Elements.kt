package com.llgl.sandbox.sim

/**
 * Everything that can sit in a cell, with its fixed properties. Ids are stored as bytes in saved
 * grids, so they must never be renumbered.
 */
object Elements {
    const val EMPTY: Byte = 0
    const val SAND: Byte = 1
    const val WATER: Byte = 2
    const val OIL: Byte = 3
    const val ACID: Byte = 4
    const val LAVA: Byte = 5
    const val WALL: Byte = 6
    const val WOOD: Byte = 7
    const val PLANT: Byte = 8
    const val GLASS: Byte = 9
    const val STONE: Byte = 10
    const val FIRE: Byte = 11
    const val SMOKE: Byte = 12
    const val STEAM: Byte = 13
    const val GUNPOWDER: Byte = 14
    const val WIRE: Byte = 15
    const val BATTERY: Byte = 16
    const val SWITCH_OFF: Byte = 17
    const val SWITCH_ON: Byte = 18
    const val LAMP: Byte = 19
    const val HEATER: Byte = 20
    const val LIGHTNING: Byte = 21
    const val COUNT = 22

    const val KIND_EMPTY = 0
    const val KIND_POWDER = 1
    const val KIND_LIQUID = 2
    const val KIND_GAS = 3
    const val KIND_SOLID = 4

    val kind = IntArray(COUNT)

    /** Heavier sinks through lighter; gases are lightest. */
    val density = IntArray(COUNT)

    /** Chance out of 256, per tick per burning neighbour, that this catches fire. */
    val flammability = IntArray(COUNT)

    /** How long (ticks) the fire burns once this ignites. */
    val burnLife = IntArray(COUNT)

    /** How far a liquid looks sideways for a lower spot each tick. */
    val dispersion = IntArray(COUNT)

    /** Carries electric charge. */
    val conducts = BooleanArray(COUNT)

    /** Charged for this many ticks, then unable to recharge for [cooldown] ticks; makes pulses travel. */
    val cooldown = IntArray(COUNT)

    val label = Array(COUNT) { "" }

    /** What the palette offers, in order. */
    val palette: List<Byte> = listOf(
        SAND, WATER, OIL, ACID, LAVA, GUNPOWDER, FIRE,
        WALL, WOOD, PLANT, GLASS,
        WIRE, BATTERY, SWITCH_OFF, LAMP, HEATER,
    )

    private fun define(id: Byte, kindOf: Int, name: String, dens: Int = 0, flam: Int = 0, burn: Int = 0, spread: Int = 0, conduct: Boolean = false, cool: Int = 0) {
        val i = id.toInt()
        kind[i] = kindOf
        density[i] = dens
        flammability[i] = flam
        burnLife[i] = burn
        dispersion[i] = spread
        conducts[i] = conduct
        cooldown[i] = cool
        label[i] = name
    }

    init {
        define(EMPTY, KIND_EMPTY, "지우개")
        define(SAND, KIND_POWDER, "모래", dens = 5)
        define(GUNPOWDER, KIND_POWDER, "화약", dens = 5, flam = 255, burn = 40)
        define(WATER, KIND_LIQUID, "물", dens = 3, spread = 5, conduct = true, cool = 2)
        define(OIL, KIND_LIQUID, "기름", dens = 2, flam = 90, burn = 50, spread = 3)
        define(ACID, KIND_LIQUID, "산", dens = 3, spread = 3)
        define(LAVA, KIND_LIQUID, "용암", dens = 4, spread = 1)
        define(WALL, KIND_SOLID, "벽")
        define(WOOD, KIND_SOLID, "나무", flam = 10, burn = 80)
        define(PLANT, KIND_SOLID, "식물", flam = 30, burn = 40)
        define(GLASS, KIND_SOLID, "유리")
        define(STONE, KIND_SOLID, "돌")
        define(FIRE, KIND_GAS, "불", dens = 0)
        define(SMOKE, KIND_GAS, "연기", dens = 1)
        define(STEAM, KIND_GAS, "증기", dens = 1)
        define(LIGHTNING, KIND_GAS, "번개", dens = 0)
        define(WIRE, KIND_SOLID, "전선", conduct = true, cool = 8)
        define(BATTERY, KIND_SOLID, "배터리")
        define(SWITCH_OFF, KIND_SOLID, "스위치")
        define(SWITCH_ON, KIND_SOLID, "스위치", conduct = true, cool = 8)
        define(LAMP, KIND_SOLID, "전구", conduct = true, cool = 8)
        define(HEATER, KIND_SOLID, "전열선", conduct = true, cool = 8)
    }

    fun isFluid(e: Byte): Boolean {
        val k = kind[e.toInt()]
        return k == KIND_LIQUID || k == KIND_GAS
    }
}
