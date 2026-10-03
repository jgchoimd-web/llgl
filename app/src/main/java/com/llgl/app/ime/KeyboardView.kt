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
import android.view.animation.AnimationUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The keyboard surface: a rotary pulse dial for one thumb. Labels are printed on the base; over them
 * sit three plates with finger holes on a fixed grid. The plate under the thumb turns with it, the
 * holes pass over the labels (a label shows only while a hole is squarely over it), and when let go
 * the plate springs home clicking once per hole. [DialRecognizer] decides what the gesture means;
 * this view only shows it. A candidate/preview bar sits on top, and the view keeps clear of the
 * system navigation bar underneath.
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

    /** One finger-hole plate: how far it is turned, who holds it, and its way back home. */
    private class Plate {
        /** The rotation drawn this frame (dial radians). */
        var rotation = 0f
        var grabbed = false

        /** Where the thumb has turned the plate to; the drawn rotation eases toward it after a grab. */
        var thumbRotation = 0f
        var catchUp = 0f
        var catchUpStart = 0L

        /** Pulse rings: the hole the relative labels are laid out around (null = the ring's middle). */
        var legendAngle: Float? = null

        /** The outer plate shows the finals while one is being dialled. */
        var finals = false
        var labelsChangedAt = 0L
        var springing = false
        var springFrom = 0f
        var springStart = 0L
        var springDuration = 1L
        var springStep = 1f
        var springPulsed = 0

        fun reset() {
            rotation = 0f
            grabbed = false
            thumbRotation = 0f
            catchUp = 0f
            legendAngle = null
            finals = false
            springing = false
        }
    }

    /** A label printed on the base: its angle, text, and which table key or item it is. */
    private class Slot(val angle: Float, val label: String, val key: Int, val special: Boolean)

    private val density = resources.displayMetrics.density
    private val barHeight = 44f * density
    private val gap = 1.5f * density
    private var layout: DialLayout? = null
    private var recognizer: DialRecognizer? = null
    private val plates = Array(Ring.entries.size) { Plate() }
    private var click: PulseClick? = null
    private var bottomInset = 0
    private var frameScheduled = false
    private var lastPulseAt = 0L
    private val pulseMode: Boolean get() = DialLayers.inner(layer, Ring.VOWEL) == null
    private val tickRad: Float get() = rad(settings.tickDegrees.toFloat())

    private val trail = ArrayList<Pt>()
    private val trailPath = Path()
    private val platePath = Path()
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

    /** One animation frame: moves springs and fades along, then redraws; re-posts itself while anything still moves. */
    private val frame = object : Runnable {
        override fun run() {
            frameScheduled = false
            val active = step(AnimationUtils.currentAnimationTimeMillis())
            invalidate()
            if (active) schedule()
        }
    }

    // A vintage telephone: bakelite base, graphite plates with chrome rims, ivory print, amber for the selection.
    private val baseColor = Color.parseColor("#14100E")
    private val barColor = Color.parseColor("#0C0A08")
    private val ivory = Color.parseColor("#EFE6CF")
    private val ivoryDim = Color.parseColor("#9C9484")
    private val amber = Color.parseColor("#FFB347")
    private val platePaint = fill("#2B2E34")
    private val plateEdgePaint = stroke("#8F949C", 1.5f)
    private val holeRimPaint = stroke("#4D5159", 1.5f)
    private val holeSelectedRimPaint = stroke("#FFB347", 2.5f)
    private val hubPaint = fill("#2E3136")
    private val hubPressedPaint = fill("#3D4149")
    private val hubRimPaint = stroke("#6B7078", 2f)
    private val stopPaint = stroke("#C9CED6", 7f).apply { strokeCap = Paint.Cap.ROUND }
    private val stopShadowPaint = stroke("#66000000", 11f).apply { strokeCap = Paint.Cap.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ivory
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ivoryDim
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    private val barTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ivory
        textAlign = Paint.Align.CENTER
        textSize = 17f * density
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = amber
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 30f * density
    }
    private val barFill = Paint().apply { color = barColor }
    private val dividerPaint = Paint().apply { color = Color.parseColor("#2A2420"); strokeWidth = density }
    private val spaceBarPaint = stroke("#EFE6CF", 3f).apply { strokeCap = Paint.Cap.ROUND }
    private val trailPaint = stroke("#90FFB347", 6f).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        // Edge-to-edge: the IME window reaches under the navigation bar, so keep the dial above it.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()).bottom
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom
            val bottom = max(bars, gestures)
            if (bottom != bottomInset) {
                bottomInset = bottom
                requestLayout()
                invalidate()
            }
            insets
        }
    }

    private fun fill(color: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor(color) }

    private fun stroke(color: String, widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.parseColor(color)
        style = Paint.Style.STROKE
        strokeWidth = widthDp * density
    }

    // ---------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------

    private fun dialHeight(): Float = settings.heightDp * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (barHeight + dialHeight() + bottomInset).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        if (width <= 0) return
        layout = DialLayout(width.toFloat(), dialHeight(), density, leftHanded = settings.leftHanded)
        rebuildForLayer()
    }

    private fun rebuildForLayer() {
        val layout = layout ?: return
        if (touching) cancelTouch()
        for (plate in plates) plate.reset()
        val inner = Ring.entries
            .filter { it != Ring.OUTER }
            .mapNotNull { ring -> DialLayers.inner(layer, ring)?.let { ring to it.size } }
            .toMap()
        val outerCount = DialLayers.outer(layer).size
        recognizer = DialRecognizer(
            layout = layout,
            pulseMode = inner.isEmpty(),
            outerCount = outerCount,
            innerCounts = inner,
            tickDegrees = settings.tickDegrees.toFloat(),
            tapRadius = settings.tapRadiusDp * density,
            longDistance = settings.longFlickDp * density,
            hysteresis = 4f * density,
            finalTickDegrees = Math.toDegrees(layout.itemStep(Ring.OUTER, outerCount).toDouble()).toFloat(),
        )
    }

    private fun plate(ring: Ring): Plate = plates[ring.ordinal]

    private fun rad(degrees: Float): Float = (degrees * PI / 180.0).toFloat()

    private fun midAngle(layout: DialLayout, ring: Ring): Float = layout.range(ring).let { (a, b) -> (a + b) / 2f }

    /** Whether [ring] is read as pulses right now: Hangul inner rings, and the outer ring while a final is dialled. */
    private fun pulseRing(ring: Ring): Boolean = pulseMode && (ring != Ring.OUTER || plate(ring).finals)

    private fun pulseTable(ring: Ring): Map<Int, Char> = when (ring) {
        Ring.OUTER -> DialTables.FINALS
        Ring.VOWEL -> DialTables.VOWELS_A
        Ring.DEEP -> DialTables.VOWELS_B
    }

    /** The spacing of the holes (and of the labels under them) on [ring]: items on the outer ring, ticks inside. */
    private fun holeStep(ring: Ring, layout: DialLayout): Float =
        if (pulseMode && ring != Ring.OUTER) tickRad else layout.itemStep(ring, DialLayers.items(layer, ring)?.size ?: 1)

    /** The holes of [ring]: a fixed grid on the plate, regardless of what is printed under them. */
    private fun holeAngles(ring: Ring, layout: DialLayout): List<Float> {
        if (pulseMode && ring != Ring.OUTER) return layout.gridAngles(ring, tickRad)
        val count = DialLayers.items(layer, ring)?.size ?: return emptyList()
        return List(count) { layout.itemCenterAngle(ring, count, it) }
    }

    /** The labels on the base of [ring]: a pulse table laid out around the entry hole, or the layer's fixed items. */
    private fun labels(ring: Ring, layout: DialLayout): List<Slot> {
        if (pulseRing(ring)) {
            val step = holeStep(ring, layout)
            val origin = plate(ring).legendAngle ?: midAngle(layout, ring)
            return pulseTable(ring).entries.map { (k, ch) -> Slot(origin - k * step, ch.toString(), k, false) }
        }
        val items = DialLayers.items(layer, ring) ?: return emptyList()
        return items.mapIndexed { i, item -> Slot(layout.itemCenterAngle(ring, items.size, i), item.label, i, item.isSpecial) }
    }

    private fun holeRadius(ring: Ring, layout: DialLayout): Float {
        val (rIn, rOut) = layout.ringRadii(ring)
        return min((rOut - rIn) * 0.36f, (rIn + rOut) / 2f * holeStep(ring, layout) * 0.44f)
    }

    // ---------------------------------------------------------------------------------------
    // Selection
    // ---------------------------------------------------------------------------------------

    /** The slot key the thumb has dialled on [ring], or null. */
    private fun selectedKey(ring: Ring): Int? = when (val r = current) {
        is DialRecognizer.Result.Item -> if (r.ring == ring) r.index else null
        is DialRecognizer.Result.Syllable -> when (ring) {
            Ring.OUTER -> if (plate(ring).finals) r.finalTicks?.let { DialTables.finalKey(it) } else r.initialIndex
            Ring.VOWEL -> if (r.vowelSet == 0) DialTables.vowelKey(0, r.vowelTicks) else null
            Ring.DEEP -> if (r.vowelSet == 1) DialTables.vowelKey(1, r.vowelTicks) else null
        }
        else -> null
    }

    /** What the selected label on [ring] reads once a push changed it: hardened consonant, capital, alternate symbol. */
    private fun selectedLabel(ring: Ring): String? {
        when (val r = current) {
            is DialRecognizer.Result.Item -> {
                if (r.ring != ring || !r.pushed) return null
                val item = DialLayers.items(layer, ring)?.getOrNull(r.index) ?: return null
                val action = item.action
                return if (action is KeyAction.Consonant) DialTables.strengthen(action.c).toString() else (item.pushed as? KeyAction.Text)?.text
            }
            is DialRecognizer.Result.Syllable -> {
                if (ring != Ring.OUTER) return null
                if (plate(ring).finals) {
                    val ticks = r.finalTicks ?: return null
                    if (!r.finalHardened) return null
                    val hard = DialTables.strengthen(DialTables.final(ticks))
                    return if (Jamo.canBeFinal(hard)) hard.toString() else null
                }
                if (!r.initialHardened) return null
                val item = r.initialIndex?.let { DialLayers.outer(layer).getOrNull(it) } ?: return null
                return (item.action as? KeyAction.Consonant)?.let { DialTables.strengthen(it.c).toString() }
            }
            else -> return null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return
        val now = AnimationUtils.currentAnimationTimeMillis()
        canvas.drawColor(baseColor)
        drawBar(canvas)
        canvas.save()
        canvas.translate(0f, barHeight)
        canvas.clipRect(0f, 0f, width.toFloat(), layout.height)
        drawHub(canvas, layout)
        for (ring in Ring.entries) drawRing(canvas, layout, ring, now)
        drawFingerStop(canvas, layout)
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
        val r0 = layout.radii[0] - gap
        val pressed = touching && current is DialRecognizer.Result.Hub
        canvas.drawCircle(pivot.x, pivot.y, r0, if (pressed) hubPressedPaint else hubPaint)
        canvas.drawCircle(pivot.x, pivot.y, r0, hubRimPaint)
        val s = layout.toScreen(r0 * 0.40f, rad(135f))
        val half = 11f * density
        canvas.drawLine(s.x - half, s.y, s.x + half, s.y, spaceBarPaint)
        if (!settings.hints) return
        val back = layout.toScreen(r0 * 0.72f, rad(158f))
        drawCentered(canvas, "⌫", back.x, back.y, hintPaint)
        val enter = layout.toScreen(r0 * 0.72f, rad(112f))
        drawCentered(canvas, "↵", enter.x, enter.y, hintPaint)
    }

    /**
     * Labels on the base, then the plate with its holes turned as far as the thumb turned it. Labels
     * and holes share one grid, so a label is fully visible only while a hole sits squarely over it
     * and fades as the plate moves between holes; a relabelled base fades in instead of popping.
     */
    private fun drawRing(canvas: Canvas, layout: DialLayout, ring: Ring, now: Long) {
        val holes = holeAngles(ring, layout)
        if (holes.isEmpty()) return
        val plate = plate(ring)
        val labels = labels(ring, layout)
        val (rIn, rOut) = layout.ringRadii(ring)
        val rMid = (rIn + rOut) / 2f
        val holeR = holeRadius(ring, layout)
        val step = holeStep(ring, layout)
        val selected = selectedKey(ring)
        val override = selectedLabel(ring)

        val turns = plate.rotation / step
        val misalignment = abs(turns - turns.roundToInt()) * 2f
        val alignAlpha = (1.4f - 1.6f * misalignment).coerceIn(0f, 1f)
        val fadeIn = ((now - plate.labelsChangedAt).toFloat() / LABEL_FADE_MS).coerceIn(0f, 1f)
        val alpha = (alignAlpha * fadeIn * 255f).toInt()

        if (alpha > 0) {
            for (s in labels) {
                val c = layout.toScreen(rMid, s.angle)
                val isSelected = selected != null && s.key == selected
                val text = if (isSelected && override != null) override else s.label
                labelPaint.color = when {
                    isSelected -> amber
                    s.special -> ivoryDim
                    else -> ivory
                }
                labelPaint.alpha = alpha
                labelPaint.textSize = if (text.length > 1) min(holeR * 0.8f, 12f * density) else min(holeR * 1.25f, 22f * density)
                drawCentered(canvas, text, c.x, c.y, labelPaint)
            }
        }

        val pivot = layout.toScreen(0f, 0f)
        platePath.rewind()
        platePath.fillType = Path.FillType.EVEN_ODD
        platePath.addCircle(pivot.x, pivot.y, rOut - gap, Path.Direction.CW)
        platePath.addCircle(pivot.x, pivot.y, rIn + gap, Path.Direction.CW)
        for (h in holes) {
            val c = layout.toScreen(rMid, h + plate.rotation)
            platePath.addCircle(c.x, c.y, holeR, Path.Direction.CW)
        }
        canvas.drawPath(platePath, platePaint)
        canvas.drawCircle(pivot.x, pivot.y, rOut - gap, plateEdgePaint)
        canvas.drawCircle(pivot.x, pivot.y, rIn + gap, plateEdgePaint)

        // Hole rims; the hole that has travelled over the selected label is lit.
        val target = if (selected == null) null else labels.firstOrNull { it.key == selected }
        var lit = Float.NaN
        if (target != null) {
            var best = Float.MAX_VALUE
            for (h in holes) {
                val d = abs(h + plate.rotation - target.angle)
                if (d < best) {
                    best = d
                    lit = h
                }
            }
        }
        for (h in holes) {
            val c = layout.toScreen(rMid, h + plate.rotation)
            canvas.drawCircle(c.x, c.y, holeR, if (h == lit) holeSelectedRimPaint else holeRimPaint)
        }
    }

    /** The chrome finger stop at the top end of the outer ring, as on a telephone dial. */
    private fun drawFingerStop(canvas: Canvas, layout: DialLayout) {
        val (rIn, rOut) = layout.ringRadii(Ring.OUTER)
        val a = layout.range(Ring.OUTER).first + rad(2.5f)
        val p0 = layout.toScreen(rIn + 8f * density, a)
        val p1 = layout.toScreen(rOut - 3f * density, a)
        canvas.drawLine(p0.x, p0.y, p1.x, p1.y, stopShadowPaint)
        canvas.drawLine(p0.x, p0.y, p1.x, p1.y, stopPaint)
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
    // Plates: following the thumb, and springing home with pulses
    // ---------------------------------------------------------------------------------------

    /** Matches the plates to what the recognizer says the thumb holds; a plate let go of springs home. */
    private fun syncPlates(now: Long) {
        val rec = recognizer
        val layout = layout
        val grabbed = if (touching && rec != null) rec.grabbedRing else null
        for (ring in Ring.entries) {
            val plate = plate(ring)
            if (rec != null && layout != null && ring == grabbed) {
                val target = rec.rotation
                if (!plate.grabbed) {
                    plate.grabbed = true
                    plate.springing = false
                    plate.catchUp = plate.rotation - target
                    plate.catchUpStart = now
                }
                plate.thumbRotation = target
                if (pulseMode) syncLegend(plate, ring, rec, layout, now)
            } else if (plate.grabbed) {
                release(plate, ring, layout, now)
            }
        }
        if (step(now)) schedule()
    }

    /** Lays the relative labels of a pulse ring around the hole the thumb entered at; the outer plate switches to finals. */
    private fun syncLegend(plate: Plate, ring: Ring, rec: DialRecognizer, layout: DialLayout, now: Long) {
        if (ring == Ring.OUTER) {
            val finalEntry = rec.finalEntryAngle
            val finals = finalEntry != null
            val count = DialLayers.outer(layer).size
            val legend = finalEntry?.let { layout.itemCenterAngle(Ring.OUTER, count, layout.index(Ring.OUTER, it, count)) }
            if (finals != plate.finals || (legend != null && legend != plate.legendAngle)) {
                plate.finals = finals
                if (legend != null) plate.legendAngle = legend
                plate.labelsChangedAt = now
            }
        } else {
            val entry = rec.vowelEntryAngle ?: return
            val legend = layout.snapToGrid(ring, entry, tickRad)
            if (legend != plate.legendAngle) {
                plate.legendAngle = legend
                plate.labelsChangedAt = now
            }
        }
    }

    private fun release(plate: Plate, ring: Ring, layout: DialLayout?, now: Long) {
        plate.grabbed = false
        plate.catchUp = 0f
        if (abs(plate.rotation) < 1e-3f) {
            plate.rotation = 0f
            return
        }
        plate.springing = true
        plate.springFrom = plate.rotation
        plate.springStart = now
        plate.springStep = if (layout != null) holeStep(ring, layout) else tickRad
        plate.springDuration = (abs(plate.rotation) / plate.springStep * MS_PER_PULSE).toLong().coerceIn(120L, 420L)
        plate.springPulsed = 0
    }

    /** Advances every animation to [now]: a grabbed plate eases onto the thumb, a released one springs home clicking per hole. */
    private fun step(now: Long): Boolean {
        var active = false
        for (ring in Ring.entries) {
            val plate = plate(ring)
            if (plate.grabbed) {
                val t = ((now - plate.catchUpStart).toFloat() / CATCH_UP_MS).coerceIn(0f, 1f)
                plate.rotation = plate.thumbRotation + plate.catchUp * (1f - t)
                if (t < 1f) active = true
            } else if (plate.springing) {
                val t = ((now - plate.springStart).toFloat() / plate.springDuration).coerceIn(0f, 1f)
                val eased = 1f - (1f - t) * (1f - t)
                plate.rotation = plate.springFrom * (1f - eased)
                val passed = (abs(plate.springFrom - plate.rotation) / plate.springStep).toInt()
                while (plate.springPulsed < passed) {
                    plate.springPulsed++
                    pulse(now)
                }
                if (t >= 1f) {
                    plate.springing = false
                    plate.rotation = 0f
                    if (ring == Ring.OUTER && plate.finals) {
                        plate.finals = false
                        plate.labelsChangedAt = now
                    }
                } else {
                    active = true
                }
            }
            if (now - plate.labelsChangedAt < LABEL_FADE_MS) active = true
        }
        return active
    }

    private fun schedule() {
        if (frameScheduled) return
        frameScheduled = true
        postOnAnimation(frame)
    }

    /** One pulse of the dial: a tick in the thumb and a click in the ear, at most one every few milliseconds. */
    private fun pulse(now: Long) {
        if (now - lastPulseAt < MIN_PULSE_GAP_MS) return
        lastPulseAt = now
        haptic(HapticFeedbackConstants.CLOCK_TICK)
        if (settings.clicks) (click ?: PulseClick(context).also { click = it }).play()
    }

    // ---------------------------------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val layout = layout ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                if (event.y < barHeight) {
                    inBar = true
                    return true
                }
                inBar = false
                beginTouch(event.x, dialY(event.y, layout))
            }
            MotionEvent.ACTION_MOVE -> {
                if (inBar || !touching) return true
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) moveTouch(event.getX(index), dialY(event.getY(index), layout))
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

    /** View y → dial y; touches below the dial (over the navigation-bar padding) count as its bottom edge. */
    private fun dialY(y: Float, layout: DialLayout): Float = (y - barHeight).coerceAtMost(layout.height - 1f)

    /** Every gesture already fired through [Listener.onResult]; this only satisfies accessibility click semantics. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun beginTouch(x: Float, y: Float) {
        val rec = recognizer ?: return
        val now = AnimationUtils.currentAnimationTimeMillis()
        touching = true
        repeating = false
        moved = false
        startX = x
        startY = y
        trail.clear()
        trail += Pt(x, y)
        val outer = plate(Ring.OUTER)
        if (outer.finals) {
            outer.finals = false
            outer.labelsChangedAt = now
        }
        rec.begin(x, y)
        current = rec.result()
        syncPlates(now)
        previewText = listener?.preview(current) ?: ""
        haptic(HapticFeedbackConstants.KEYBOARD_TAP)
        if (current is DialRecognizer.Result.Hub) handler.postDelayed(holdRunnable, HOLD_DELAY_MS)
        invalidate()
    }

    private fun moveTouch(x: Float, y: Float) {
        val rec = recognizer ?: return
        val now = AnimationUtils.currentAnimationTimeMillis()
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
            pulse(now)
        }
        syncPlates(now)
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
        syncPlates(AnimationUtils.currentAnimationTimeMillis())
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        removeCallbacks(frame)
        frameScheduled = false
        click?.release()
        click = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_CANDIDATES = 4
        const val HOLD_DELAY_MS = 450L
        const val REPEAT_INTERVAL_MS = 55L

        /** How long the spring back takes per hole; a real dial does about 100 ms, this one hurries. */
        const val MS_PER_PULSE = 45f

        /** A freshly grabbed plate glides onto the thumb instead of jumping. */
        const val CATCH_UP_MS = 120f

        /** A relabelled base fades in instead of popping. */
        const val LABEL_FADE_MS = 110f
        const val MIN_PULSE_GAP_MS = 24L
    }
}
