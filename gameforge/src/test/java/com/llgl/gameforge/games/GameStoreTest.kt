package com.llgl.gameforge.games

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

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `create, list, read, rename and delete`() {
        val a = store.create("벽돌 깨기", "벽돌 깨기 게임\n두 줄짜리 프롬프트", "<html>a</html>", "Gemma 3n E2B", 1200, 90_000)
        Thread.sleep(2)
        val b = store.create("뱀", "뱀 게임", "<html>b</html>", "Gemma 3 1B", 800, 40_000)
        val listed = store.list()
        assertEquals(listOf(b.id, a.id), listed.map { it.id })
        assertEquals("벽돌 깨기 게임\n두 줄짜리 프롬프트", store.meta(a.id)!!.prompt)
        assertEquals("<html>a</html>", store.html(a.id))
        assertEquals(1, a.versions)
        assertEquals("새 게임", a.lastChange)

        store.rename(a.id, "벽돌 깨기 2")
        assertEquals("벽돌 깨기 2", store.meta(a.id)!!.title)

        assertTrue(store.delete(a.id))
        assertNull(store.meta(a.id))
        assertNull(store.html(a.id))
        assertEquals(listOf(b.id), store.list().map { it.id })
    }

    @Test
    fun `update keeps history and revert restores it`() {
        val g = store.create("핑퐁", "핑퐁", "<html>v1</html>", "m", 1, 1)
        assertFalse(store.hasHistory(g.id))
        Thread.sleep(2)
        val v2 = store.update(g.id, "<html>v2</html>", "m", 2, 2, "공을 빠르게")
        assertEquals(2, v2.versions)
        assertEquals("공을 빠르게", v2.lastChange)
        assertEquals("<html>v2</html>", store.html(g.id))
        assertTrue(store.hasHistory(g.id))
        assertTrue(v2.updatedAt >= g.updatedAt)

        assertTrue(store.revert(g.id))
        assertEquals("<html>v1</html>", store.html(g.id))
        assertEquals(1, store.meta(g.id)!!.versions)
        assertFalse(store.hasHistory(g.id))
        assertFalse(store.revert(g.id))
    }

    @Test
    fun `history is capped`() {
        val g = store.create("x", "x", "<html>0</html>", "m", 0, 0)
        for (i in 1..(GameStore.HISTORY + 3)) {
            Thread.sleep(2)
            store.update(g.id, "<html>$i</html>", "m", i, i.toLong(), "v$i")
        }
        val history = File(store.dir(g.id), GameStore.HISTORY_DIR).listFiles()!!.filter { it.isFile }
        assertEquals(GameStore.HISTORY, history.size)
        assertEquals(GameStore.HISTORY + 4, store.meta(g.id)!!.versions)
    }

    @Test
    fun `a broken folder is skipped, not fatal`() {
        File(root, "junk").mkdirs()
        File(root, "stray.txt").writeText("x")
        assertTrue(store.list().isEmpty())
        assertNull(store.meta("junk"))
    }
}
