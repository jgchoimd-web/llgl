package com.llgl.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTest {

    private val camera = Camera(width = 1000f, height = 2000f)

    @Test
    fun `nearer objects are drawn lower and wider`() {
        assertTrue(camera.y(2f) > camera.y(8f))
        assertTrue(camera.roadHalf(2f) > camera.roadHalf(8f))
    }

    @Test
    fun `the slab runs from below the screen up to the lip`() {
        assertTrue(camera.y(GameEngine.NEAR) > camera.height)
        assertEquals(camera.lipY, camera.y(GameEngine.FAR), 1e-3f)
    }

    @Test
    fun `perspective is mild so the far edge stays wide`() {
        assertTrue(camera.scale(GameEngine.FAR) > 0.5f)
        assertEquals(1f, camera.scale(GameEngine.NEAR), 1e-6f)
    }

    @Test
    fun `the road centre is the screen centre and the marble stays on screen`() {
        assertEquals(camera.centerX, camera.x(GameEngine.MARBLE_D, 0f), 1e-3f)
        val marbleR = GameEngine.MARBLE_HALF_U * camera.roadHalf(GameEngine.MARBLE_D)
        val rightMost = camera.x(GameEngine.MARBLE_D, GameEngine.MAX_U) + marbleR
        assertTrue(rightMost <= camera.width)
    }
}
