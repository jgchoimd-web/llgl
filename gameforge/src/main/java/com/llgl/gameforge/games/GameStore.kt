package com.llgl.gameforge.games

import com.llgl.gameforge.harness.Shell
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
    /** How many versions exist: 1 for a fresh game, +1 per revision, fix or manual edit. */
    val versions: Int,
    /** What the last change was ("새 게임", a request, "오류 수정 · update 수정", "직접 편집"). */
    val lastChange: String,
)

/**
 * Games on disk, one folder each: `shell.html` (the page with the engine and a placeholder),
 * `game.js` (the part the model and the person edit), `index.html` (the two assembled, what the
 * WebView runs), `meta.properties`, and up to [HISTORY] earlier scripts for undo. A game saved by
 * the old whole-file flow has only `index.html`; it is split on first use. Pure JVM, so unit-tested.
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

    /** The assembled page the WebView runs. */
    fun html(id: String): String? = File(dir(id), INDEX).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    /** The editable game script, splitting an old whole-file game on first use. */
    fun gameJs(id: String): String? {
        ensureSplit(id)
        return File(dir(id), GAME_JS).takeIf { it.exists() }?.readText(Charsets.UTF_8)
    }

    /** The page wrapper the script is pasted into. */
    fun shell(id: String): String {
        ensureSplit(id)
        return File(dir(id), SHELL).takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: Shell.template
    }

    /** HTML line number of the script's first line, for mapping browser errors to script lines. */
    fun gameLineOffset(id: String): Int = Shell.gameLineOffset(shell(id))

    fun create(title: String, prompt: String, shell: String, gameJs: String, model: String, tokens: Int, durationMs: Long): Game {
        val id = newId()
        val now = System.currentTimeMillis()
        val game = Game(id, title, prompt, now, now, model, tokens, durationMs, 1, "새 게임")
        dir(id).mkdirs()
        File(dir(id), SHELL).writeText(shell, Charsets.UTF_8)
        File(dir(id), GAME_JS).writeText(gameJs, Charsets.UTF_8)
        File(dir(id), INDEX).writeText(Shell.assemble(shell, gameJs, title), Charsets.UTF_8)
        write(game)
        return game
    }

    /** Replaces the game script, keeping the previous one in `history/` for [revert], and rebuilds the page. */
    fun update(id: String, gameJs: String, model: String, tokens: Int, durationMs: Long, change: String, title: String? = null): Game {
        val old = meta(id) ?: throw IllegalArgumentException("no game $id")
        ensureSplit(id)
        val script = File(dir(id), GAME_JS)
        if (script.exists()) {
            val history = File(dir(id), HISTORY_DIR).apply { mkdirs() }
            script.renameTo(File(history, "${old.updatedAt}.js"))
            trimHistory(history)
        }
        val game = old.copy(
            title = title ?: old.title,
            updatedAt = System.currentTimeMillis(),
            model = model,
            tokens = tokens,
            durationMs = durationMs,
            versions = old.versions + 1,
            lastChange = change,
        )
        script.writeText(gameJs, Charsets.UTF_8)
        File(dir(id), INDEX).writeText(Shell.assemble(shell(id), gameJs, game.title), Charsets.UTF_8)
        write(game)
        return game
    }

    /** Puts the newest history version back; false if there is none. */
    fun revert(id: String): Boolean {
        val old = meta(id) ?: return false
        val history = File(dir(id), HISTORY_DIR)
        val newest = history.listFiles()?.filter { it.isFile && it.name.endsWith(".js") }
            ?.maxByOrNull { it.name.removeSuffix(".js").toLongOrNull() ?: 0L } ?: return false
        val script = File(dir(id), GAME_JS)
        script.delete()
        if (!newest.renameTo(script)) return false
        val game = old.copy(updatedAt = System.currentTimeMillis(), versions = (old.versions - 1).coerceAtLeast(1), lastChange = "이전 버전으로 되돌림")
        File(dir(id), INDEX).writeText(Shell.assemble(shell(id), script.readText(Charsets.UTF_8), game.title), Charsets.UTF_8)
        write(game)
        return true
    }

    fun hasHistory(id: String): Boolean = File(dir(id), HISTORY_DIR).listFiles()?.any { it.isFile && it.name.endsWith(".js") } == true

    fun rename(id: String, title: String) {
        val game = meta(id) ?: return
        write(game.copy(title = title))
        gameJs(id)?.let { File(dir(id), INDEX).writeText(Shell.assemble(shell(id), it, title), Charsets.UTF_8) }
    }

    fun delete(id: String): Boolean = dir(id).deleteRecursively()

    fun dir(id: String): File = File(root, id)

    /** Old format (index.html only): carve the script out so it can be edited like any other game. */
    private fun ensureSplit(id: String) {
        val folder = dir(id)
        val script = File(folder, GAME_JS)
        val index = File(folder, INDEX)
        if (script.exists() || !index.exists()) return
        val (shell, js) = Shell.splitLegacy(index.readText(Charsets.UTF_8)) ?: return
        File(folder, SHELL).writeText(shell, Charsets.UTF_8)
        script.writeText(js, Charsets.UTF_8)
    }

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
        val files = history.listFiles()?.filter { it.isFile }?.sortedByDescending { it.name.substringBefore('.').toLongOrNull() ?: 0L }
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
        const val GAME_JS = "game.js"
        const val SHELL = "shell.html"
        const val META = "meta.properties"
        const val HISTORY_DIR = "history"
        const val HISTORY = 5
    }
}
