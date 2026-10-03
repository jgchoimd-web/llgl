package com.llgl.gameforge.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test
    fun `create prompt carries the idea, the budget, the api and the output contract`() {
        val p = Prompts.create("  뱀 게임  ", 150)
        assertTrue(p.contains("\"뱀 게임\""))
        assertTrue(p.contains("at most 150 lines"))
        assertTrue(p.contains("gameOver()"))
        assertTrue(p.contains("onSwipe"))
        assertTrue(p.contains("```js"))
        assertTrue(p.contains("No HTML"))
        assertTrue(p.contains("Korean"))
    }

    @Test
    fun `line budget shrinks with the context`() {
        assertEquals(80, Prompts.linesFor(1280))
        assertEquals(80, Prompts.linesFor(2048))
        assertEquals(150, Prompts.linesFor(4096))
        assertEquals(220, Prompts.linesFor(8192))
    }

    @Test
    fun `edit prompt shows the script, the request and the merge rules`() {
        val code = "function update(dt) {\n  x += dt;\n}"
        val whole = Prompts.edit("공을 더 빠르게", code, "", allowRead = false)
        assertTrue(whole.contains(code))
        assertTrue(whole.contains("\"공을 더 빠르게\""))
        assertTrue(whole.contains("// DELETE name"))
        assertTrue(whole.contains("ONLY the top-level declarations"))
        assertFalse(whole.contains("READ name1"))
        assertFalse(whole.contains("not shown"))

        val partial = Prompts.edit("x", code, "- function draw() {  (12줄)", allowRead = true)
        assertTrue(partial.contains("- function draw() {  (12줄)"))
        assertTrue(partial.contains("READ name1, name2"))
        assertTrue(partial.contains("not shown"))
    }

    @Test
    fun `fix prompt lists the errors`() {
        val p = Prompts.fix(listOf("Uncaught ReferenceError: ball is not defined (줄 12)", " x is null "), "let a = 1;", "", allowRead = false)
        assertTrue(p.contains("- Uncaught ReferenceError: ball is not defined (줄 12)"))
        assertTrue(p.contains("- x is null"))
        assertTrue(p.contains("let a = 1;"))
        assertTrue(p.contains("Fix the bugs"))
    }

    @Test
    fun `suggestions are distinct and short`() {
        assertEquals(Prompts.suggestions.size, Prompts.suggestions.toSet().size)
        assertTrue(Prompts.suggestions.all { it.isNotBlank() && it.length <= 20 })
    }
}
