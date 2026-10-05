package com.llgl.xnl

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.llgl.xnl.info.DeviceInfo
import com.llgl.xnl.render.TerminalRenderer
import com.llgl.xnl.shell.ShellRunner
import com.llgl.xnl.term.Command
import com.llgl.xnl.term.CommandResult
import com.llgl.xnl.term.Commands
import com.llgl.xnl.term.Format
import com.llgl.xnl.term.Playbook
import com.llgl.xnl.term.Rng
import com.llgl.xnl.term.Terminal
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * One running terminal: the pure [Terminal], a worker that runs its requests for real (the shell
 * or the `xnl` built-ins), the renderer, and the settings. Shared by the wallpaper engine and the
 * in-app preview. Everything but the worker happens on the main thread.
 */
class TermSession(context: Context, private val prefs: Prefs) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val app = context.applicationContext
    private val renderer = TerminalRenderer()
    private val info = DeviceInfo(app)
    private val shell = ShellRunner()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "xnl-shell").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.elapsedRealtime()
    private var generation = 0
    private var terminal = newTerminal()
    private var lastNanos = 0L
    private var frames = 0L
    private var fpsFrames = 0
    private var fpsTime = 0f
    private var fps = 0f

    var width = 0
        private set
    var height = 0
        private set

    var lockFade: Boolean
        get() = renderer.lockFade
        set(value) {
            renderer.lockFade = value
        }

    val animating: Boolean get() = terminal.animating

    init {
        info.wallpaperInfo = { wallpaperInfo() }
        renderer.accent = prefs.accent
        prefs.register(this)
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
        layout()
    }

    /** The frame clock restarts, so a long pause does not arrive as one huge step. */
    fun pause() {
        lastNanos = 0L
    }

    fun tap() = terminal.tap()

    fun frame(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.033f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now
        terminal.step(dt)
        terminal.takeRequest()?.let { dispatch(it, generation) }
        if (width > 0 && height > 0) renderer.draw(canvas, terminal, width, height)
        frames++
        fpsFrames++
        fpsTime += dt
        if (fpsTime >= 1f) {
            fps = fpsFrames / fpsTime
            fpsFrames = 0
            fpsTime = 0f
        }
    }

    fun close() {
        prefs.unregister(this)
        worker.shutdownNow()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_ACCENT -> renderer.accent = prefs.accent
            Prefs.KEY_SPEED -> terminal.speed = prefs.speed
            Prefs.KEY_COLUMNS -> layout()
            Prefs.KEY_SHELL -> restart()
            Prefs.KEY_BROKEN -> if (prefs.broken.isEmpty()) restart()
        }
    }

    private fun newTerminal(): Terminal {
        val seed = (System.nanoTime() and 0x7FFFFFFF).toInt()
        val playbook = Playbook(Rng(seed), Commands.list(prefs.shell), prefs.broken)
        playbook.onBroken = { prefs.markBroken(it.text) }
        return Terminal(seed, playbook, info.prompt()).also {
            it.speed = prefs.speed
            it.start(info.banner())
        }
    }

    private fun restart() {
        generation++
        terminal = newTerminal()
        layout()
    }

    private fun layout() {
        if (width <= 0 || height <= 0) return
        val m = renderer.layout(width, height, prefs.columns)
        terminal.resize(m.columns, m.rows)
    }

    private fun dispatch(command: Command, gen: Int) {
        worker.execute {
            val result = try {
                if (command.builtin) info.run(command.text) else shell.run(command.text)
            } catch (e: Exception) {
                CommandResult(listOf("${command.text}: ${e.javaClass.simpleName}: ${e.message}"), false)
            }
            main.post { if (gen == generation) terminal.deliver(result) }
        }
    }

    private fun wallpaperInfo(): List<String> = Format.table(
        listOf(
            "surface" to "${width}x$height px",
            "grid" to "${terminal.columns} cols x ${terminal.rows} rows",
            "frames" to "$frames (${String.format(Locale.US, "%.0f", fps)} fps)",
            "commands run" to "${terminal.commandsRun}",
            "scrollback" to "${terminal.lineCount} lines",
            "session up" to Format.duration(SystemClock.elapsedRealtime() - startedAt),
            "lock screen" to if (renderer.lockFade) "yes" else "no",
            "shell" to if (prefs.shell) "/system/bin/sh" else "off (xnl built-ins only)",
            "dropped" to "${prefs.broken.size} commands that failed here",
        ),
    )
}
