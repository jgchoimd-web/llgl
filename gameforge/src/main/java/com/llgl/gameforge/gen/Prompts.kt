package com.llgl.gameforge.gen

/**
 * What we say to Gemma. Instructions are in English because small models follow English coding
 * instructions more reliably; the games themselves are asked to show Korean text.
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

    /** How long a game we can afford: the whole prompt plus the whole file must fit the model's context. */
    fun linesFor(contextTokens: Int): Int = when {
        contextTokens <= 2048 -> 110
        contextTokens <= 4096 -> 200
        else -> 300
    }

    fun create(idea: String, maxLines: Int): String = """
You are an expert HTML5 game developer. Build a small, complete, playable game for a phone from this idea:

"${idea.trim()}"

Rules:
1. Reply with exactly one HTML document, starting with <!DOCTYPE html> and ending with </html>. No words before or after it, no markdown fences, no explanations.
2. Everything inline in that one file: one <style> block and one <script> block. No external files, libraries, images, fonts or network requests.
3. It runs on a phone in portrait with touch only: use touch events (touchstart/touchmove/touchend with preventDefault) plus mouse events for desktop. No keyboard needed. Touch targets at least 48 px.
4. Draw on one <canvas> that fills the window: size it from window.innerWidth and window.innerHeight, scale by devicePixelRatio, redraw on resize. Dark background, bright simple shapes, readable text. All on-screen text in Korean.
5. Flow: a start screen ("탭하여 시작") → play with a visible score → a game-over screen with the score and "다시 하기". Game loop with requestAnimationFrame and delta time.
6. Plain JavaScript (ES2017), at most $maxLines lines in total, no TODO or placeholder code, no alert/prompt/confirm, no console spam. Declare every variable, define every function before it is used, and double-check that the code runs without errors.
""".trimIndent()

    fun revise(html: String, request: String): String = """
Here is a complete HTML5 phone game (one file, canvas, touch controls):

${html.trim()}

Change it as requested, and keep everything else working:
"${request.trim()}"

Reply with the complete updated HTML document only: start with <!DOCTYPE html>, end with </html>, no markdown, no explanations. Keep it self-contained (inline style and script, no external resources).
""".trimIndent()

    fun fix(html: String, errors: List<String>): String = """
This HTML5 phone game throws errors when it runs:

${html.trim()}

Errors reported by the browser:
${errors.joinToString("\n") { "- ${it.trim()}" }}

Fix the bugs so the game runs cleanly and plays as intended. Reply with the complete corrected HTML document only: start with <!DOCTYPE html>, end with </html>, no markdown, no explanations.
""".trimIndent()
}
