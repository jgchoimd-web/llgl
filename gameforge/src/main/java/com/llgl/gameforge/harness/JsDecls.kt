package com.llgl.gameforge.harness

/** One top-level unit of a game script: a function, a class, a variable, or loose code. */
data class Decl(
    val kind: Kind,
    /** null for loose code and destructuring declarations. */
    val name: String?,
    val source: String,
    /** 1-based, inclusive, in the script the unit was parsed from. */
    val startLine: Int,
    val endLine: Int,
) {
    enum class Kind { FUNCTION, CLASS, VARIABLE, CODE }

    val lineCount: Int get() = endLine - startLine + 1

    /** The first line of code (comments skipped), for maps shown to the model. */
    val header: String
        get() {
            val code = source.replace(BLOCK_COMMENT, "").lineSequence().map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("//") }
            return (code ?: source.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "").take(90)
        }

    fun contains(line: Int): Boolean = line in startLine..endLine

    private companion object {
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    }
}

/**
 * Splits a script into top-level declarations without a real parser: a depth counter that knows
 * about strings, template literals, comments and (roughly) regex literals decides where each unit
 * ends. Comments directly above a declaration belong to it; `let a = 1, b = 2;` becomes two units
 * so each name can be replaced on its own. Good enough for the code small models write, and the
 * merge only ever works on whole units, so a misjudged boundary cannot corrupt code inside one.
 */
object JsDecls {
    private val FUNCTION = Regex("^\\s*(?:async\\s+)?function\\s*\\*?\\s*([A-Za-z_$][\\w$]*)")
    private val CLASS = Regex("^\\s*class\\s+([A-Za-z_$][\\w$]*)")
    private val VARIABLE = Regex("^\\s*(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)")
    private val VARIABLE_ANY = Regex("^\\s*(?:const|let|var)\\b")
    private val KEYWORD = Regex("^\\s*(const|let|var)\\s+")
    private val NAME = Regex("^[A-Za-z_$][\\w$]*")
    private val STARTS_UNIT = Regex("^\\s*(?:(?:async\\s+)?function\\b|class\\b|const\\b|let\\b|var\\b|//|/\\*)")

