package com.llgl.gameforge.harness

/**
 * Decides which parts of the game script the model gets to see. Whole script when it fits the
 * budget; otherwise the pieces that matter for the task plus a map of the rest, which the model
 * can ask for with `READ name`.
 */
object ContextBuilder {
    data class Selection(val shown: List<Decl>, val hidden: List<Decl>) {
        val whole: Boolean get() = hidden.isEmpty()

        /** The code the model sees. */
        fun code(): String = JsDecls.join(shown)

        /** One line per unseen unit. */
        fun map(): String = hidden.joinToString("\n") { "- ${it.header}  (${it.lineCount}줄)" }
    }

    private val IDENT = Regex("[A-Za-z_$][A-Za-z0-9_$]{2,}")

    fun select(
        decls: List<Decl>,
        budgetChars: Int,
        request: String = "",
        errorLines: List<Int> = emptyList(),
        errorNames: List<String> = emptyList(),
        reads: List<String> = emptyList(),
    ): Selection {
        if (decls.sumOf { it.source.length } <= budgetChars) return Selection(decls, emptyList())

        val requested = reads.toSet()
        val mentioned = IDENT.findAll(request).map { it.value }.toSet()
        fun priority(d: Decl): Int = when {
            d.name != null && d.name in requested -> 0
            errorLines.any { d.contains(it) } || (d.name != null && d.name in errorNames) -> 1
            d.name == "CONFIG" || d.kind == Decl.Kind.VARIABLE -> 2
            d.name == "reset" -> 3
            d.name != null && d.name in mentioned -> 4
            mentioned.isNotEmpty() && mentioned.any { d.source.contains(it) } -> 5
            d.name == "update" || d.name == "draw" -> 6
            d.kind == Decl.Kind.CODE -> 7
            else -> 8
        }
        val ordered = decls.withIndex().sortedWith(compareBy({ priority(it.value) }, { it.value.source.length }))
        val shownIdx = mutableSetOf<Int>()
        var used = 0
        for ((index, d) in ordered) {
            val p = priority(d)
            if (p <= 1 || used + d.source.length <= budgetChars) {
                shownIdx += index
                used += d.source.length
            }
        }
        val shown = decls.filterIndexed { i, _ -> i in shownIdx }
        val hidden = decls.filterIndexed { i, _ -> i !in shownIdx }
        return Selection(shown, hidden)
    }

    /** Line numbers in error strings like "… (줄 123)", already converted to game-script lines by the caller. */
    fun errorLines(errors: List<String>, htmlToGame: (Int) -> Int): List<Int> =
        errors.mapNotNull { LINE.find(it)?.groupValues?.get(1)?.toIntOrNull() }.map(htmlToGame).filter { it > 0 }

    /** Identifiers that appear in error messages and name a unit, e.g. "update(): …" or "at spawn". */
    fun errorNames(errors: List<String>, decls: List<Decl>): List<String> {
        val names = JsDecls.names(decls)
        return errors.flatMap { e -> IDENT.findAll(e).map { it.value } }.filter { it in names }.distinct()
    }

    private val LINE = Regex("줄\\s*(\\d+)")
}
