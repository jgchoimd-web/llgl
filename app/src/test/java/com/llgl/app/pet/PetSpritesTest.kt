package com.llgl.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSpritesTest {

    @Test
    fun `every turtle frame is 24 by 16 and uses only palette characters`() {
        for (pose in PetSprites.Pose.entries) {
            val frame = PetSprites.frame(pose)
            assertEquals("$pose height", PetSprites.HEIGHT, frame.height)
            frame.rows.forEachIndexed { index, row ->
                assertEquals("$pose row $index width", PetSprites.WIDTH, row.length)
                for (ch in row) assertTrue("$pose row $index has unknown char '$ch'", ch in PetSprites.PALETTE)
            }
        }
    }

    @Test
    fun `the lettuce is 8 by 8`() {
        assertEquals(PetSprites.LETTUCE_SIZE, PetSprites.LETTUCE.width)
        assertEquals(PetSprites.LETTUCE_SIZE, PetSprites.LETTUCE.height)
    }

    @Test
    fun `transparent pixels are zero and mirroring flips each row`() {
        val frame = PetSprites.WALK_A
        val pixels = frame.pixels()
        val mirrored = frame.pixels(mirrored = true)
        assertEquals(0, pixels[0])
        for (r in 0 until frame.height) {
            for (c in 0 until frame.width) {
                assertEquals(pixels[r * frame.width + c], mirrored[r * frame.width + (frame.width - 1 - c)])
            }
        }
        assertTrue(pixels.any { it != 0 })
    }

    @Test
    fun `walking alternates frames with a bob and every activity has a cel`() {
        val a = PetSprites.celFor(Activity.WALK, 0f)
        val b = PetSprites.celFor(Activity.WALK, 0.25f)
        assertEquals(PetSprites.Pose.WALK_A, a.pose)
        assertEquals(PetSprites.Pose.WALK_B, b.pose)
        assertEquals(0, a.bob)
        assertEquals(1, b.bob)
        for (activity in Activity.entries) PetSprites.celFor(activity, 1.23f)
    }
}
