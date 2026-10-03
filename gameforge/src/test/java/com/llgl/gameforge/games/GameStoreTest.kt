package com.llgl.gameforge.games

import com.llgl.gameforge.harness.Shell
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameStoreTest {
    private val root: File = Files.createTempDirectory("games").toFile()
    private val store = GameStore(root)
    private val shell = Shell.template

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `create assembles the page, then list, read, rename and delete`() {
        val a = store.create("벽돌 깨기", "벽돌 깨기 게임\n두 줄짜리 프롬프트", shell, "function update(dt) {}\n", "Gemma 3n E2B", 1200, 90_000)
        Thread.sleep(2)
        val b = store.create("뱀", "뱀 게임", shell, "function draw() {}\n", "Gemma 3 1B", 800, 40_000)
        assertEquals(listOf(b.id, a.id), store.list().map { it.id })
        assertEquals("벽돌 깨기 게임\n두 줄짜리 프롬프트", store.meta(a.id)!!.prompt)
        assertEquals("function update(dt) {}\n", store.gameJs(a.id))
        val html = store.html(a.id)!!
        assertTrue(html.contains("<title>벽돌 깨기</title>"))
        assertTrue(html.contains("function update(dt) {}"))
        assertTrue(html.contains("gameforge engine"))
        assertFalse(html.contains(Shell.PLACEHOLDER))
        assertEquals(Shell.gameLineOffset(shell), store.gameLineOffset(a.id))

        store.rename(a.id, "벽돌 깨기 2")
        assertEquals("벽돌 깨기 2", store.meta(a.id)!!.title)
        assertTrue(store.html(a.id)!!.contains("<title>벽돌 깨기 2</title>"))

        assertTrue(store.delete(a.id))
        assertNull(store.meta(a.id))
        assertNull(store.html(a.id))
        assertEquals(listOf(b.id), store.list().map { it.id })
    }

    @Test
    fun `update keeps history, rebuilds the page and revert restores it`() {
        val g = store.create("핑퐁", "핑퐁", shell, "let v = 1;\n", "m", 1, 1)
        assertFalse(store.hasHistory(g.id))
        Thread.sleep(2)
        val v2 = store.update(g.id, "let v = 2;\n", "m", 2, 2, "공을 빠르게", title = "핑퐁 2")
        assertEquals(2, v2.versions)
        assertEquals("공을 빠르게", v2.lastChange)
        assertEquals("핑퐁 2", v2.title)
        assertEquals("let v = 2;\n", store.gameJs(g.id))
        assertTrue(store.html(g.id)!!.contains("let v = 2;"))
        assertTrue(store.html(g.id)!!.contains("<title>핑퐁 2</title>"))
        assertTrue(store.hasHistory(g.id))

        assertTrue(store.revert(g.id))
        assertEquals("let v = 1;\n", store.gameJs(g.id))
        assertTrue(store.html(g.id)!!.contains("let v = 1;"))
        assertEquals(1, store.meta(g.id)!!.versions)
        assertFalse(store.hasHistory(g.id))
        assertFalse(store.revert(g.id))
    }

    @Test
    fun `history is capped`() {
        val g = store.create("x", "x", shell, "// 0\n", "m", 0, 0)
        for (i in 1..(GameStore.HISTORY + 3)) {
            Thread.sleep(2)
            store.update(g.id, "// $i\n", "m", i, i.toLong(), "v$i")
        }
        val history = File(store.dir(g.id), GameStore.HISTORY_DIR).listFiles()!!.filter { it.isFile }
        assertEquals(GameStore.HISTORY, history.size)
        assertEquals(GameStore.HISTORY + 4, store.meta(g.id)!!.versions)
    }

    @Test
    fun `an old whole-file game is split on first use and then edits like any other`() {
        val id = "legacy1"
        val folder = File(root, id).apply { mkdirs() }
        File(folder, GameStore.INDEX).writeText(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body><canvas></canvas>\n<script>\nlet s = 1;\nfunction loop() {}\n</script>\n</body></html>",
        )
        File(folder, GameStore.META).writeText("title=옛 게임\nversions=1\nupdatedAt=5\ncreatedAt=5\n")
        assertEquals("let s = 1;\nfunction loop() {}", store.gameJs(id))
        assertTrue(File(folder, GameStore.SHELL).exists())
        val updated = store.update(id, "let s = 2;\nfunction loop() {}\n", "m", 1, 1, "직접 편집")
        assertEquals(2, updated.versions)
        val html = store.html(id)!!
        assertTrue(html.contains("let s = 2;"))
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertFalse(html.contains(Shell.PLACEHOLDER))
    }

    @Test
    fun `a broken folder is skipped, not fatal`() {
        File(root, "junk").mkdirs()
        File(root, "stray.txt").writeText("x")
        assertTrue(store.list().isEmpty())
        assertNull(store.meta("junk"))
        assertNull(store.gameJs("junk"))
    }
}
