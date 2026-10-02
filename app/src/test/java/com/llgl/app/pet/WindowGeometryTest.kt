package com.llgl.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowGeometryTest {

    private val usable = 1080
    private val spriteW = 264
    private val spriteH = 176
    private val wide = 792
    private val extra = 198

    @Test
    fun `plain mode is exactly the sprite at its position`() {
        val layout = WindowGeometry.layout(0.5f, 0f, false, usable, spriteW, spriteH, wide, extra)
        assertEquals(408, layout.x)
        assertEquals(0, layout.y)
        assertEquals(spriteW, layout.width)
        assertEquals(spriteH, layout.height)
        assertEquals(0, layout.spriteOffsetX)
    }

    @Test
    fun `wide mode centres the box on the turtle`() {
        val layout = WindowGeometry.layout(0.5f, 0f, true, usable, spriteW, spriteH, wide, extra)
        assertEquals(wide, layout.width)
        assertEquals(spriteH + extra, layout.height)
        assertEquals(408 - (wide - spriteW) / 2, layout.x)
        assertEquals((wide - spriteW) / 2, layout.spriteOffsetX)
    }

    @Test
    fun `wide mode is clamped at both screen edges and the sprite stays put`() {
        val left = WindowGeometry.layout(0f, 0f, true, usable, spriteW, spriteH, wide, extra)
        assertEquals(0, left.x)
        assertEquals(0, left.spriteOffsetX)

        val right = WindowGeometry.layout(1f, 0f, true, usable, spriteW, spriteH, wide, extra)
        assertEquals(usable - wide, right.x)
        assertEquals(usable - spriteW, right.x + right.spriteOffsetX)
    }

    @Test
    fun `height follows the lift and x snaps to the sprite pixel grid`() {
        val lifted = WindowGeometry.layout(0.5f, 1.5f, false, usable, spriteW, spriteH, wide, extra)
        assertEquals(264, lifted.y)

        val snapped = WindowGeometry.layout(0.5f, 0f, false, usable, spriteW, spriteH, wide, extra, quantum = 11)
        assertEquals(0, snapped.x % 11)

        // At the far edge the snapped position may stop up to one sprite pixel short, never past it.
        val walkable = usable - spriteW
        val edge = WindowGeometry.layout(1f, 0f, false, usable, spriteW, spriteH, wide, extra, quantum = 11)
        assertEquals(0, edge.x % 11)
        assertTrue(edge.x in (walkable - 11)..walkable)
    }
}
