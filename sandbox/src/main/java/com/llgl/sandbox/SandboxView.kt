package com.llgl.sandbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.llgl.sandbox.render.GridRenderer
import com.llgl.sandbox.sim.Elements
import com.llgl.sandbox.sim.Grid
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The sandbox surface: runs the grid every frame, blows its bitmap up into crisp squares, and turns
 * the thumb into a brush. Holding still keeps pouring, dragging paints a continuous stroke.
 */
class SandboxView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Tool { PAINT, ERASE, SPARK, TOGGLE }

    var tool = Tool.PAINT
    var element: Byte = Elements.SAND
    var brush = 3
    var paused = false
    var speed = 1

    /** Called once the grid exists for this surface, so saved cells can be loaded into it. */
    var onGridReady: ((Grid) -> Unit)? = null

    var grid: Grid? = null
        private set
    private var renderer: GridRenderer? = null
    private var cell = 5
    private val dst = Rect()
    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isDither = false
    }

    private var running = false
    private var attached = false
    private var fingerDown = false
    private var pointerId = -1
    private var lastCx = -1
    private var lastCy = -1

    private val frame = object : Runnable {
        override fun run() {
            if (!running || !attached) return
            tick()
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        cell = max(3, (w / CELLS_ACROSS).roundToInt())
        val cols = ceil(w / cell.toFloat()).toInt()
        val rows = ceil(h / cell.toFloat()).toInt()
        val g = Grid(cols, rows, seed = (System.nanoTime() and 0x7FFFFFFF).toInt())
        grid = g
        renderer = GridRenderer(g)
        dst.set(0, 0, w, h)
        onGridReady?.invoke(g)
    }

    fun resume() {
        running = true
        if (attached) {
            removeCallbacks(frame)
            postOnAnimation(frame)
        }
    }

    fun pause() {
        running = false
        removeCallbacks(frame)
    }

    /** One tick while paused, for watching a circuit or a fire frame by frame. */
    fun stepOnce() {
        grid?.step()
        renderer?.render()
        invalidate()
    }

    fun clear() {
        grid?.clear()
        renderer?.render()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (running) postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun tick() {
        val g = grid ?: return
        if (fingerDown && lastCx >= 0 && tool != Tool.TOGGLE) apply(lastCx, lastCy)
        if (!paused) repeat(speed) { g.step() }
        renderer?.render()
    }

    override fun onDraw(canvas: Canvas) {
        val r = renderer ?: return
        canvas.drawBitmap(r.bitmap, null, dst, pixelPaint)
    }

    private fun apply(cx: Int, cy: Int) {
        val g = grid ?: return
        when (tool) {
            Tool.PAINT -> g.paint(cx, cy, brush, element)
            Tool.ERASE -> g.erase(cx, cy, brush)
            Tool.SPARK -> g.spark(cx, cy, brush)
            Tool.TOGGLE -> g.toggle(cx, cy, brush)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (grid == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                fingerDown = true
                val cx = (event.x / cell).toInt()
                val cy = (event.y / cell).toInt()
                apply(cx, cy)
                lastCx = cx
                lastCy = cy
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val cx = (event.getX(index) / cell).toInt()
                val cy = (event.getY(index) / cell).toInt()
                if (tool != Tool.TOGGLE) strokeTo(cx, cy)
                lastCx = cx
                lastCy = cy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                fingerDown = false
                lastCx = -1
                lastCy = -1
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    /** Paints every cell along the way from the last touch point, so fast drags leave no gaps. */
    private fun strokeTo(cx: Int, cy: Int) {
        if (lastCx < 0) {
            apply(cx, cy)
            return
        }
        val steps = max(abs(cx - lastCx), abs(cy - lastCy))
        if (steps == 0) {
            apply(cx, cy)
            return
        }
        for (k in 1..steps) {
            val x = lastCx + (cx - lastCx) * k / steps
            val y = lastCy + (cy - lastCy) * k / steps
            apply(x, y)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        /** Cells across the screen; a 1080 px phone gets 5 px cells. */
        const val CELLS_ACROSS = 200f
    }
}
