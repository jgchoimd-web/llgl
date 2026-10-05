package com.llgl.gameforge.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellTest {

    @Test
    fun `the template ships and declares the documented api`() {
        val template = Shell.template
        assertTrue(template.contains(Shell.PLACEHOLDER))
        assertTrue(template.contains("<title>${Shell.TITLE_PLACEHOLDER}</title>"))
        assertTrue(template.contains("data-gameforge"))
        val names = Shell.engineNames
        for (n in listOf("W", "H", "G", "ctx", "rand", "randInt", "clamp", "lerp", "dist", "hitCircle", "hitRect", "circle", "rect", "line", "text", "addScore", "setScore", "gameOver", "vibrate", "__gameforgeBoot")) {
            assertTrue("engine should declare $n", n in names)
        }
        for (n in listOf("reset", "update", "draw", "onTap", "CONFIG")) {
            assertFalse("engine must not declare the game's $n", n in names)
        }
        // Every helper the summary promises exists.
        for (n in listOf("addScore", "setScore", "gameOver", "rand", "randInt", "clamp", "lerp", "dist", "hitCircle", "hitRect", "circle", "rect", "line", "text", "vibrate")) {
            assertTrue(Shell.apiSummary.contains(n))
            assertTrue(Shell.apiGuideKo.contains(n))
        }
    }

    @Test
    fun `assemble fills both placeholders and maps line numbers`() {
        val game = "const CONFIG = { title: 'A <b> & c' };\nfunction update(dt) {\n  nope();\n}\n"
        val html = Shell.assemble(Shell.template, game, "A <b> & c")
        assertTrue(html.contains("<title>A &lt;b&gt; &amp; c</title>"))
        assertFalse(html.contains(Shell.PLACEHOLDER))
        assertTrue(html.contains("function update(dt) {\n  nope();"))
        val offset = Shell.gameLineOffset(Shell.template)
        val htmlLines = html.split('\n')
        assertEquals("const CONFIG = { title: 'A <b> & c' };", htmlLines[offset - 1])
        assertEquals(3, Shell.htmlLineToGameLine(Shell.template, offset + 2))
        assertEquals("  nope();", htmlLines[offset + 1])
    }

    @Test
    fun `title is read from CONFIG`() {
        assertEquals("공 잡기", Shell.titleFrom("let x = 1;\nconst CONFIG = {\n  title: \"공 잡기\",\n  hint: 'tap'\n};"))
        assertNull(Shell.titleFrom("function update() {}"))
        assertNull(Shell.titleFrom("const CONFIG = { hint: 'x' };"))
    }

    @Test
    fun `a legacy whole-file game splits into shell and script`() {
        val legacy = """
            <!DOCTYPE html><html><head><meta charset="utf-8" data-gameforge><script data-gameforge>(function(){})();</script></head>
            <body><canvas></canvas>
            <script>
            const c = document.querySelector('canvas');
            function loop() { requestAnimationFrame(loop); }
            loop();
            </script>
            </body></html>
        """.trimIndent()
        val (shell, script) = Shell.splitLegacy(legacy)!!
        assertTrue(script.startsWith("const c = document.querySelector"))
        assertTrue(script.endsWith("loop();"))
        assertTrue(shell.contains(Shell.PLACEHOLDER))
        assertTrue(shell.contains("(function(){})();"))
        val back = Shell.assemble(shell, script, "x")
        assertTrue(back.contains("function loop() { requestAnimationFrame(loop); }"))
        assertEquals(1, Shell.htmlLineToGameLine(shell, Shell.gameLineOffset(shell)))
        assertNull(Shell.splitLegacy("<html><body>no script</body></html>"))
    }
}