    fun parse(code: String): List<Decl> {
        val lines = code.split('\n')
        val out = mutableListOf<Decl>()
        val scanner = Scanner()
        var i = 0
        var pendingComment = -1

        fun flushOrphanComment(upTo: Int) {
            if (pendingComment >= 0 && upTo > pendingComment) {
                out += Decl(Decl.Kind.CODE, null, lines.subList(pendingComment, upTo).joinToString("\n"), pendingComment + 1, upTo)
            }
            pendingComment = -1
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushOrphanComment(i)
                i++
                continue
            }
            if (trimmed.startsWith("//")) {
                if (pendingComment < 0) pendingComment = i
                i++
                continue
            }
            if (trimmed.startsWith("/*")) {
                if (pendingComment < 0) pendingComment = i
                var k = i
                while (k < lines.size && !lines[k].contains("*/")) k++
                i = k + 1
                continue
            }
            val start = if (pendingComment >= 0) pendingComment else i
            pendingComment = -1
            val (kind, name) = classify(line)
            val commas = mutableListOf<Pair<Int, Int>>()
            var j = i
            while (true) {
                scanner.feed(lines[j])
                if (kind == Decl.Kind.VARIABLE) for (col in scanner.commas) commas += (j - i) to col
                val next = if (j + 1 < lines.size) lines[j + 1] else null
                if (scanner.depth == 0 && !scanner.inComment && !scanner.inTemplate && unitEnds(kind, scanner, next)) break
                if (j == lines.size - 1) break
                j++
            }
            scanner.reset()
            val unitLines = lines.subList(start, j + 1)
            if (kind == Decl.Kind.VARIABLE && commas.isNotEmpty()) {
                out += splitVariables(unitLines, i - start, commas, start + 1)
            } else {
                out += Decl(kind, name, unitLines.joinToString("\n"), start + 1, j + 1)
            }
            i = j + 1
        }
        flushOrphanComment(lines.size)
        return out
    }

    fun names(decls: List<Decl>): Set<String> = decls.mapNotNull { it.name }.toSet()

    /** Joins units back into a script, one blank line apart. */
    fun join(decls: List<Decl>): String = decls.joinToString("\n\n") { it.source.trim() }.let { if (it.isEmpty()) "" else it + "\n" }

    fun find(decls: List<Decl>, name: String): Decl? = decls.lastOrNull { it.name == name }

    private fun classify(line: String): Pair<Decl.Kind, String?> {
        FUNCTION.find(line)?.let { return Decl.Kind.FUNCTION to it.groupValues[1] }
        CLASS.find(line)?.let { return Decl.Kind.CLASS to it.groupValues[1] }
        VARIABLE.find(line)?.let { return Decl.Kind.VARIABLE to it.groupValues[1] }
        if (VARIABLE_ANY.containsMatchIn(line)) return Decl.Kind.VARIABLE to null
        return Decl.Kind.CODE to null
    }

    private fun unitEnds(kind: Decl.Kind, scanner: Scanner, next: String?): Boolean = when (kind) {
        Decl.Kind.FUNCTION, Decl.Kind.CLASS -> scanner.closedBraceToZero
        Decl.Kind.VARIABLE, Decl.Kind.CODE ->
            scanner.lastSignificant == ';' || scanner.closedBraceToZero || next == null || next.isBlank() || STARTS_UNIT.containsMatchIn(next)
    }

    /** `let a = 1, b = f(1, 2);` → `let a = 1;` and `let b = f(1, 2);`, comments staying with the first. */
    private fun splitVariables(unitLines: List<String>, firstCode: Int, commas: List<Pair<Int, Int>>, startLine: Int): List<Decl> {
        val source = unitLines.joinToString("\n")
        val lineStarts = IntArray(unitLines.size)
        var acc = 0
        for (k in unitLines.indices) {
            lineStarts[k] = acc
            acc += unitLines[k].length + 1
        }
        val codeStart = lineStarts[firstCode]
        val keywordMatch = KEYWORD.find(source.substring(codeStart))
            ?: return listOf(Decl(Decl.Kind.VARIABLE, VARIABLE.find(unitLines[firstCode])?.groupValues?.get(1), source, startLine, startLine + unitLines.size - 1))
        val keyword = keywordMatch.groupValues[1]
        val bodyStart = codeStart + keywordMatch.range.last + 1
        val cuts = commas.map { (li, col) -> lineStarts[firstCode + li] + col }.filter { it >= bodyStart }.sorted()
        if (cuts.isEmpty()) return listOf(Decl(Decl.Kind.VARIABLE, NAME.find(source.substring(bodyStart).trimStart())?.value, source, startLine, startLine + unitLines.size - 1))

        val head = source.substring(0, bodyStart)
        val bounds = mutableListOf<Pair<Int, Int>>()
        var prev = bodyStart
        for (c in cuts) {
            bounds += prev to c
            prev = c + 1
        }
        bounds += prev to source.length
        return bounds.mapIndexed { idx, (from, to) ->
            val raw = source.substring(from, to)
            val name = NAME.find(raw.trimStart())?.value
            val text = (if (idx == 0) head else "$keyword ") + terminate(raw.trim())
            val sLine = startLine + source.substring(0, from).count { it == '\n' }
            val eLine = startLine + source.substring(0, to).count { it == '\n' }
            Decl(Decl.Kind.VARIABLE, name, text, if (idx == 0) startLine else sLine, eLine)
        }
    }

    /** Ensures a statement ends with `;`, keeping a trailing line comment after it. */
    private fun terminate(part: String): String {
        val semi = part.lastIndexOf(';')
        if (semi >= 0) {
            val tail = part.substring(semi + 1)
            if (tail.isBlank() || tail.trimStart().startsWith("//")) return part
        }
        val comment = part.indexOf("//")
        return if (comment >= 0) part.substring(0, comment).trimEnd() + "; " + part.substring(comment) else "$part;"
    }

    /** Tracks nesting across lines; braces inside strings, templates, comments and regexes do not count. */
    private class Scanner {
        var depth = 0
        var inComment = false
        var closedBraceToZero = false
        var lastSignificant = ';'

        /** Columns of top-level commas seen in the last fed line. */
        val commas = mutableListOf<Int>()
        private val modes = ArrayDeque<Int>() // TEMPLATE_STRING markers and the depth each ${ started at

        val inTemplate: Boolean get() = modes.isNotEmpty()

        fun reset() {
            depth = 0
            inComment = false
            closedBraceToZero = false
            lastSignificant = ';'
            commas.clear()
            modes.clear()
        }

        fun feed(line: String) {
            closedBraceToZero = false
            commas.clear()
            var i = 0
            val n = line.length
            while (i < n) {
                val c = line[i]
                val next = if (i + 1 < n) line[i + 1] else '\u0000'
                if (inComment) {
                    if (c == '*' && next == '/') {
                        inComment = false
                        i += 2
                    } else {
                        i++
                    }
                    continue
                }
                if (modes.isNotEmpty() && modes.last() == TEMPLATE_STRING) {
                    when {
                        c == '\\' -> i += 2
                        c == '`' -> {
                            modes.removeLast()
                            i++
                        }
                        c == '$' && next == '{' -> {
                            modes.addLast(depth)
                            depth++
                            i += 2
                        }
                        else -> i++
                    }
                    continue
                }
                when {
                    c == '/' && next == '/' -> return
                    c == '/' && next == '*' -> {
                        inComment = true
                        i += 2
                    }
                    c == '\'' || c == '"' -> {
                        i = skipString(line, i, c)
                        lastSignificant = c
                    }
                    c == '`' -> {
                        modes.addLast(TEMPLATE_STRING)
                        lastSignificant = c
                        i++
                    }
                    c == '/' && regexAllowed(lastSignificant) -> {
                        i = skipRegex(line, i)
                        lastSignificant = '/'
                    }
                    c == '{' || c == '(' || c == '[' -> {
                        depth++
                        lastSignificant = c
                        i++
                    }
                    c == '}' -> {
                        if (modes.isNotEmpty() && modes.last() != TEMPLATE_STRING && depth - 1 == modes.last()) {
                            modes.removeLast() // back into the template string
                            depth--
                        } else {
                            depth = (depth - 1).coerceAtLeast(0)
                            if (depth == 0) closedBraceToZero = true
                        }
                        lastSignificant = c
                        i++
                    }
                    c == ')' || c == ']' -> {
                        depth = (depth - 1).coerceAtLeast(0)
                        lastSignificant = c
                        i++
                    }
                    c == ',' -> {
                        if (depth == 0) commas += i
                        lastSignificant = c
                        i++
                    }
                    c.isWhitespace() -> i++
                    else -> {
                        lastSignificant = c
                        i++
                    }
                }
            }
        }

        private fun skipString(line: String, start: Int, quote: Char): Int {
            var i = start + 1
            while (i < line.length) {
                val c = line[i]
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) return i + 1
                i++
            }
            return line.length
        }

        private fun skipRegex(line: String, start: Int): Int {
            var i = start + 1
            var inClass = false
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '\\' -> i += 2
                    c == '[' -> {
                        inClass = true
                        i++
                    }
                    c == ']' -> {
                        inClass = false
                        i++
                    }
                    c == '/' && !inClass -> {
                        i++
                        while (i < line.length && line[i].isLetter()) i++
                        return i
                    }
                    else -> i++
                }
            }
            return line.length
        }

        private fun regexAllowed(prev: Char): Boolean = prev in "(,=:[!&|?{};+-*%<>~^"

        companion object {
            const val TEMPLATE_STRING = -1
        }
    }
}
