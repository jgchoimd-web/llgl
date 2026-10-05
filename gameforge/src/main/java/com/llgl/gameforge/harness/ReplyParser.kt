package com.llgl.gameforge.harness

/** What the model answered, taken apart: code units to merge, names to delete, names it asked to read. */
data class ModelReply(
    val decls: List<Decl>,
    val deletes: List<String>,
    val reads: List<String>,
    val raw: String,
) {
    val isEmpty: Boolean get() = decls.isEmpty() && deletes.isEmpty()
}

/**
 * Lenient reading of a small model's reply: code may come fenced or bare, as a whole HTML page out
 * of habit, with chatter around it, with `READ a, b` requests and `// DELETE name` directives.
 */
object ReplyParser {
    private val FENCE = Regex("```[A-Za-z0-9_-]*[ \\t]*\\r?\\n(.*?)(?:```|$)", RegexOption.DOT_MATCHES_ALL)
    private val READ = Regex("^\\s*READ\\b[:\\s]*(.+?)\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val DELETE = Regex("^\\s*//\\s*DELETE\\b[:\\s]*([A-Za-z_$][\\w$]*)\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val SCRIPT = Regex("<script\\b[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val IDENT = Regex("[A-Za-z_$][\\w$]*")

    fun parse(raw: String): ModelReply {
        val reads = READ.findAll(raw)
            .flatMap { m -> IDENT.findAll(m.groupValues[1]).map { it.value } }
            .filter { it.lowercase() != "read" }
            .distinct()
            .toList()

        val fenced = FENCE.findAll(raw).map { it.groupValues[1] }.toList()
        var code = if (fenced.isNotEmpty()) fenced.joinToString("\n") else raw
        if (code.contains("<script", ignoreCase = true)) {
            // The model wrote a page: keep the game script (the biggest one that is not our engine).
            val bodies = SCRIPT.findAll(code).map { it.groupValues[1] }
                .filter { !it.contains("gameforge engine") && !it.contains("__gameforgeBoot") }
                .toList()
            code = bodies.maxByOrNull { it.length } ?: ""
        }
        val deletes = DELETE.findAll(code).map { it.groupValues[1] }.distinct().toList()
        code = code.lines().filter { !DELETE.matches(it) && !READ.matches(it) }.joinToString("\n")

        var decls = JsDecls.parse(code)
        if (fenced.isEmpty()) {
            // Bare text: prose lines parse as loose code, so only trust real declarations.
            decls = decls.filter { it.kind != Decl.Kind.CODE }
        }
        return ModelReply(decls, deletes, reads, raw)
    }
}
