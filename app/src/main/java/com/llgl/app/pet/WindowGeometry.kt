package com.llgl.app.pet

import kotlin.math.roundToInt

/** Where the overlay window sits and how big it is, in pixels, relative to the bottom-left corner. */
data class WindowLayout(val x: Int, val y: Int, val width: Int, val height: Int, val spriteOffsetX: Int)

/**
 * Pure mapping from the brain's normalized position to a window rectangle. The window is normally
 * exactly the sprite; when a bubble, menu or lettuce is showing it becomes a wider, taller box
 * centred on the turtle (clamped to the screen), and [WindowLayout.spriteOffsetX] says where the
 * sprite sits inside it.
 */
object WindowGeometry {
    fun layout(
        x01: Float,
        yUnits: Float,
        wide: Boolean,
        usableWidth: Int,
        spriteW: Int,
        spriteH: Int,
        wideWidth: Int,
        topExtra: Int,
        quantum: Int = 1,
    ): WindowLayout {
        val walkable = (usableWidth - spriteW).coerceAtLeast(0)
        val q = quantum.coerceAtLeast(1)
        val spriteX = (((x01.coerceIn(0f, 1f) * walkable) / q).roundToInt() * q).coerceIn(0, walkable)
        val y = (yUnits.coerceAtLeast(0f) * spriteH).roundToInt()
        if (!wide || wideWidth <= spriteW || usableWidth < wideWidth) {
            return WindowLayout(spriteX, y, spriteW, spriteH, 0)
        }
        val x = (spriteX - (wideWidth - spriteW) / 2).coerceIn(0, usableWidth - wideWidth)
        return WindowLayout(x, y, wideWidth, spriteH + topExtra, spriteX - x)
    }
}
