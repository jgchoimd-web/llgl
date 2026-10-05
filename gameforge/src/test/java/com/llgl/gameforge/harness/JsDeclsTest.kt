package com.llgl.gameforge.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsDeclsTest {

    private val script = """
        // the game's settings
        const CONFIG = { title: '공 잡기', hint: '탭!' };
        let ball = { x: 0, y: 0, r: 28 };
        let misses = 0, flash = 0;

        function reset() {
          ball.x = W / 2; // middle
          const label = "a } b { c";
          const tpl = `size ${'$'}{ball.r > 10 ? '{big}' : '{small}'} done`;
          if (/\}/.test(label)) misses = 0;
        }

        /* multi-line
           comment with { braces */
        function update(dt) {
          ball.x += 10 * dt;
          if (ball.x > W) {
            gameOver();
          }
        }
        const helper = (a, b) => {
          return a + b;
        };
        class Enemy {
          constructor() { this.x = 0; }
          step() { this.x++; }
        }
        state.count = 5;
        for (let i = 0; i < 3; i++) {
          misses++;
        }
        function draw()
        {
          circle(ball.x, ball.y, ball.r, '#fff');
        }
    """.trimIndent()

    @Test
    fun `splits a script into named units`() {
        val decls = JsDecls.parse(script)
        val names = decls.map { it.name }
        assertEquals(listOf("CONFIG", "ball", "misses", "flash", "reset", "update", "helper", "Enemy", null, null, "draw"), names)
        assertEquals(
            listOf(
                Decl.Kind.VARIABLE, Decl.Kind.VARIABLE, Decl.Kind.VARIABLE, Decl.Kind.VARIABLE, Decl.Kind.FUNCTION, Decl.Kind.FUNCTION,
                Decl.Kind.VARIABLE, Decl.Kind.CLASS, Decl.Kind.CODE, Decl.Kind.CODE, Decl.Kind.FUNCTION,
            ),
            decls.map { it.kind },
        )
    }

    @Test
    fun `braces in strings, templates, regexes and comments do not end a function early`() {
        val decls = JsDecls.parse(script)
        val reset = JsDecls.find(decls, "reset")!!
        assertTrue(reset.source.contains("if (/\\}/.test(label)) misses = 0;"))
        assertTrue(reset.source.trimEnd().endsWith("}"))
        val update = JsDecls.find(decls, "update")!!
        assertTrue(update.source.startsWith("/* multi-line"))
        assertTrue(update.source.contains("gameOver();"))
        assertEquals("function update(dt) {", update.header)
    }

    @Test
    fun `comments above a declaration belong to it and line numbers are right`() {
        val decls = JsDecls.parse(script)
        val config = decls.first()
        assertTrue(config.source.startsWith("// the game's settings"))
        assertEquals(1, config.startLine)
        assertEquals(2, config.endLine)
        val draw = JsDecls.find(decls, "draw")!!
        assertEquals(script.lines().size, draw.endLine)
        assertTrue(draw.contains(draw.startLine + 1))
        assertEquals(4, draw.lineCount)
    }

    @Test
    fun `multi-name declarations are split so each name stands alone`() {
        val decls = JsDecls.parse(script)
        assertEquals("let misses = 0;", JsDecls.find(decls, "misses")!!.source)
        assertEquals("let flash = 0;", JsDecls.find(decls, "flash")!!.source)
        assertEquals(4, JsDecls.find(decls, "flash")!!.startLine)

        val tricky = JsDecls.parse(
            "// counters\nlet a = 1, b = { x: 1, y: 2 }, c = f(1, 2), d = 'x,y'; // trailing\nvar W = 0, H = 0\nconst { p, q } = obj, [r, s] = arr;",
        )
        assertEquals(listOf("a", "b", "c", "d", "W", "H", null, null), tricky.map { it.name })
        assertEquals("// counters\nlet a = 1;", tricky[0].source)
        assertEquals("let b = { x: 1, y: 2 };", tricky[1].source)
        assertEquals("let c = f(1, 2);", tricky[2].source)
        assertEquals("let d = 'x,y'; // trailing", tricky[3].source)
        assertEquals("var W = 0;", tricky[4].source)
        assertEquals("var H = 0;", tricky[5].source)
        assertTrue(tricky[6].source.startsWith("const { p, q } = obj"))
        assertEquals("[r, s] = arr;", tricky[7].source.removePrefix("const ").trim())
    }

    @Test
    fun `join round-trips the code and keeps orphan comments`() {
        val decls = JsDecls.parse(script)
        val joined = JsDecls.join(decls)
        val again = JsDecls.parse(joined)
        assertEquals(decls.map { it.name }, again.map { it.name })
        assertEquals(decls.map { it.source.trim() }, again.map { it.source.trim() })
        assertEquals("", JsDecls.join(emptyList()))

        val withHeader = JsDecls.parse("// 파일 설명\n// 둘째 줄\n\nfunction f() {}\n// 끝 주석\n")
        assertEquals(listOf(null, "f", null), withHeader.map { it.name })
        assertEquals("// 파일 설명\n// 둘째 줄", withHeader[0].source)
        assertTrue(JsDecls.join(withHeader).contains("// 끝 주석"))
    }

    @Test
    fun `odd shapes survive`() {
        assertTrue(JsDecls.parse("").isEmpty())
        assertEquals(1, JsDecls.parse("\n\n// only a comment\n").size)
        val unbalanced = JsDecls.parse("function a() {\n  if (x) {\n    y();\n")
        assertEquals(1, unbalanced.size)
        assertEquals("a", unbalanced[0].name)
        val destructured = JsDecls.parse("const { a, b } = obj;\nlet [p, q] = arr;")
        assertEquals(2, destructured.size)
        assertNull(destructured[0].name)
        assertEquals(Decl.Kind.VARIABLE, destructured[0].kind)
        val oneLiners = JsDecls.parse("const a = 1\nconst b = 2\nfunction f() { return 1; }\nconst c = () => 3")
        assertEquals(listOf("a", "b", "f", "c"), oneLiners.map { it.name })
        val stray = JsDecls.parse("}\nfunction g() {\n  return 2;\n}")
        assertEquals("g", stray.last().name)
    }
}
