package com.llgl.gameforge.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlExtractorTest {

    private val game = """
        <!DOCTYPE html>
        <html lang="ko">
        <head>
        <meta charset="UTF-8">
        <title>  우주선   피하기 </title>
        <style>body{background:#111}</style>
        </head>
        <body>
        <canvas id="c"></canvas>
        <script>
        const c = document.getElementById('c');
        </script>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `strips chatter and markdown fences around the document`() {
        val raw = "Sure! Here is your game:\n```html\n$game\n```\nEnjoy playing!"
        val out = HtmlExtractor.extract(raw, "fallback")!!
        assertTrue(out.html.startsWith("<!DOCTYPE html>"))
        assertTrue(out.html.endsWith("</html>"))
        assertFalse(out.html.contains("```"))
        assertFalse(out.html.contains("Enjoy"))
        assertEquals("우주선 피하기", out.title)
        assertFalse(out.truncated)
    }

    @Test
    fun `accepts a bare document with text before and after`() {
        val raw = "Here you go.\n$game\nThat's it."
        val out = HtmlExtractor.extract(raw, "x")!!
        assertTrue(out.html.startsWith("<!DOCTYPE html>"))
        assertTrue(out.html.endsWith("</html>"))
        assertFalse(out.html.contains("That's it"))
    }

    @Test
    fun `closes a document that was cut off inside the script`() {
        val cut = game.substringBefore("</script>")
        val out = HtmlExtractor.extract(cut, "x")!!
        assertTrue(out.truncated)
        assertTrue(out.html.endsWith("</html>"))
        assertTrue(out.html.contains("</script>"))
        assertTrue(out.html.contains("</body>"))
        assertEquals(1, Regex("</html>").findAll(out.html).count())
    }

    @Test
    fun `wraps a fragment that has markup but no shell, and rejects prose`() {
        val fragment = "<canvas id=\"c\"></canvas>\n<script>let x = 1;</script>"
        val out = HtmlExtractor.extract(fragment, "조각")!!
        assertTrue(out.truncated)
        assertTrue(out.html.startsWith("<!DOCTYPE html>"))
        assertTrue(out.html.contains(fragment))
        assertEquals("조각", out.title)

        assertNull(HtmlExtractor.extract("I cannot make a game about that, sorry.", "x"))
        assertNull(HtmlExtractor.extract("", "x"))
    }

    @Test
    fun `title falls back to the idea and is clipped`() {
        val noTitle = game.replace(Regex("<title>.*?</title>"), "")
        assertEquals("벽돌 깨기", HtmlExtractor.titleOf(noTitle, "  벽돌   깨기 "))
        val long = "가".repeat(60)
        val clipped = HtmlExtractor.titleOf(noTitle, long)
        assertTrue(clipped.length <= 41)
        assertTrue(clipped.endsWith("…"))
        assertEquals("이름 없는 게임", HtmlExtractor.titleOf(noTitle, "   "))
    }

    @Test
    fun `prepare injects viewport and hook once and keeps an existing charset`() {
        val prepared = HtmlExtractor.prepare(game)
        assertEquals(1, Regex("name=\"viewport\"").findAll(prepared).count())
        assertEquals(1, Regex("unhandledrejection").findAll(prepared).count())
        assertEquals(1, Regex("charset", RegexOption.IGNORE_CASE).findAll(prepared).count())
        assertTrue(prepared.indexOf(HtmlExtractor.MARK) < prepared.indexOf("<title>"))
        assertEquals(prepared, HtmlExtractor.prepare(prepared))
    }

    @Test
    fun `prepare builds a head when the document lacks one`() {
        val headless = "<html><body><canvas></canvas><script>1</script></body></html>"
        val prepared = HtmlExtractor.prepare(headless)
        assertTrue(prepared.contains("<head>"))
        assertTrue(prepared.contains("name=\"viewport\""))
        assertTrue(prepared.contains("charset"))
        assertNotNull(HtmlExtractor.extract(prepared, "x"))

        val bare = "<canvas></canvas>"
        val wrapped = HtmlExtractor.prepare(bare)
        assertTrue(wrapped.startsWith("<!DOCTYPE html>"))
        assertTrue(wrapped.contains(bare))
    }
}
