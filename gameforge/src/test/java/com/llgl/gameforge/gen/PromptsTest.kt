package com.llgl.gameforge.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test
    fun `create prompt carries the idea, the line budget and the output contract`() {
        val p = Prompts.create("  뱀 게임  ", 150)
        assertTrue(p.contains("\"뱀 게임\""))
        assertTrue(p.contains("at most 150 lines"))
        assertTrue(p.contains("<!DOCTYPE html>"))
        assertTrue(p.contains("</html>"))
        assertTrue(p.contains("Korean"))
        assertTrue(p.contains("touch"))
    }

    @Test
    fun `line budget shrinks with the context`() {
        assertEquals(110, Prompts.linesFor(1280))
        assertEquals(110, Prompts.linesFor(2048))
        assertEquals(200, Prompts.linesFor(4096))
        assertEquals(300, Prompts.linesFor(8192))
    }

    @Test
    fun `revise and fix prompts embed the current game`() {
        val html = "<!DOCTYPE html><html><body>game</body></html>"
        val r = Prompts.revise(html, "공을 더 빠르게")
        assertTrue(r.contains(html))
        assertTrue(r.contains("\"공을 더 빠르게\""))
        val f = Prompts.fix(html, listOf("Uncaught ReferenceError: ball is not defined (줄 12)", " x is null "))
        assertTrue(f.contains(html))
        assertTrue(f.contains("- Uncaught ReferenceError: ball is not defined (줄 12)"))
        assertTrue(f.contains("- x is null"))
    }

    @Test
    fun `suggestions are distinct and short`() {
        assertEquals(Prompts.suggestions.size, Prompts.suggestions.toSet().size)
        assertTrue(Prompts.suggestions.all { it.isNotBlank() && it.length <= 20 })
    }
}
