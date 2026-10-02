package com.llgl.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetStateTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun `a state that was never seen only gets its timestamp`() {
        val fresh = PetRules.decayed(PetState(hunger = 25f, happiness = 70f, lastSeenEpochMs = 0L), now)
        assertEquals(25f, fresh.hunger, 1e-6f)
        assertEquals(70f, fresh.happiness, 1e-6f)
        assertEquals(now, fresh.lastSeenEpochMs)
    }

    @Test
    fun `decay follows the hourly rates and clamps to the 0-100 range`() {
        val after = PetRules.decayed(PetState(hunger = 10f, happiness = 50f, lastSeenEpochMs = now - 5 * hour), now)
        assertEquals(40f, after.hunger, 1e-3f)
        assertEquals(30f, after.happiness, 1e-3f)

        val starved = PetRules.decayed(PetState(hunger = 90f, happiness = 5f, lastSeenEpochMs = now - 10 * hour), now)
        assertEquals(100f, starved.hunger, 1e-6f)
        assertEquals(0f, starved.happiness, 1e-6f)
    }

    @Test
    fun `time away is capped at a week`() {
        val month = PetRules.decayed(PetState(hunger = 0f, happiness = 100f, lastSeenEpochMs = now - 30L * 24 * hour), now)
        val week = PetRules.decayed(PetState(hunger = 0f, happiness = 100f, lastSeenEpochMs = now - 7L * 24 * hour), now)
        assertEquals(week.hunger, month.hunger, 1e-6f)
        assertEquals(week.happiness, month.happiness, 1e-6f)
    }

    @Test
    fun `a clock that went backwards does not decay`() {
        val back = PetRules.decayed(PetState(hunger = 20f, happiness = 60f, lastSeenEpochMs = now + hour), now)
        assertEquals(20f, back.hunger, 1e-6f)
        assertEquals(60f, back.happiness, 1e-6f)
        assertEquals(now, back.lastSeenEpochMs)
    }

    @Test
    fun `the sleep window wraps past midnight`() {
        assertTrue(PetRules.isSleepHour(23))
        assertTrue(PetRules.isSleepHour(0))
        assertTrue(PetRules.isSleepHour(6))
        assertFalse(PetRules.isSleepHour(7))
        assertFalse(PetRules.isSleepHour(12))
        assertFalse(PetRules.isSleepHour(22))
    }

    @Test
    fun `the codec tolerates garbage and reports nothing stored`() {
        assertNull(PetState.fromMap(emptyMap()))
        val parsed = PetState.fromMap(mapOf(PetState.KEY_HUNGER to "oops", PetState.KEY_LAST_SEEN to "123", PetState.KEY_X to "9"))
        assertEquals(PetState().hunger, parsed!!.hunger, 1e-6f)
        assertEquals(123L, parsed.lastSeenEpochMs)
        assertEquals(1f, parsed.x01, 1e-6f)
    }
}
