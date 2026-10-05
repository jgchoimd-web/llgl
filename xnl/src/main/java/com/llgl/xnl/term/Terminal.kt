package com.llgl.xnl.term

import kotlin.math.max
import kotlin.math.min

/**
 * The terminal, pure Kotlin so it can be tested: logical lines hard-wrapped at the column count, a
 * viewport of the last rows that slides up briefly when new rows arrive, and the session loop that
 * types a command, waits for the host to run it for real, prints the result line by line and shows
 * a fresh prompt before the next one. The host polls [takeRequest] and answers with [deliver].
 */
class Terminal(seed: Int, val playbook: Playbook, val prompt: String) {
    enum class State { IDLE, TYPING, RUNNING, OUTPUT }

    private val rng = Rng(seed)
    private val buffer = ArrayList<Line>()

    /** The logical lines, oldest first. */
    val lines: List<Line> get() = buffer

    var columns = 56
        private set
    var rows = 40
        private set

    /** Scales typing, printing and the pauses between commands. */
    var speed = 1f
        set(value) {
            field = value.coerceIn(0.25f, 4f)
        }

    /** Seconds since the session started; the playbook's periods count in this time. */
    var time = 0f
        private set
    var state = State.IDLE
        private set

    /** Rows the content is still shifted down by, easing to zero after new rows pushed it up. */
    var slide = 0f
        private set

    /** Rows of all lines after wrapping. */
    var totalRows = 0
        private set
    var commandsRun = 0
        private set

    private var wait = 0f
    private var typing = ""
    private var typed = 0
    private var lastLineRows = 0
    private var request: Command? = null
    private var current: Command? = null
    private var result: CommandResult? = null
    private var outIndex = 0
    private var blink = 0f

    val lineCount: Int get() = buffer.size
    val lastLine: Line? get() = buffer.lastOrNull()

    /** True while something moves: typing, printing or the slide. The host can draw slower otherwise. */
    val animating: Boolean get() = state != State.IDLE || slide > 0f

    /** The block cursor: on at the prompt and while typing, blinking once a second. */
    val cursorOn: Boolean get() = (state == State.IDLE || state == State.TYPING) && blink % 1f < 0.6f

    /** Column of the cursor on the last row. */
    val cursorColumn: Int
        get() {
            val len = buffer.lastOrNull()?.text?.length ?: return 0
            val col = len % columns
            return if (len > 0 && col == 0) columns - 1 else col
        }

    /** The host prints its banner once, then the first prompt appears. */
    fun start(banner: List<String>) {
        for (l in banner) add(Line(l, Kind.BANNER))
        newPrompt()
        wait = 0.8f
    }

    fun resize(columns: Int, rows: Int) {
        this.columns = max(8, columns)
        this.rows = max(2, rows)
        totalRows = buffer.sumOf { rowsOf(it) }
        lastLineRows = buffer.lastOrNull()?.let { rowsOf(it) } ?: 0
        slide = 0f
    }

    /** The command the host must run now, handed over once. */
    fun takeRequest(): Command? {
        val r = request
        request = null
        return r
    }

    /** The real result of the command last requested. */
    fun deliver(r: CommandResult) {
        if (state == State.TYPING || state == State.RUNNING) result = r
    }

    /** A tap ends the pause before the next command. */
    fun tap() {
        if (state == State.IDLE) wait = 0f
    }

    fun step(dt: Float) {
        time += dt
        blink += dt
        if (slide > 0f) slide = max(0f, slide - dt * SLIDE_ROWS_PER_SEC * (1f + slide))
        val s = dt * speed
        when (state) {
            State.IDLE -> {
                wait -= s
                if (wait <= 0f) begin()
            }
            State.TYPING -> {
                wait -= s
                val line = buffer.last()
                while (wait <= 0f && typed < typing.length) {
                    typed++
                    line.text = prompt + typing.substring(0, typed)
                    blink = 0f
                    wait += rng.range(0.025f, 0.075f)
                    lastLineChanged()
                }
                if (typed >= typing.length) {
                    state = State.RUNNING
                    wait = 0.12f
                }
            }
            State.RUNNING -> {
                wait -= s
                val r = result
                if (r != null && wait <= 0f) {
                    state = State.OUTPUT
                    outIndex = 0
                    wait = 0f
                    if (!r.ok || r.lines.isEmpty()) current?.let { playbook.markBroken(it) }
                }
            }
            State.OUTPUT -> {
                val r = result
                if (r == null) {
                    finish()
                    return
                }
                wait -= s
                val perLine = if (r.lines.size > 30) 0.02f else 0.055f
                while (wait <= 0f && outIndex < r.lines.size) {
                    add(Line(r.lines[outIndex++], if (r.ok) Kind.OUTPUT else Kind.ERROR))
                    wait += perLine
                }
                if (outIndex >= r.lines.size) finish()
            }
        }
    }

    /** Fills `out` with the last rows plus a few above them for the slide, top to bottom; returns the count. */
    fun visible(out: MutableList<VisualRow>): Int {
        var need = rows + EXTRA_ROWS
        var first = buffer.size
        var i = buffer.size - 1
        while (i >= 0 && need > 0) {
            need -= rowsOf(buffer[i])
            first = i
            i--
        }
        var skip = if (need < 0) -need else 0
        var n = 0
        for (j in first until buffer.size) {
            val line = buffer[j]
            val r = rowsOf(line)
            for (k in 0 until r) {
                if (skip > 0) {
                    skip--
                    continue
                }
                val row = if (n < out.size) out[n] else VisualRow().also { out.add(it) }
                row.line = line
                row.start = k * columns
                row.end = min(line.text.length, (k + 1) * columns)
                n++
            }
        }
        return n
    }

    fun rowsOf(line: Line): Int = max(1, (line.text.length + columns - 1) / columns)

    private fun begin() {
        if (buffer.isEmpty() || buffer.last().kind != Kind.PROMPT) newPrompt()
        val c = playbook.next(time)
        if (c == null) {
            wait = 3f
            return
        }
        current = c
        typing = c.text
        typed = 0
        result = null
        request = c
        state = State.TYPING
        wait = 0.05f
    }

    private fun finish() {
        commandsRun++
        current = null
        result = null
        newPrompt()
        state = State.IDLE
        wait = rng.range(1.4f, 4f)
    }

    private fun newPrompt() {
        add(Line(prompt, Kind.PROMPT, prompt.length))
        blink = 0f
    }

    private fun add(line: Line) {
        while (buffer.size >= MAX_LINES) totalRows -= rowsOf(buffer.removeAt(0))
        buffer.add(line)
        lastLineRows = rowsOf(line)
        grow(lastLineRows)
    }

    private fun lastLineChanged() {
        val line = buffer.lastOrNull() ?: return
        val r = rowsOf(line)
        if (r != lastLineRows) {
            grow(r - lastLineRows)
            lastLineRows = r
        }
    }

    /** New rows at the bottom push the viewport: the content is drawn shifted down and slides up. */
    private fun grow(delta: Int) {
        val before = totalRows
        totalRows += delta
        val pushed = max(0, totalRows - rows) - max(0, before - rows)
        if (pushed > 0) slide = min(MAX_SLIDE, slide + pushed)
    }

    companion object {
        const val MAX_LINES = 400
        const val EXTRA_ROWS = 3
        const val MAX_SLIDE = 4f
        const val SLIDE_ROWS_PER_SEC = 14f
    }
}
