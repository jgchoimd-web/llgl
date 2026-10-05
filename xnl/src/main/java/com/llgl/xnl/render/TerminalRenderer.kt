package com.llgl.xnl.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.llgl.xnl.term.Kind
import com.llgl.xnl.term.Terminal
import com.llgl.xnl.term.VisualRow
import kotlin.math.max
import kotlin.math.min

/** Draws the terminal: a dark screen, monospace rows, the prompt in the accent, a block cursor. */
class TerminalRenderer {
    class Metrics(val charWidth: Float, val lineHeight: Float, val ascent: Float, val padX: Float, val padTop: Float, val columns: Int, val rows: Int)

    private class Palette(val bg: Int, val accent: Int, val fg: Int)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val cursor = Paint()
    private val rowsBuf = ArrayList<VisualRow>()
    private var metrics: Metrics? = null

    /** Index into the accent palettes: cyan, amber, green, magenta, white. */
    var accent = 0
        set(value) {
            field = value.coerceIn(0, PALETTES.size - 1)
        }

    /** On the lock screen the oldest rows, up where the clock sits, fade so the clock stays readable. */
    var lockFade = false

    /** Sizes the font so that about `wantColumns` fit the width; the terminal takes the exact grid back. */
    fun layout(width: Int, height: Int, wantColumns: Int): Metrics {
        val padX = width * 0.025f
        val padTop = height * 0.02f
        val padBottom = height * 0.03f
        val usable = width - 2f * padX
        text.textSize = max(6f, usable / (max(8, wantColumns) * 0.6f))
        val advance = text.measureText("0")
        val columns = max(8, (usable / advance).toInt())
        val fm = text.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.12f
        val rows = max(2, ((height - padTop - padBottom) / lineHeight).toInt())
        return Metrics(advance, lineHeight, fm.ascent, padX, padTop, columns, rows).also { metrics = it }
    }

    fun draw(canvas: Canvas, term: Terminal, width: Int, height: Int) {
        val m = metrics ?: layout(width, height, term.columns)
        val p = PALETTES[accent]
        canvas.drawColor(p.bg)
        val n = term.visible(rowsBuf)
        val offset = max(0, n - term.rows)
        for (i in 0 until n) {
            val y = m.padTop + (i - offset + term.slide) * m.lineHeight
            if (y + m.lineHeight < 0f || y > height) continue
            var alpha = 1f
            if (lockFade) {
                val f = ((y + m.lineHeight) / (height * 0.42f)).coerceIn(0f, 1f)
                alpha = 0.12f + 0.88f * f * f
            }
            drawRow(canvas, rowsBuf[i], m, p, y - m.ascent, alpha)
        }
        if (term.cursorOn && n > 0) {
            val y = m.padTop + (n - 1 - offset + term.slide) * m.lineHeight
            val x = m.padX + term.cursorColumn * m.charWidth
            cursor.color = p.accent
            canvas.drawRect(x, y + m.lineHeight * 0.08f, x + m.charWidth, y + m.lineHeight * 0.92f, cursor)
        }
    }

    private fun drawRow(c: Canvas, row: VisualRow, m: Metrics, p: Palette, baseline: Float, alpha: Float) {
        val line = row.line
        val s = line.text
        var x = m.padX
        var start = row.start
        if (line.kind == Kind.PROMPT && start < line.promptLen) {
            val end = min(row.end, line.promptLen)
            text.color = withAlpha(p.accent, alpha)
            c.drawText(s, start, end, x, baseline, text)
            x += (end - start) * m.charWidth
            start = end
        }
        if (start < row.end) {
            text.color = withAlpha(
                when (line.kind) {
                    Kind.BANNER -> p.accent
                    Kind.PROMPT -> BRIGHT
                    Kind.OUTPUT -> p.fg
                    Kind.ERROR -> ERROR
                    Kind.DIM -> DIM
                },
                alpha,
            )
            c.drawText(s, start, row.end, x, baseline, text)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    private companion object {
        const val BRIGHT = 0xFFFFFFFF.toInt()
        const val DIM = 0xFF5E6B78.toInt()
        const val ERROR = 0xFFFF7B72.toInt()
        val PALETTES = arrayOf(
            Palette(0xFF070A0D.toInt(), 0xFF38E8FF.toInt(), 0xFFBEEAF5.toInt()),
            Palette(0xFF0B0805.toInt(), 0xFFFFB347.toInt(), 0xFFF2D7A8.toInt()),
            Palette(0xFF050A07.toInt(), 0xFF5CFF8A.toInt(), 0xFFB5F0C3.toInt()),
            Palette(0xFF0B060A.toInt(), 0xFFFF5FD2.toInt(), 0xFFF0C4E6.toInt()),
            Palette(0xFF08090B.toInt(), 0xFFE6F1FF.toInt(), 0xFFC9D4DE.toInt()),
        )
    }
}
