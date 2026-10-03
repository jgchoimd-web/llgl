package com.llgl.gameforge.harness

/**
 * Applies a model reply to a game script: replaces declarations by name, appends new ones, drops
 * deleted ones. Variables that would redeclare an engine name become plain assignments, so a
 * `const rand = …` does not kill the whole script with a SyntaxError.
 */
object CodeMerge {
    data class Result(
        val code: String,
        val replaced: List<String>,
        val added: List<String>,
        val deleted: List<String>,
        val converted: List<String>,
        val problems: List<String>,
    ) {
        val changed: Boolean get() = replaced.isNotEmpty() || added.isNotEmpty() || deleted.isNotEmpty()

        /** "update, draw 수정 · spawn 추가 · old 삭제" for the history line. */
        fun summary(): String {
            val parts = mutableListOf<String>()
            if (replaced.isNotEmpty()) parts += replaced.joinToString(", ") + " 수정"
            if (added.isNotEmpty()) parts += added.joinToString(", ") + " 추가"
            if (deleted.isNotEmpty()) parts += deleted.joinToString(", ") + " 삭제"
            return parts.joinToString(" · ")
        }
    }

    private val DECL_KEYWORD = Regex("^(\\s*)(?:const|let|var)\\s+")

    fun apply(base: String, reply: ModelReply, reservedNames: Set<String>): Result {
        val units = JsDecls.parse(base).toMutableList()
        val replaced = mutableListOf<String>()
        val added = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val converted = mutableListOf<String>()
        val problems = mutableListOf<String>()

        for (incoming in reply.decls) {
            var decl = incoming
            val name = decl.name
            if (name != null && decl.kind == Decl.Kind.VARIABLE && name in reservedNames) {
                decl = decl.copy(source = DECL_KEYWORD.replaceFirst(decl.source, "$1"))
                converted += name
            }
            if (name != null) {
                val at = units.indexOfLast { it.name == name }
                if (at >= 0) {
                    units[at] = decl
                    replaced += name
                } else {
                    units += decl
                    added += name
                }
            } else {
                val duplicate = units.any { it.name == null && it.source.trim() == decl.source.trim() }
                if (!duplicate) {
                    units += decl
                    added += if (decl.kind == Decl.Kind.VARIABLE) "(변수)" else "(코드)"
                }
            }
        }
        for (name in reply.deletes) {
            if (units.removeAll { it.name == name }) deleted += name else problems += "$name: 지울 선언이 없어요"
        }
        return Result(JsDecls.join(units), replaced.distinct(), added.distinct(), deleted.distinct(), converted.distinct(), problems)
    }
}
