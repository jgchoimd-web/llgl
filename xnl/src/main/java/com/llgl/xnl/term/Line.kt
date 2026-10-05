package com.llgl.xnl.term

/** What a line is, which decides its colour. */
enum class Kind { BANNER, PROMPT, OUTPUT, ERROR, DIM }

/** One logical line before wrapping. A prompt line is the prompt prefix plus whatever has been typed after it. */
class Line(var text: String, val kind: Kind, val promptLen: Int = 0)

/** One screen row: a slice of a logical line, the way the renderer draws it. Reused frame to frame. */
class VisualRow {
    var line: Line = EMPTY
    var start = 0
    var end = 0

    val length: Int get() = end - start
    val text: String get() = line.text.substring(start, end)

    private companion object {
        val EMPTY = Line("", Kind.DIM)
    }
}
