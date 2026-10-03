package com.llgl.gameforge.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    private val decls = JsDecls.parse(
        """
        const CONFIG = { title: '우주선' };
        let ship = { x: 0, y: 0 };

        function reset() {
          ship.x = W / 2;
        }

        function update(dt) {
          moveShip(dt);
          spawnRocks(dt);
        }

        function moveShip(dt) {
          ship.x += dt;
          ship.x += dt;
          ship.x += dt;
        }

        function spawnRocks(dt) {
          rocks.push({ x: rand(0, W), y: -10 });
        }

        function draw() {
          circle(ship.x, ship.y, 10, '#fff');
        }
        """.trimIndent(),
    )

    @Test
    fun `everything is shown when it fits`() {
        val s = ContextBuilder.select(decls, budgetChars = 100_000)
        assertTrue(s.whole)
        assertEquals(decls.size, s.shown.size)
        assertEquals("", s.map())
    }

    @Test
    fun `under a budget the task decides what is shown and the rest is mapped`() {
        val total = decls.sumOf { it.source.length }
        val s = ContextBuilder.select(decls, budgetChars = total / 2, request = "make the spawnRocks slower")
        val shown = s.shown.map { it.name }
        assertTrue("state and config first: $shown", "CONFIG" in shown && "ship" in shown)
        assertTrue("the function named in the request: $shown", "spawnRocks" in shown)
        assertTrue(s.hidden.isNotEmpty())
        assertTrue(s.map().lines().all { it.startsWith("- ") && it.contains("줄)") })
        // Order of the original script is kept within the shown part.
        assertEquals(shown, decls.map { it.name }.filter { it in shown })
    }

    @Test
    fun `error locations always make the cut, and reads come first`() {
        val moveShip = JsDecls.find(decls, "moveShip")!!
        val tiny = ContextBuilder.select(decls, budgetChars = 10, errorLines = listOf(moveShip.startLine + 1))
        assertEquals(listOf("moveShip"), tiny.shown.map { it.name })

        val byName = ContextBuilder.select(decls, budgetChars = 10, errorNames = listOf("draw"))
        assertEquals(listOf("draw"), byName.shown.map { it.name })

        val read = ContextBuilder.select(decls, budgetChars = 60, reads = listOf("spawnRocks"))
        assertTrue(read.shown.any { it.name == "spawnRocks" })
    }

    @Test
    fun `error helpers parse lines and names`() {
        val errors = listOf("Uncaught TypeError: x is undefined (줄 120)", "[게임 오류] update(): rocks is not defined (줄 7)")
        assertEquals(listOf(101), ContextBuilder.errorLines(errors.take(1)) { it - 19 })
        assertEquals(listOf("update"), ContextBuilder.errorNames(errors, decls))
    }
}
