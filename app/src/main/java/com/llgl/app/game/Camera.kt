package com.llgl.app.game

/**
 * Projection of road coordinates onto the screen for a high camera looking down the slab:
 * the near plane ([GameEngine.NEAR]) sits just below the bottom edge, the far edge
 * ([GameEngine.FAR]) is the slab's lip at [lipY], and widths shrink only mildly with distance.
 */
class Camera(val width: Float, val height: Float) {
    val centerX = width / 2f

    /** Screen y of the slab's far edge, where obstacles appear. */
    val lipY = height * 0.26f

    private val nearY = height * 1.02f
    private val roadHalfNear = width * 0.75f
    private val farScale = 0.58f
    private val k = (1f / farScale - 1f) / (GameEngine.FAR - GameEngine.NEAR)

    /** 1 at the near plane, [farScale] at the far edge. */
    fun scale(d: Float): Float = 1f / (1f + k * (d - GameEngine.NEAR))

    fun y(d: Float): Float = lipY + (nearY - lipY) * (scale(d) - farScale) / (1f - farScale)

    fun roadHalf(d: Float): Float = roadHalfNear * scale(d)

    fun x(d: Float, u: Float): Float = centerX + u * roadHalf(d)
}
