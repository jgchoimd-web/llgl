package com.llgl.gameforge.gen

import com.llgl.gameforge.harness.Shell

/**
 * What we say to Gemma. Instructions are in English because small models follow English coding
 * instructions more reliably; the games themselves are asked to show Korean text. The model never
 * writes a whole page: the engine template owns the page, the model writes or edits declarations.
 */
object Prompts {
    val suggestions: List<String> = listOf(
        "벽돌 깨기",
        "뱀 게임",
        "탭해서 점프하는 공룡 달리기",
        "두더지 잡기",
        "떨어지는 과일 받기",
        "숫자 합치기 2048",
        "컴퓨터와 틱택토",
        "장애물 피하는 우주선",
        "리듬에 맞춰 탭",
        "미로 탈출",
        "핑퐁",
        "풍선 터뜨리기",
        "카드 짝 맞추기",
        "블록 탑 쌓기",
        "플래피 버드",
        "색깔 맞추기 반응 속도",
        "지렁이 키우기",
        "운석 피하기",
    )

    /** Lines of game code (the engine is not counted) a fresh game may use, by model context. */
    fun linesFor(contextTokens: Int): Int = when {
        contextTokens <= 2048 -> 80
        contextTokens <= 4096 -> 150
        else -> 220
    }

    fun create(idea: String, maxLines: Int): String = """
You write the game logic for a tiny HTML5 canvas game engine that already handles the canvas, the frame loop, touch input, the title screen, the score HUD and the game-over screen. Make this game for a phone:

"${idea.trim()}"

${Shell.apiSummary}

Rules:
1. Reply with one ```js code block containing only top-level declarations: const CONFIG, your variables, and the hook functions you need (at least update and draw). No HTML, no <script> tags, no DOM access, no requestAnimationFrame, no event listeners, no external resources.
2. Plain JavaScript (ES2017), at most $maxLines lines. Declare every variable with let or const at top level and give it its starting value in reset(). Define every helper function you call. No alert, prompt or console.log. Double-check that it runs without errors.
3. Playable with one thumb in portrait: big touch targets, react to onTap, onDrag or onSwipe. Dark background, bright simple shapes, Korean text. Make it fun: something moves, something to react to, a way to lose, a score.
""".trimIndent()

    fun edit(request: String, shown: String, hiddenMap: String, allowRead: Boolean): String = """
You maintain the game logic of a small HTML5 canvas game. The engine is fixed; only the game script can change.

${Shell.apiSummary}

${scriptBlock(shown, hiddenMap)}
Request: "${request.trim()}"

${replyRules(allowRead)}
""".trimIndent()

    fun fix(errors: List<String>, shown: String, hiddenMap: String, allowRead: Boolean): String = """
You maintain the game logic of a small HTML5 canvas game. The engine is fixed; only the game script can change.

${Shell.apiSummary}

${scriptBlock(shown, hiddenMap)}
The browser reported these errors while running the game:
${errors.joinToString("\n") { "- ${it.trim()}" }}

Fix the bugs so the game runs cleanly and plays as intended. ${replyRules(allowRead)}
""".trimIndent()

    private fun scriptBlock(shown: String, hiddenMap: String): String {
        val heading = if (hiddenMap.isEmpty()) "Current game script:" else "Current game script (only the relevant part; the rest is listed below):"
        val rest = if (hiddenMap.isEmpty()) "" else "\nOther declarations in the script, not shown:\n$hiddenMap\n"
        return "$heading\n```js\n${shown.trimEnd()}\n```\n$rest"
    }

    private fun replyRules(allowRead: Boolean): String {
        val read = if (allowRead) " If you must see a declaration that is not shown before you can answer, reply with just `READ name1, name2` and nothing else." else ""
        return "Reply with one ```js block containing ONLY the top-level declarations you change or add, each one complete (the whole function or variable). Do not repeat unchanged code and do not write anything outside the block. To remove a declaration, write a line `// DELETE name` inside the block.$read"
    }
}
