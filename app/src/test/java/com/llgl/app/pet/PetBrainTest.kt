package com.llgl.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FakeClock(
    var epochMs: Long = 1_700_000_000_000L,
    var hour: Int = 12,
    var day: String = "2026-1",
) : PetClock {
    override fun nowEpochMs(): Long = epochMs
    override fun hourOfDay(): Int = hour
    override fun localDayKey(): String = day
}

class PetBrainTest {

    private val clock = FakeClock()

    private fun brain(restored: PetState? = null, seed: Int = 7): PetBrain =
        PetBrain(Random(seed), clock, restored = restored)

    /** Runs the brain at 60 fps, moving the fake wall clock along, and collects the events. */
    private fun PetBrain.run(seconds: Float): List<PetEvent> {
        val seen = ArrayList<PetEvent>()
        val frames = (seconds * 60f).toInt()
        repeat(frames) {
            clock.epochMs += 16
            advance(1f / 60f)
            seen += drainEvents()
        }
        return seen
    }

    @Test
    fun `wanders inside the walkable range and turns around`() {
        val brain = brain()
        val facings = HashSet<Facing>()
        var moved = 0f
        var last = brain.x01
        repeat(600 * 60) {
            clock.epochMs += 16
            brain.advance(1f / 60f)
            assertTrue(brain.x01 in 0f..1f)
            moved += kotlin.math.abs(brain.x01 - last)
            last = brain.x01
            facings += brain.facing
        }
        assertTrue("expected the turtle to walk", moved > 2f)
        assertEquals(setOf(Facing.LEFT, Facing.RIGHT), facings)
    }

    @Test
    fun `a tap sends it into its shell and it comes back out`() {
        val brain = brain()
        brain.onTap()
        assertEquals(Activity.HIDE, brain.activity)
        brain.run(3f)
        assertNotEquals(Activity.HIDE, brain.activity)
    }

    @Test
    fun `dragging carries it and releasing high up makes it fall`() {
        val brain = brain()
        brain.onDragStart()
        assertEquals(Activity.CARRIED, brain.activity)
        brain.onDrag(0.3f, 2f)
        assertEquals(0.3f, brain.x01, 1e-6f)
        assertEquals(2f, brain.yUnits, 1e-6f)

        brain.onDragEnd(0.3f, 2f)
        assertEquals(Activity.FALL, brain.activity)
        val events = brain.run(3f)
        assertEquals(0f, brain.yUnits, 1e-6f)
        assertTrue(events.any { it is PetEvent.Landed })
        assertTrue(brain.activity == Activity.IDLE || brain.activity == Activity.WALK)
    }

    @Test
    fun `releasing near the ground lands immediately and clamps the position`() {
        val brain = brain()
        brain.onDragStart()
        brain.onDragEnd(1.7f, 0.02f)
        assertEquals(Activity.IDLE, brain.activity)
        assertEquals(1f, brain.x01, 1e-6f)
        assertEquals(0f, brain.yUnits, 1e-6f)
    }

    @Test
    fun `feeding puts down a lettuce and the turtle walks over and eats it`() {
        val brain = brain(PetState(hunger = 60f, happiness = 50f, lastSeenEpochMs = clock.epochMs))
        assertTrue(brain.feed())
        assertNotNull(brain.lettuceX01)
        assertEquals(Activity.WALK, brain.activity)

        val events = brain.run(12f)
        assertNull(brain.lettuceX01)
        assertEquals(30f, brain.hunger, 0.5f)
        assertEquals(60f, brain.happiness, 0.5f)
        assertTrue(events.any { it is PetEvent.Spoke && it.line == Line.YUM })
    }

    @Test
    fun `a full turtle refuses food and feeding has a cooldown`() {
        val full = brain(PetState(hunger = 5f, lastSeenEpochMs = clock.epochMs))
        assertFalse(full.feed())
        assertEquals(Line.FULL, full.bubble)
        assertNull(full.lettuceX01)

        val hungry = brain(PetState(hunger = 50f, lastSeenEpochMs = clock.epochMs))
        assertTrue(hungry.feed())
        assertFalse(hungry.feed())
    }

    @Test
    fun `petting raises happiness`() {
        val brain = brain(PetState(happiness = 50f, lastSeenEpochMs = clock.epochMs))
        assertTrue(brain.pet())
        assertEquals(55f, brain.happiness, 1e-6f)
        assertEquals(Line.PETTED, brain.bubble)
    }

    @Test
    fun `time away makes it hungrier and sadder`() {
        val twoHoursAgo = clock.epochMs - 2L * 60 * 60 * 1000
        val brain = brain(PetState(hunger = 20f, happiness = 60f, lastSeenEpochMs = twoHoursAgo))
        clock.epochMs += 1000
        brain.advance(1f / 60f)
        assertEquals(32f, brain.hunger, 0.05f)
        assertEquals(52f, brain.happiness, 0.05f)
    }

    @Test
    fun `sleeps at night, wakes grumpy when tapped and dozes off again`() {
        clock.hour = 23
        val brain = brain(PetState(happiness = 50f, lastSeenEpochMs = clock.epochMs))
        brain.run(1f)
        assertEquals(Activity.SLEEP, brain.activity)

        brain.onTap()
        assertNotEquals(Activity.SLEEP, brain.activity)
        assertEquals(Line.SLEEPY, brain.bubble)
        assertEquals(48f, brain.happiness, 0.05f)

        brain.run(10f)
        assertNotEquals("should stay awake during the grace period", Activity.SLEEP, brain.activity)

        clock.epochMs += 61_000
        brain.run(2f)
        assertEquals(Activity.SLEEP, brain.activity)
    }

    @Test
    fun `wakes in the morning and greets once per day`() {
        clock.hour = 23
        val brain = brain(PetState(lastSeenEpochMs = clock.epochMs))
        brain.run(1f)
        assertEquals(Activity.SLEEP, brain.activity)

        clock.hour = 7
        clock.day = "2026-2"
        val morning = brain.run(1f)
        assertNotEquals(Activity.SLEEP, brain.activity)
        assertEquals(1, morning.count { it is PetEvent.Spoke && it.line == Line.MORNING })

        val later = brain.run(30f)
        assertEquals(0, later.count { it is PetEvent.Spoke && it.line == Line.MORNING })
        assertEquals("2026-2", brain.exportState().lastGreetingDay)
    }

    @Test
    fun `food is ignored while asleep`() {
        clock.hour = 2
        val brain = brain(PetState(hunger = 80f, lastSeenEpochMs = clock.epochMs))
        brain.run(1f)
        assertEquals(Activity.SLEEP, brain.activity)
        assertFalse(brain.feed())
        assertEquals(Line.ZZZ, brain.bubble)
        assertEquals(80f, brain.hunger, 0.1f)
    }

    @Test
    fun `chatters now and then and the bubble goes away`() {
        val brain = brain()
        val events = brain.run(300f)
        assertTrue(events.any { it is PetEvent.Spoke })
        // Whatever it last said has had time to fade unless it just spoke.
        brain.run(5f)
        assertTrue(brain.bubble == null || brain.bubble in Line.IDLE_LINES || brain.bubble == Line.STARTLED || brain.bubble == Line.LANDED)
    }

    @Test
    fun `state round-trips through the map codec`() {
        val brain = brain(PetState(hunger = 33f, happiness = 44f, lastSeenEpochMs = clock.epochMs, lastGreetingDay = "2026-9", x01 = 0.25f))
        val exported = brain.exportState()
        assertEquals(exported, PetState.fromMap(exported.toMap()))
    }
}
