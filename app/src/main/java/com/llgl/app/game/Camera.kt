package com.llgl.app.game

/**
 * Pseudo-3D projection of road coordinates onto the screen using 1/d perspective:
 * things at [GameEngine.NEAR] sit at the bottom edge and far things converge on the horizon.
 * [curve] (-1..1) bends the far end of the road sideways for a winding-descent look.
 */
class Camera(val width: Float, val height: Float, val curve: Float = 0f) {
    val centerX = width / 2f
    val horizonY = height * 0.30f
    private val bottomY = height * 1.02f
    private val roadHalfNear = width * 0.62f

    /** 1 at the near plane, approaching 0 at the horizon. */
    fun scale(d: Float): Float = GameEngine.NEAR / d

    fun y(d: Float): Float = horizonY + (bottomY - horizonY) * scale(d)

    fun roadHalf(d: Float): Float = roadHalfNear * scale(d)

    /** Sideways shift of the road centre at distance [d]; zero at the near plane. */
    fun bend(d: Float): Float {
        val t = 1f - scale(d)
        return curve * t * t * width * 0.35f
    }

    fun x(d: Float, u: Float): Float = centerX + bend(d) + u * roadHalf(d)
}
