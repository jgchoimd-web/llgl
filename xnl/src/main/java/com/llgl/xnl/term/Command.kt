package com.llgl.xnl.term

/**
 * Something the terminal runs. A real shell line (`builtin=false`, `script=null`) is executed in the
 * phone's `/system/bin/sh`. An `xnl` built-in (`builtin=true`) is answered from the app's own APIs.
 * A scripted command (`script != null`) only ever shows its canned lines — the session flavour and
 * the easter eggs are scripted, so they are shown as text and never executed by anything.
 */
class Command(
    val text: String,
    val builtin: Boolean,
    val weight: Float,
    val period: Float,
    val script: List<String>? = null,
) {
    val scripted: Boolean get() = script != null
    override fun toString(): String = text
}

/** What came back: the lines exactly as produced, and whether the command worked. */
class CommandResult(val lines: List<String>, val ok: Boolean)
