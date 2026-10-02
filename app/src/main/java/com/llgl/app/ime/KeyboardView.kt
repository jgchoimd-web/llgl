package com.llgl.app.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.llgl.app.dial.DialItem
import com.llgl.app.dial.DialLayers
import com.llgl.app.dial.DialLayout
import com.llgl.app.dial.DialRecognizer
import com.llgl.app.dial.DialTables
import com.llgl.app.dial.Ring
import com.llgl.app.hangul.Jamo
import com.llgl.app.keyboard.KeyAction
import com.llgl.app.keyboard.Layer
import com.llgl.app.keyboard.Pt
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min

/**
 * The keyboard surface: a rotary dial for one thumb. Draws the hub and the three rings, follows
 * the thumb through a [DialRecognizer], clicks on every tick like a pulse dial, and hands the
 * finished gesture to the IME. A candidate/preview bar sits on top. Plain android.view.View for
 * low input latency.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** The thumb lifted: what the whole gesture meant. */
        fun onResult(result: DialRecognizer.Result)
        fun onCandidate(index: Int)
        fun onBackspaceRepeat()

        /** What [result] would enter right now, shown large while the thumb is down. */
        fun preview(result: DialRecognizer.Result): String
    }

    var listener: Listener? = null

    var settings: KeyboardSettings.Values = KeyboardSettings.Values()
        set(value) {
            field = value
            rebuild()
            requestLayout()
            invalidate()
        }

    var layer: Layer = Layer.HANGUL
        set(value) {
            field = value
            rebuildForLayer()
            invalidate()
        }

    var candidates: List<String> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val barHeight = 44f * density
    private val gap = 1.5f * density
    private var layout: DialLayout? = null
    private var recognizer: DialRecognizer? = null
    private var sectors: Map<Pair<Ring, Int>, Path> = emptyMap()
    private var bands: Map<Ring, Path> = emptyMap()
    private val pulseMode: Boolean get() = DialLayers.inner(layer, Ring.VOWEL) == null

    private val trail = ArrayList<Pt>()
    private val trailPath = Path()
    private var pointerId = -1
    private var inBar = false
    private var touching = false
    private var startX = 0f
    private var startY = 0f
    private var moved = false
    private var repeating = false
    private var previewText = ""
    private var current: DialRecognizer.Result = DialRecognizer.Result.None

    private val handler = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspaceRepeat()
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }
    private val holdRunnable = Runnable {
        if (touching && !moved && current is DialRecognizer.Result.Hub) {
            repeating = true
            previewText = "⌫"
            haptic(HapticFeedbackConstants.LONG_PRESS)
            repeatRunnable.run()
            invalidate()
        }
    }

    private val bgColor = Color.parseColor("#0E1116")
    private val barColor = Color.parseColor("#090B0F")
    private val labelColor = Color.parseColor("#E8ECF3")
    private val holeLabelColor = Color.parseColor("#C5CDDB")
    private val hubFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141923") }
    private val hubPressedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#223049") }
    private val deepFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#121722") }
    private val vowelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#171D29") }
    private val outerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1C2230") }
    private val specialFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141923") }
    private val pressedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E8BFF") }
    private val pushedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7A59") }
    private val holeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0D12") }
    private val holeActiveFill = pressedFill
    private val holeHardenedFill = pushedFill
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 22f * density
    }
    private val holeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = holeLabelColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 18f * density
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A94A6")
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val hubHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A94A6")
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    private val barTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textAlign = Paint.Align.CENTER
        textSize = 17f * density
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7CC4FF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 30f * density
    }
    private val barFill = Paint().apply { color = barColor }
    private val dividerPaint = Paint().apply { color = Color.parseColor("#232A38"); strokeWidth = density }
    private val spaceBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AEB7C6")
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A2E8BFF")
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ---------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (settings.heightDp * density + barHeight).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        if (width <= 0) return
        layout = DialLayout(width.toFloat(), settings.heightDp * density, density, leftHanded = settings.leftHanded)
        rebuildForLayer()
    }

    private fun rebuildForLayer() {
        val layout = layout ?: return
        if (touching) cancelTouch()
        val inner = Ring.entries
            .filter { it != Ring.OUTER }
            .mapNotNull { ring -> DialLayers.inner(layer, ring)?.let { ring to it.size } }
            .toMap()
        recognizer = DialRecognizer(
            layout = layout,
            pulseMode = inner.isEmpty(),
            outerCount = DialLayers.outer(layer).size,
            innerCounts = inner,
            tickDegrees = settings.tickDegrees.toFloat(),
            tapRadius = settings.tapRadiusDp * density,
            longDistance = settings.longFlickDp * density,
        )
        val paths = HashMap<Pair<Ring, Int>, Path>()
        for (ring in Ring.entries) {
            val items = DialLayers.items(layer, ring) ?: continue
            for (i in items.indices) paths[ring to i] = arcPath(layout, ring, items.size, i)
        }
        sectors = paths
        bands = Ring.entries.associateWith { arcPath(layout, it, 1, 0, steps = 32) }
    }

    /** One sector of a ring (the whole band when [count] is 1), with a small gap to its neighbours. */
    private fun arcPath(layout: DialLayout, ring: Ring, count: Int, index: Int, steps: Int = 8): Path {
        val (rIn0, rOut0) = layout.ringRadii(ring)
        val rIn = rIn0 + gap
        val rOut = rOut0 - gap
        val (a0raw, a1raw) = layout.itemAngles(ring, count, index)
        val da = if (count > 1) gap / ((rIn + rOut) / 2f) else 0f
        val a0 = a0raw + da
        val a1 = a1raw - da
        val p = Path()
        val first = layout.toScreen(rOut, a1)
        p.moveTo(first.x, first.y)
        for (i in 1..steps) {
            val q = layout.toScreen(rOut, a1 - (a1 - a0) * i / steps)
            p.lineTo(q.x, q.y)
        }
        for (i in 0..steps) {
            val q = layout.toScreen(rIn, a0 + (a1 - a0) * i / steps)
            p.lineTo(q.x, q.y)
        }
        p.close()
        return p
    }

    private fun rad(degrees: Float): Float = (degrees * PI / 180.0).toFloat()

    private fun midAngle(layout: DialLayout, ring: Ring): Float = layout.range(ring).let { (a, b) -> (a + b) / 2f }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return
        canvas.drawColor(bgColor)
        drawBar(canvas)
        canvas.save()
        canvas.translate(0f, barHeight)
        canvas.clipRect(0f, 0f, width.toFloat(), layout.height)
        drawHub(canvas, layout)
        if (pulseMode) drawPulseRings(canvas, layout) else drawFixedRings(canvas, layout)
        if (settings.showTrail && trail.size > 1) {
            trailPath.rewind()
            trailPath.moveTo(trail[0].x, trail[0].y)
            for (i in 1 until trail.size) trailPath.lineTo(trail[i].x, trail[i].y)
            canvas.drawPath(trailPath, trailPaint)
        }
        canvas.restore()
    }

    private fun drawHub(canvas: Canvas, layout: DialLayout) {
        val pivot = layout.toScreen(0f, 0f)
        val pressed = touching && current is DialRecognizer.Result.Hub
        val r0 = layout.radii[0]
        canvas.drawCircle(pivot.x, pivot.y, r0 - gap, if (pressed) hubPressedFill else hubFill)
        val s = layout.toScreen(r0 * 0.40f, rad(135f))
        val half = 11f * density
        canvas.drawLine(s.x - half, s.y, s.x + half, s.y, spaceBarPaint)
        if (!settings.hints) return
        val back = layout.toScreen(r0 * 0.72f, rad(158f))
        drawCentered(canvas, "⌫", back.x, back.y, hubHintPaint)
        val enter = layout.toScreen(r0 * 0.72f, rad(112f))
        drawCentered(canvas, "↵", enter.x, enter.y, hubHintPaint)
    }

    /** Hangul: inner rings are finger holes laid out from where the thumb entered; the outer ring holds the consonants. */
    private fun drawPulseRings(canvas: Canvas, layout: DialLayout) {
        val syllable = current as? DialRecognizer.Result.Syllable
        val vowelEntry = recognizer?.vowelEntryAngle
        val finalEntry = recognizer?.finalEntryAngle
        val tickRad = rad(settings.tickDegrees.toFloat())

        for (ring in INNER_RINGS) {
            canvas.drawPath(bands.getValue(ring), ringFill(ring))
            val set = if (ring == Ring.DEEP) 1 else 0
            val onThisRing = syllable != null && syllable.vowelSet == set && vowelEntry != null
            val entry = if (onThisRing) vowelEntry!! else midAngle(layout, ring)
            val table = if (set == 0) DialTables.VOWELS_A else DialTables.VOWELS_B
            drawHoles(canvas, layout, ring, table, entry, if (onThisRing) syllable!!.vowelTicks else null, false, tickRad)
        }

        if (syllable != null && finalEntry != null) {
            canvas.drawPath(bands.getValue(Ring.OUTER), ringFill(Ring.OUTER))
            drawHoles(canvas, layout, Ring.OUTER, DialTables.FINALS, finalEntry, syllable.finalTicks, syllable.finalHardened, tickRad)
        } else {
            drawItems(canvas, layout, Ring.OUTER)
        }
    }

    private fun drawFixedRings(canvas: Canvas, layout: DialLayout) {
        for (ring in Ring.entries) drawItems(canvas, layout, ring)
    }

    private fun ringFill(ring: Ring): Paint = when (ring) {
        Ring.DEEP -> deepFill
        Ring.VOWEL -> vowelFill
        Ring.OUTER -> outerFill
    }

    private fun drawItems(canvas: Canvas, layout: DialLayout, ring: Ring) {
        val items = DialLayers.items(layer, ring) ?: return
        val hit = hitIndex(ring)
        val pushed = hitPushed()
        for ((i, item) in items.withIndex()) {
            val path = sectors[ring to i] ?: continue
            val fill = when {
                i == hit && pushed -> pushedFill
                i == hit -> pressedFill
                item.isSpecial -> specialFill
                else -> ringFill(ring)
            }
            canvas.drawPath(path, fill)
            val c = layout.itemCenter(ring, items.size, i)
            val label = item.label
            labelPaint.textSize = (if (label.length > 1) 14f else 22f) * density
            labelPaint.color = if (i == hit) Color.WHITE else labelColor
            drawCentered(canvas, label, c.x, c.y, labelPaint)
            if (!settings.hints) continue
            val hint = pushedHint(item) ?: continue
            val (rIn, _) = layout.ringRadii(ring)
            val (a0, a1) = layout.itemAngles(ring, items.size, i)
            val h = layout.toScreen(rIn + 12f * density, (a0 + a1) / 2f)
            drawCentered(canvas, hint, h.x, h.y, hintPaint)
        }
    }

    /** Finger holes every tick around [entry]; positive ticks run clockwise (toward the top of the screen). */
    private fun drawHoles(
        canvas: Canvas, layout: DialLayout, ring: Ring, table: Map<Int, Char>,
        entry: Float, active: Int?, hardened: Boolean, tickRad: Float,
    ) {
        val (rIn, rOut) = layout.ringRadii(ring)
        val rMid = (rIn + rOut) / 2f
        val (aMin, aMax) = layout.range(ring)
        val holeR = min((rOut - rIn) * 0.34f, rMid * tickRad * 0.42f)
        holeText.textSize = min(holeR * 1.3f, 22f * density)
        for ((k, ch) in table) {
            val a = entry - k * tickRad
            if (a < aMin - tickRad * 0.4f || a > aMax + tickRad * 0.4f) continue
            val c = layout.toScreen(rMid, a)
            val isActive = active == k
            val fill = when {
                isActive && hardened -> holeHardenedFill
                isActive -> holeActiveFill
                else -> holeFill
            }
            canvas.drawCircle(c.x, c.y, holeR, fill)
            val hard = DialTables.strengthen(ch)
            val text = if (isActive && hardened && Jamo.canBeFinal(hard)) hard else ch
            holeText.color = if (isActive) Color.WHITE else holeLabelColor
            drawCentered(canvas, text.toString(), c.x, c.y, holeText)
        }
    }

    private fun hitIndex(ring: Ring): Int? = when (val r = current) {
        is DialRecognizer.Result.Item -> if (r.ring == ring) r.index else null
        is DialRecognizer.Result.Syllable -> if (ring == Ring.OUTER) r.initialIndex else null
        else -> null
    }

    private fun hitPushed(): Boolean = when (val r = current) {
        is DialRecognizer.Result.Item -> r.pushed
        is DialRecognizer.Result.Syllable -> r.initialHardened
        else -> false
    }

    /** The small label showing what a radial push turns the item into. */
    private fun pushedHint(item: DialItem): String? {
        val action = item.action
        if (action is KeyAction.Consonant) return DialTables.STRENGTHEN[action.c]?.toString()
        val pushed = (item.pushed as? KeyAction.Text)?.text ?: return null
        return if (pushed.equals(item.label, ignoreCase = true)) null else pushed
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        canvas.drawText(text, x, y - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    private fun drawBar(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, barFill)
        canvas.drawLine(0f, barHeight, width.toFloat(), barHeight, dividerPaint)
        val middle = barHeight / 2f
        if (previewText.isNotEmpty()) {
            drawCentered(canvas, previewText, width / 2f, middle, previewPaint)
            return
        }
        if (candidates.isEmpty()) return
        val shown = candidates.take(MAX_CANDIDATES)
        val cell = width.toFloat() / shown.size
        for ((i, word) in shown.withIndex()) {
            drawCentered(canvas, word, cell * i + cell / 2f, middle, barTextPaint)
            if (i > 0) canvas.drawLine(cell * i, 8f * density, cell * i, barHeight - 8f * density, dividerPaint)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (layout == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                if (event.y < barHeight) {
                    inBar = true
                    return true
                }
                inBar = false
                beginTouch(event.x, event.y - barHeight)
            }
            MotionEvent.ACTION_MOVE -> {
                if (inBar || !touching) return true
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) moveTouch(event.getX(index), event.getY(index) - barHeight)
            }
            MotionEvent.ACTION_UP -> {
                if (inBar) {
                    inBar = false
                    tapCandidate(event.x, event.y)
                } else {
                    endTouch()
                }
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    /** Every gesture already fired through [Listener.onResult]; this only satisfies accessibility click semantics. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun beginTouch(x: Float, y: Float) {
        val rec = recognizer ?: return
        touching = true
        repeating = false
        moved = false
        startX = x
        startY = y
        trail.clear()
        trail += Pt(x, y)
        rec.begin(x, y)
        current = rec.result()
        previewText = listener?.preview(current) ?: ""
        haptic(HapticFeedbackConstants.KEYBOARD_TAP)
        if (current is DialRecognizer.Result.Hub) handler.postDelayed(holdRunnable, HOLD_DELAY_MS)
        invalidate()
    }

    private fun moveTouch(x: Float, y: Float) {
        val rec = recognizer ?: return
        trail += Pt(x, y)
        if (!moved && hypot(x - startX, y - startY) > settings.tapRadiusDp * density) {
            moved = true
            handler.removeCallbacks(holdRunnable)
        }
        if (repeating) return
        rec.move(x, y)
        val next = rec.result()
        if (next != current) {
            current = next
            previewText = listener?.preview(next) ?: ""
            haptic(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
    }

    private fun endTouch() {
        handler.removeCallbacks(holdRunnable)
        handler.removeCallbacks(repeatRunnable)
        val rec = recognizer
        if (touching && rec != null) {
            val result = rec.end()
            if (!repeating) {
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                listener?.onResult(result)
            }
        }
        resetTouch()
    }

    private fun cancelTouch() {
        handler.removeCallbacks(holdRunnable)
        handler.removeCallbacks(repeatRunnable)
        recognizer?.end()
        resetTouch()
    }

    private fun resetTouch() {
        touching = false
        repeating = false
        moved = false
        pointerId = -1
        trail.clear()
        previewText = ""
        current = DialRecognizer.Result.None
        invalidate()
    }

    private fun tapCandidate(x: Float, y: Float) {
        val shown = candidates.take(MAX_CANDIDATES)
        if (shown.isEmpty() || y >= barHeight) return
        val index = (x / (width.toFloat() / shown.size)).toInt().coerceIn(0, shown.size - 1)
        listener?.onCandidate(index)
    }

    private fun haptic(constant: Int) {
        if (!settings.haptics) return
        if (Build.VERSION.SDK_INT >= 33) {
            performHapticFeedback(constant)
        } else {
            @Suppress("DEPRECATION")
            performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private companion object {
        val INNER_RINGS = listOf(Ring.DEEP, Ring.VOWEL)
        const val MAX_CANDIDATES = 4
        const val HOLD_DELAY_MS = 450L
        const val REPEAT_INTERVAL_MS = 55L
    }
}
