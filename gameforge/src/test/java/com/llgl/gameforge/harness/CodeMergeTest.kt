package com.llgl.gameforge.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeMergeTest {

    private val base = """
        const CONFIG = { title: '핑퐁', hint: '탭' };
        let speed = 100;

        function reset() {
          speed = 100;
        }

        function update(dt) {
          speed += dt;
        }

        function draw() {
          circle(10, 10, 5, '#fff');
        }
    """.trimIndent()

    private val reserved = setOf("rand", "clamp", "W", "H", "G", "gameOver")

    @Test
    fun `replaces by name, appends new units, deletes on request`() {
        val reply = ReplyParser.parse(
            """
            Here is the change:
            ```js
            function update(dt) {
              speed += dt * 2;
              spawn();
            }

            function spawn() {
              // new
            }
            // DELETE draw
            ```
            """.trimIndent(),
        )
        val result = CodeMerge.apply(base, reply, reserved)
        assertEquals(listOf("update"), result.replaced)
        assertEquals(listOf("spawn"), result.added)
        assertEquals(listOf("draw"), result.deleted)
        assertTrue(result.changed)
        assertEquals("update 수정 · spawn 추가 · draw 삭제", result.summary())
        val names = JsDecls.parse(result.code).map { it.name }
        assertEquals(listOf("CONFIG", "speed", "reset", "update", "spawn"), names)
        assertTrue(result.code.contains("speed += dt * 2;"))
        assertFalse(result.code.contains("speed += dt;\n"))
        assertFalse(result.code.contains("circle(10"))
        // Order of untouched units is preserved and nothing is duplicated.
        assertEquals(1, Regex("function reset").findAll(result.code).count())
    }

    @Test
    fun `a variable that shadows an engine name becomes an assignment`() {
        val reply = ReplyParser.parse("```js\nconst rand = (a, b) => a + Math.random() * (b - a);\nlet speed = 50;\n```")
        val result = CodeMerge.apply(base, reply, reserved)
        assertEquals(listOf("rand"), result.converted)
        assertTrue(result.code.contains("\nrand = (a, b) =>"))
        assertFalse(result.code.contains("const rand"))
        assertEquals(listOf("speed"), result.replaced)
        assertTrue(result.code.contains("let speed = 50;"))
    }

    @Test
    fun `loose code is appended once and deleting a missing name is reported`() {
        val reply = ReplyParser.parse("```js\nspeed = 5;\n// DELETE nothingHere\n```")
        val first = CodeMerge.apply(base, reply, reserved)
        assertEquals(listOf("(코드)"), first.added)
        assertEquals(listOf("nothingHere: 지울 선언이 없어요"), first.problems)
        val second = CodeMerge.apply(first.code, reply, reserved)
        assertTrue(second.added.isEmpty())
        assertEquals(1, Regex("^speed = 5;$", RegexOption.MULTILINE).findAll(second.code).count())
    }

    @Test
    fun `an empty reply changes nothing`() {
        val result = CodeMerge.apply(base, ReplyParser.parse("I would change the speed."), reserved)
        assertFalse(result.changed)
        assertEquals(JsDecls.join(JsDecls.parse(base)), result.code)
    }
}
