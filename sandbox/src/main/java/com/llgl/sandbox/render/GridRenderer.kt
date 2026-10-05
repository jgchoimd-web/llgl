package com.llgl.sandbox.render

import android.graphics.Bitmap
import com.llgl.sandbox.sim.Elements
import com.llgl.sandbox.sim.Grid
import kotlin.math.min

/** Turns the grid into a bitmap with one pixel per cell; the view blows it up into crisp squares. */
class GridRenderer(private val grid: Grid) {
    private val colors = IntArray(grid.size)
    val bitmap: Bitmap = Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.ARGB_8888)

    fun render() {
        val w = grid.width
        val cells = grid.cells
        val tick = grid.tick
        var i = 0
        for (y in 0 until grid.height) {
            for (x in 0 until w) {
                colors[i] = colorOf(cells[i], x, y, i, tick)
                i++
            }
        }
        bitmap.setPixels(colors, 0, w, 0, 0, w, grid.height)
    }

    private fun colorOf(e: Byte, x: Int, y: Int, i: Int, tick: Long): Int {
        val shade = (x * 7 + y * 13) and 3
        val charged = grid.charge[i] > 0
        return when (e) {
            Elements.EMPTY -> Palette.BACKGROUND
            Elements.WATER -> if (charged) 0xFF9FD3FF.toInt() else Palette.shade(e, shade)
            Elements.ACID -> if (((tick + x * 3 + y * 5) and 15L) == 0L) 0xFFCFFFA0.toInt() else Palette.shade(e, shade)
            Elements.LAVA -> if ((((tick shr 1) + x * 3 + y * 5) % 7) < 2) 0xFFFFC04A.toInt() else Palette.shade(e, shade)
            Elements.FIRE -> {
                val l = grid.lifeOf(i)
                val heat = min(1f, l / 40f)
                val flicker = ((tick + i * 31) and 3L) == 0L
                val c = Palette.mix(0xFFB0301A.toInt(), if (flicker) 0xFFFFF3A0.toInt() else 0xFFFFB030.toInt(), heat)
                c
            }
            Elements.SMOKE -> Palette.mix(Palette.BACKGROUND, Palette.base(e), min(1f, grid.lifeOf(i) / 90f))
            Elements.STEAM -> Palette.mix(Palette.BACKGROUND, Palette.base(e), 0.35f + 0.65f * min(1f, grid.lifeOf(i) / 150f))
            Elements.WIRE -> if (charged) Palette.SPARK else Palette.shade(e, shade)
            Elements.BATTERY -> Palette.shade(e, (x + y) and 1)
            Elements.SWITCH_OFF -> Palette.shade(e, (x + y) and 1)
            Elements.SWITCH_ON -> if (charged) Palette.SPARK else Palette.shade(e, (x + y) and 1)
            Elements.LAMP -> {
                val glow = if (charged) 1f else grid.lifeOf(i) / Grid.LAMP_GLOW.toFloat()
                Palette.mix(Palette.shade(e, 0), Palette.shade(e, 1), glow)
            }
            Elements.HEATER -> if (charged) Palette.shade(e, 1) else Palette.shade(e, 0)
            Elements.LIGHTNING -> Palette.shade(e, grid.lifeOf(i) and 1)
            Elements.WOOD -> Palette.shade(e, (y shr 1) and 3)
            else -> Palette.shade(e, shade)
        }
    }
}
