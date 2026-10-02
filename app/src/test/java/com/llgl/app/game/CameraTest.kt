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
    fun `the road converges on the horizon`() {
        assertEquals(camera.horizonY, camera.y(1e6f), 1f)
        assertEquals(camera.centerX, camera.x(1e6f, 1f), 2f)
    }

    @Test
    fun `curve bends only the far road`() {
        val bent = Camera(width = 1000f, height = 2000f, curve = 1f)
        assertEquals(0f, bent.bend(GameEngine.NEAR), 1e-3f)
        assertTrue(bent.bend(GameEngine.FAR) > 0f)
        assertEquals(camera.x(GameEngine.NEAR, 0.5f), bent.x(GameEngine.NEAR, 0.5f), 1e-3f)
    }
}
