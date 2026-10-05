package com.llgl.gameforge.harness

/**
 * The HTML page a game lives in: the engine template shipped with the app, or the wrapper an
 * older whole-file game was split into. The game script is pasted in at [PLACEHOLDER].
 */
object Shell {
    const val PLACEHOLDER = "/*__GAME__*/"
    const val TITLE_PLACEHOLDER = "__TITLE__"
    const val ENGINE_VERSION = 1

    val template: String by lazy {
        Shell::class.java.getResourceAsStream("/gameforge/shell.html")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("shell.html missing from resources")
    }

    /** Everything the engine script declares at top level; a game must not redeclare these as variables. */
    val engineNames: Set<String> by lazy {
        val engine = SCRIPT.findAll(template).map { it.groupValues[1] }.firstOrNull { it.contains("gameforge engine") } ?: ""
        JsDecls.names(JsDecls.parse(engine))
    }

    fun assemble(shell: String, gameJs: String, title: String): String =
        shell.replace(TITLE_PLACEHOLDER, escape(title)).replace(PLACEHOLDER, gameJs.trimEnd())

    /** The HTML line number of the game script's first line, so browser line numbers map back to game lines. */
    fun gameLineOffset(shell: String): Int {
        val lines = shell.split('\n')
        val index = lines.indexOfFirst { it.contains(PLACEHOLDER) }
        return if (index < 0) 1 else index + 1
    }

    fun htmlLineToGameLine(shell: String, htmlLine: Int): Int = htmlLine - gameLineOffset(shell) + 1

    /** The game's title as written in CONFIG, if any. */
    fun titleFrom(gameJs: String): String? {
        val config = JsDecls.find(JsDecls.parse(gameJs), "CONFIG")?.source ?: return null
        val match = TITLE.find(config) ?: return null
        return match.groupValues[2].trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Turns a whole-file game (the old format) into a shell plus its script: the largest script
     * becomes the game script and the page keeps everything else. Null when there is no script.
     */
    fun splitLegacy(html: String): Pair<String, String>? {
        val scripts = SCRIPT.findAll(html).toList()
        val main = scripts.filter { !it.value.contains(HtmlMarker) }.maxByOrNull { it.groupValues[1].length } ?: return null
        val body = main.groupValues[1]
        val range = main.groups[1]?.range ?: return null
        val shell = html.substring(0, range.first) + "\n" + PLACEHOLDER + "\n" + html.substring(range.last + 1)
        return shell to body.trim('\n')
    }

    private const val HtmlMarker = "data-gameforge"
    private val SCRIPT = Regex("<script\\b[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE = Regex("title\\s*:\\s*(['\"`])(.*?)\\1")

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** The engine API as the model sees it. */
    val apiSummary: String = """
Engine globals you can use (already defined; do not redefine any of them):
- W, H: screen size in CSS pixels (portrait phone). ctx: the 2D canvas context, already scaled; the engine clears the canvas every frame.
- G.score (read only; use addScore/setScore), G.time: seconds since the round began, G.state: 'title' | 'play' | 'over', G.touch: {down, x, y} for the current finger.
- addScore(n = 1), setScore(n), gameOver(): ends the round. The engine draws the title screen, the score HUD and the game-over screen, and restarts on tap.
- rand(a, b), randInt(a, b) (inclusive), clamp(v, lo, hi), lerp(a, b, t), dist(x1, y1, x2, y2), hitCircle(a, b) for objects with x, y, r, hitRect(a, b) for objects with x, y, w, h.
- circle(x, y, r, color), rect(x, y, w, h, color), line(x1, y1, x2, y2, color, width), text(str, x, y, size, color, align = 'center'), vibrate(ms).
Hooks the engine calls (define the ones you need as top-level function declarations):
- const CONFIG = { title: '…', hint: '…' }: Korean title and one line telling how to play.
- reset(): start a new round (also called once before the title screen, so give every variable a value here).
- update(dt): advance the game by dt seconds; call gameOver() when the player loses.
- draw(): draw the world with ctx and the helpers. Keep text in Korean.
- onTap(x, y), onDown(x, y), onDrag(x, y, dx, dy), onUp(x, y), onSwipe(dir) with dir 'left' | 'right' | 'up' | 'down'.
""".trimIndent()

    /** The same, for the person editing the code. */
    val apiGuideKo: String = """
엔진이 이미 만들어 둔 것 (다시 선언하지 마세요)
• W, H: 화면 크기(px). ctx: 2D 캔버스 컨텍스트. 매 프레임 엔진이 화면을 지웁니다.
• G.score(읽기), G.time(라운드 경과 초), G.state('title'|'play'|'over'), G.touch {down, x, y}
• addScore(n=1), setScore(n), gameOver(): 라운드 종료. 시작 화면·점수·게임오버 화면·재시작은 엔진이 그립니다.
• rand(a,b), randInt(a,b), clamp(v,lo,hi), lerp(a,b,t), dist(x1,y1,x2,y2), hitCircle(a,b){x,y,r}, hitRect(a,b){x,y,w,h}
• circle(x,y,r,색), rect(x,y,w,h,색), line(x1,y1,x2,y2,색,굵기), text(글,x,y,크기,색,정렬), vibrate(ms)

게임이 정의하는 것 (필요한 것만, 최상위 function으로)
• const CONFIG = { title: '제목', hint: '한 줄 설명' }
• reset(): 새 라운드 준비(시작 화면 전에도 한 번 불립니다)
• update(dt): dt초만큼 진행, 지면 gameOver()
• draw(): 세상 그리기
• onTap(x,y), onDown(x,y), onDrag(x,y,dx,dy), onUp(x,y), onSwipe('left'|'right'|'up'|'down')
""".trimIndent()
}
