package com.llgl.gameforge.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyParserTest {

    @Test
    fun `fenced code with chatter`() {
        val reply = ReplyParser.parse(
            "Sure! Here's the updated function:\n```javascript\nfunction update(dt) {\n  x += dt;\n}\n```\nLet me know if you need anything else.",
        )
        assertEquals(listOf("update"), reply.decls.map { it.name })
        assertTrue(reply.reads.isEmpty())
        assertTrue(reply.deletes.isEmpty())
    }

    @Test
    fun `bare code keeps declarations and drops prose`() {
        val reply = ReplyParser.parse(
            "I changed the speed so the game is harder.\nfunction update(dt) {\n  x += dt * 3;\n}\nconst MAX = 5;\nThat should do it.",
        )
        assertEquals(listOf("update", "MAX"), reply.decls.map { it.name })
    }

    @Test
    fun `read requests and delete directives`() {
        val reply = ReplyParser.parse("READ: draw, spawnEnemy and onTap\n")
        assertEquals(listOf("draw", "spawnEnemy", "and", "onTap"), reply.reads)
        assertTrue(reply.isEmpty)

        val del = ReplyParser.parse("```js\n// DELETE oldHelper\n// delete: other\nfunction a() {}\n```")
        assertEquals(listOf("oldHelper", "other"), del.deletes)
        assertEquals(listOf("a"), del.decls.map { it.name })
    }

    @Test
    fun `a whole page out of habit yields its game script`() {
        val page = """
            <!DOCTYPE html><html><head><title>x</title></head><body>
            <canvas id="game"></canvas>
            <script>// ===== gameforge engine v1 =====
            var W = 0; function rand() {}
            </script>
            <script>
            const CONFIG = { title: '뱀' };
            function update(dt) { move(); }
            function move() {}
            </script>
            <script>__gameforgeBoot();</script>
            </body></html>
        """.trimIndent()
        val reply = ReplyParser.parse(page)
        assertEquals(listOf("CONFIG", "update", "move"), reply.decls.map { it.name })
    }

    @Test
    fun `unterminated fence still counts`() {
        val reply = ReplyParser.parse("```js\nfunction draw() {\n  rect(0, 0, W, H, '#000');\n}")
        assertEquals(listOf("draw"), reply.decls.map { it.name })
    }
}
