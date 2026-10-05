package com.llgl.xnl.term

import java.util.Locale

/** Small formatters for the built-ins, the way command-line tools print things. */
object Format {
    /** "3d 04:12:33" or "04:12:33"; nothing negative. */
    fun duration(ms: Long): String {
        val total = if (ms < 0) 0L else ms / 1000L
        val days = total / 86400L
        val h = (total % 86400L) / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        val clock = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        return if (days > 0) "${days}d $clock" else clock
    }

    /** "900B", "1.5K", "512M", "3.0G". */
    fun bytes(b: Long): String {
        if (b < 0) return "?"
        val units = arrayOf("B", "K", "M", "G", "T")
        var v = b.toDouble()
        var u = 0
        while (v >= 1024.0 && u < units.size - 1) {
            v /= 1024.0
            u++
        }
        if (u == 0) return "${b}B"
        return if (v >= 100.0) String.format(Locale.US, "%.0f%s", v, units[u]) else String.format(Locale.US, "%.1f%s", v, units[u])
    }

    /** Key/value rows with the values aligned, like a tool's status output. */
    fun table(rows: List<Pair<String, String>>): List<String> {
        val w = rows.maxOfOrNull { it.first.length } ?: 0
        return rows.map { (k, v) -> k.padEnd(w) + "  " + v }
    }

    /** Tabs become spaces to the next multiple of 8, as a terminal would show them. */
    fun expandTabs(s: String): String {
        if (s.indexOf('\t') < 0) return s
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            if (ch == '\t') {
                do sb.append(' ') while (sb.length % 8 != 0)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Control characters out, tabs expanded, over-long lines cut: what the terminal is willing to show. */
    fun clean(raw: String, maxLength: Int = 400): String {
        val expanded = expandTabs(ANSI.replace(raw.trimEnd('\r', '\n'), ""))
        val sb = StringBuilder(expanded.length)
        for (ch in expanded) {
            if (ch.code >= 32) sb.append(ch)
        }
        return if (sb.length > maxLength) sb.substring(0, maxLength - 1) + "…" else sb.toString()
    }

    private val ANSI = Regex("\u001b\\[[0-9;?]*[ -/]*[@-~]")
}
