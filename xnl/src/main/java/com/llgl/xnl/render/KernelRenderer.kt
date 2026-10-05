package com.llgl.xnl.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.llgl.xnl.kernel.KernelScene
import com.llgl.xnl.kernel.Uptime
import kotlin.math.max
import kotlin.math.min

/** Draws a [KernelScene]: page map, core tiles, the provisional wordmark, the log, the status line, sparks. */
class KernelRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val skyPaint = Paint()
    private var skyHeight = 0f
    private val rect = RectF()
    private val tile = FloatArray(4)

    private class Accent(val main: Int, val dim: Int, val glow: Int)

    private val accents = arrayOf(
        Accent(0xFF38E8FF.toInt(), 0xFF1C6F7A.toInt(), 0x3338E8FF),
        Accent(0xFFFFB347.toInt(), 0xFF7A5A24.toInt(), 0x33FFB347),
        Accent(0xFF5CFF8A.toInt(), 0xFF2A7A42.toInt(), 0x335CFF8A),
        Accent(0xFFFF5FD2.toInt(), 0xFF7A2E66.toInt(), 0x33FF5FD2),
        Accent(0xFFE6F1FF.toInt(), 0xFF6F7A88.toInt(), 0x33E6F1FF),
    )

    fun draw(canvas: Canvas, scene: KernelScene, zoom: Float) {
        val w = scene.width
        val h = scene.height
        val a = accents[scene.accent.coerceIn(0, accents.size - 1)]
        if (skyHeight != h) {
            skyHeight = h
            skyPaint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF060A0F.toInt(), 0xFF0B1220.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        canvas.save()
        if (zoom > 0f) {
            val s = 1f - 0.05f * zoom.coerceIn(0f, 1f)
            canvas.scale(s, s, w / 2f, h / 2f)
        }
        val px = -scene.tiltX * w * 0.012f
        val py = scene.tiltY * h * 0.008f

        drawPages(canvas, scene, a, px, py)
        drawCores(canvas, scene, a)
        if (scene.showWordmark) drawWordmark(canvas, scene, a)
        drawLog(canvas, scene, a)
        drawStatus(canvas, scene, a)
        for (s in scene.sparks) {
            fill.color = a.glow
            canvas.drawCircle(s.x, s.y, w * 0.012f, fill)
            fill.color = a.main
            canvas.drawCircle(s.x, s.y, w * 0.004f, fill)
        }
        canvas.restore()
    }

    private fun drawPages(canvas: Canvas, scene: KernelScene, a: Accent, ox: Float, oy: Float) {
        val cell = scene.cellPx
        val cols = scene.pageCols
        val rows = scene.pageRows
        stroke.color = 0xFF0F1C26.toInt()
        stroke.strokeWidth = 1f
        for (c in 0..cols) {
            val x = c * cell + ox
            canvas.drawLine(x, 0f, x, scene.height, stroke)
        }
        for (r in 0..rows) {
            val y = r * cell + oy
            canvas.drawLine(0f, y, scene.width, y, stroke)
        }
        val inset = max(1f, cell * 0.12f)
        val pages = scene.pages
        val heat = scene.heat
        for (r in 0 until rows) {
            val y = r * cell + oy
            for (c in 0 until cols) {
                val k = r * cols + c
                val state = pages[k]
                if (state == KernelScene.FREE) continue
                val x = c * cell + ox
                val hot = heat[k]
                fill.color = if (hot > 0f) blend(a.dim, a.main, hot) else a.dim
                fill.alpha = if (hot > 0f) (70 + 150 * hot).toInt() else 55
                canvas.drawRect(x + inset, y + inset, x + cell - inset, y + cell - inset, fill)
            }
        }
        fill.alpha = 255
        if (scene.scanRow >= 0) {
            fill.color = a.main
            fill.alpha = 40
            val y = scene.scanRow * cell + oy
            canvas.drawRect(0f, y, scene.width, y + cell, fill)
            fill.alpha = 255
        }
    }

    private fun drawCores(canvas: Canvas, scene: KernelScene, a: Accent) {
        mono.textSize = scene.width * 0.02f
        mono.textAlign = Paint.Align.LEFT
        for (i in 0 until scene.cores) {
            scene.coreTile(i, tile)
            fill.color = 0xCC0A131C.toInt()
            rect.set(tile[0], tile[1], tile[2], tile[3])
            canvas.drawRoundRect(rect, 6f, 6f, fill)
            stroke.color = a.dim
            stroke.strokeWidth = 2f
            canvas.drawRoundRect(rect, 6f, 6f, stroke)
            val load = scene.coreLoad[i]
            val pad = (tile[2] - tile[0]) * 0.12f
            val barTop = tile[3] - pad - (tile[3] - tile[1] - 2f * pad) * load
            fill.color = blend(a.dim, a.main, load)
            canvas.drawRect(tile[0] + pad, barTop, tile[2] - pad, tile[3] - pad, fill)
            mono.color = 0xFF9FB3C8.toInt()
            canvas.drawText("c$i", tile[0] + pad, tile[1] - pad * 0.6f, mono)
        }
    }

    /** The provisional wordmark: X, N and L built from bars, with a glow; the subtitle under it. */
    private fun drawWordmark(canvas: Canvas, scene: KernelScene, a: Accent) {
        val h = scene.width * 0.12f
        val t = h * 0.2f
        val lw = h * 0.75f
        val gap = h * 0.3f
        val total = 3f * lw + 2f * gap
        val x0 = (scene.width - total) / 2f
        val y0 = scene.wordmarkY - h / 2f
        stroke.strokeCap = Paint.Cap.BUTT
        for (pass in 0 until 3) {
            when (pass) {
                0 -> { stroke.color = a.glow; stroke.strokeWidth = t + h * 0.3f }
                1 -> { stroke.color = a.glow; stroke.strokeWidth = t + h * 0.12f }
                else -> { stroke.color = a.main; stroke.strokeWidth = t }
            }
            // X
            var x = x0
            canvas.drawLine(x, y0, x + lw, y0 + h, stroke)
            canvas.drawLine(x + lw, y0, x, y0 + h, stroke)
            // N
            x += lw + gap
            canvas.drawLine(x + t / 2f, y0, x + t / 2f, y0 + h, stroke)
            canvas.drawLine(x + t / 2f, y0, x + lw - t / 2f, y0 + h, stroke)
            canvas.drawLine(x + lw - t / 2f, y0, x + lw - t / 2f, y0 + h, stroke)
            // L
            x += lw + gap
            canvas.drawLine(x + t / 2f, y0, x + t / 2f, y0 + h, stroke)
            canvas.drawLine(x, y0 + h - t / 2f, x + lw, y0 + h - t / 2f, stroke)
        }
        stroke.strokeWidth = 1f
        mono.textAlign = Paint.Align.CENTER
        mono.textSize = scene.width * 0.024f
        mono.color = 0xFF9FB3C8.toInt()
        canvas.drawText("linux-compatible · closed source · kernel", scene.width / 2f, y0 + h + h * 0.45f, mono)
        mono.textSize = scene.width * 0.019f
        mono.color = 0xFF55687A.toInt()
        canvas.drawText("0.1.0-rc3 · provisional mark", scene.width / 2f, y0 + h + h * 0.72f, mono)
    }

    private fun drawLog(canvas: Canvas, scene: KernelScene, a: Accent) {
        mono.textAlign = Paint.Align.LEFT
        val size = scene.width * 0.0235f
        mono.textSize = size
        val lineH = size * 1.32f
        val maxLines = ((scene.logBottom - scene.logTop) / lineH).toInt().coerceAtLeast(1)
        val lines = scene.log
        val shown = min(maxLines, lines.size)
        var y = scene.logBottom
        val x = scene.width * 0.04f
        val maxChars = ((scene.width - 2f * x) / (size * 0.6f)).toInt()
        for (i in lines.size - 1 downTo lines.size - shown) {
            val l = lines[i]
            val fresh = (1f - l.age / 2f).coerceIn(0f, 1f)
            val depth = (lines.size - 1 - i).toFloat() / shown
            val base = if (l.warn) 0xFFFFB347.toInt() else 0xFF9FB3C8.toInt()
            mono.color = blend(base, a.main, fresh * 0.8f)
            mono.alpha = (255 * (1f - 0.75f * depth)).toInt()
            val text = if (l.text.length > maxChars) l.text.substring(0, max(0, maxChars - 1)) + "…" else l.text
            canvas.drawText(text, x, y, mono)
            y -= lineH
        }
        mono.alpha = 255
    }

    private fun drawStatus(canvas: Canvas, scene: KernelScene, a: Accent) {
        mono.textAlign = Paint.Align.LEFT
        mono.textSize = scene.width * 0.022f
        val x = scene.width * 0.04f
        val y = scene.statusY
        stroke.color = a.dim
        stroke.strokeWidth = 1f
        canvas.drawLine(x, y - mono.textSize * 1.5f, scene.width - x, y - mono.textSize * 1.5f, stroke)
        val sb = StringBuilder()
        sb.append("up ").append(Uptime.format(scene.uptimeMs))
        if (scene.memTotalMb > 0) sb.append("  mem ").append("%.1f".format(scene.memUsedMb / 1024f)).append('/').append("%.1f".format(scene.memTotalMb / 1024f)).append('G')
        if (scene.battery >= 0) sb.append("  bat ").append(scene.battery).append('%')
        sb.append("  ").append(scene.cores).append(" cpu")
        if (scene.hostKernel.isNotEmpty()) sb.append("  host ").append(scene.hostKernel)
        mono.color = a.main
        canvas.drawText(sb.toString(), x, y, mono)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (from ushr shift) and 0xFF
            val y = (to ushr shift) and 0xFF
            return (x + (y - x) * k + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
