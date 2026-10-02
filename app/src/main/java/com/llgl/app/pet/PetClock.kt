package com.llgl.app.pet

import java.util.Calendar

/** Wall-clock access for the brain, so tests can fake the time of day. */
interface PetClock {
    fun nowEpochMs(): Long

    /** 0..23 in the device's local time zone. */
    fun hourOfDay(): Int

    /** A key that changes once per local calendar day, e.g. "2026-275". */
    fun localDayKey(): String
}

/** [java.util.Calendar] based clock; java.time would need desugaring on minSdk 24. */
class SystemPetClock : PetClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()

    override fun hourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    override fun localDayKey(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }
}
