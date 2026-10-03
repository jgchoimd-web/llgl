package com.llgl.gameforge.games

import java.io.File
import java.util.Properties
import kotlin.random.Random

data class Game(
    val id: String,
    val title: String,
    val prompt: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String,
    val tokens: Int,
    val durationMs: Long,
    /** How many versions exist: 1 for a fresh game, +1 per revision or fix. */
    val versions: Int,
    /** What the last change was ("새 게임", a revision request, "오류 수정"). */
    val lastChange: String,
)

/**
 * Games on disk: one folder per game holding `index.html`, a `meta.properties`, and up to
 * [HISTORY] earlier versions so a bad revision can be undone. Pure JVM, so it is unit-tested.
 */
class GameStore(private val root: File) {
    init {
        root.mkdirs()
    }

    fun list(): List<Game> = (root.listFiles() ?: emptyArray())
        .filter { it.isDirectory }
        .mapNotNull { meta(it.name) }
        .sortedByDescending { it.updatedAt }

    fun meta(id: String): Game? {
        val file = File(dir(id), META)
        if (!file.exists()) return null
        val p = Properties()
        try {
            file.reader(Charsets.UTF_8).use { p.load(it) }
        } catch (_: Exception) {
            return null
        }
        return Game(
            id = id,
            title = p.getProperty("title", "이름 없는 게임"),
            prompt = p.getProperty("prompt", ""),
            createdAt = p.getProperty("createdAt")?.toLongOrNull() ?: 0L,
            updatedAt = p.getProperty("updatedAt")?.toLongOrNull() ?: 0L,
            model = p.getProperty("model", ""),
            tokens = p.getProperty("tokens")?.toIntOrNull() ?: 0,
            durationMs = p.getProperty("durationMs")?.toLongOrNull() ?: 0L,
            versions = p.getProperty("versions")?.toIntOrNull() ?: 1,
            lastChange = p.getProperty("lastChange", "새 게임"),
        )
    }

    fun html(id: String): String? = File(dir(id), INDEX).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun create(title: String, prompt: String, html: String, model: String, tokens: Int, durationMs: Long): Game {
        val id = newId()
        val now = System.currentTimeMillis()
        val game = Game(id, title, prompt, now, now, model, tokens, durationMs, 1, "새 게임")
        dir(id).mkdirs()
        File(dir(id), INDEX).writeText(html, Charsets.UTF_8)
        write(game)
        return game
    }

    /** Replaces the game's HTML, keeping the previous version in `history/` for [revert]. */
    fun update(id: String, html: String, model: String, tokens: Int, durationMs: Long, change: String, title: String? = null): Game {
        val old = meta(id) ?: throw IllegalArgumentException("no game $id")
        val index = File(dir(id), INDEX)
        if (index.exists()) {
            val history = File(dir(id), HISTORY_DIR).apply { mkdirs() }
            index.renameTo(File(history, "${old.updatedAt}.html"))
            trimHistory(history)
        }
        index.writeText(html, Charsets.UTF_8)
        val game = old.copy(
            title = title ?: old.title,
            updatedAt = System.currentTimeMillis(),
            model = model,
            tokens = tokens,
            durationMs = durationMs,
            versions = old.versions + 1,
            lastChange = change,
        )
        write(game)
        return game
    }

    /** Puts the newest history version back; false if there is none. */
    fun revert(id: String): Boolean {
        val old = meta(id) ?: return false
        val history = File(dir(id), HISTORY_DIR)
        val newest = history.listFiles()?.filter { it.isFile }?.maxByOrNull { it.name.removeSuffix(".html").toLongOrNull() ?: 0L }
            ?: return false
        val index = File(dir(id), INDEX)
        index.delete()
        if (!newest.renameTo(index)) return false
        write(old.copy(updatedAt = System.currentTimeMillis(), versions = (old.versions - 1).coerceAtLeast(1), lastChange = "이전 버전으로 되돌림"))
        return true
    }

    fun hasHistory(id: String): Boolean = File(dir(id), HISTORY_DIR).listFiles()?.any { it.isFile } == true

    fun rename(id: String, title: String) {
        val game = meta(id) ?: return
        write(game.copy(title = title))
    }

    fun delete(id: String): Boolean = dir(id).deleteRecursively()

    fun dir(id: String): File = File(root, id)

    private fun write(game: Game) {
        val p = Properties()
        p.setProperty("title", game.title)
        p.setProperty("prompt", game.prompt)
        p.setProperty("createdAt", game.createdAt.toString())
        p.setProperty("updatedAt", game.updatedAt.toString())
        p.setProperty("model", game.model)
        p.setProperty("tokens", game.tokens.toString())
        p.setProperty("durationMs", game.durationMs.toString())
        p.setProperty("versions", game.versions.toString())
        p.setProperty("lastChange", game.lastChange)
        File(dir(game.id), META).writer(Charsets.UTF_8).use { p.store(it, null) }
    }

    private fun trimHistory(history: File) {
        val files = history.listFiles()?.filter { it.isFile }?.sortedByDescending { it.name.removeSuffix(".html").toLongOrNull() ?: 0L }
            ?: return
        files.drop(HISTORY).forEach { it.delete() }
    }

    private fun newId(): String {
        val time = System.currentTimeMillis().toString(36)
        val salt = Random.nextInt(0, 36 * 36).toString(36).padStart(2, '0')
        var id = "$time$salt"
        while (dir(id).exists()) id += Random.nextInt(0, 36).toString(36)
        return id
    }

    companion object {
        const val INDEX = "index.html"
        const val META = "meta.properties"
        const val HISTORY_DIR = "history"
        const val HISTORY = 5
    }
}
