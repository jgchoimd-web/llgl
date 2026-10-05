package com.llgl.xnl.shell

import com.llgl.xnl.term.CommandResult
import com.llgl.xnl.term.Format
import java.io.IOException

/**
 * Runs one line in the phone's own shell, /system/bin/sh, as this app's user, and returns exactly
 * what it printed (stdout and stderr together). A command that does not finish in time is killed.
 * A non-zero exit or a permission/missing-file message means the command does not work on this
 * phone and the playbook drops it.
 */
class ShellRunner {
    fun run(command: String, timeoutMs: Long = 4000L): CommandResult {
        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            return CommandResult(listOf("sh: ${e.message}"), false)
        }
        val killer = Thread {
            try {
                Thread.sleep(timeoutMs)
            } catch (_: InterruptedException) {
                return@Thread
            }
            process.destroy()
        }.apply {
            isDaemon = true
            start()
        }
        val lines = ArrayList<String>()
        var truncated = 0
        try {
            process.inputStream.bufferedReader().useLines { seq ->
                for (raw in seq) {
                    if (lines.size < MAX_LINES) lines.add(Format.clean(raw)) else truncated++
                }
            }
        } catch (_: IOException) {
            // The process was killed or closed its output; what we have is what we show.
        }
        val exit = try {
            process.waitFor()
        } catch (_: InterruptedException) {
            -1
        }
        killer.interrupt()
        if (truncated > 0) lines.add("... ($truncated more lines)")
        val denied = lines.any { DENIED.containsMatchIn(it) }
        return CommandResult(lines, exit == 0 && !denied)
    }

    private companion object {
        const val MAX_LINES = 120
        val DENIED = Regex("Permission denied|No such file or directory|Operation not permitted|not found|inaccessible or not found")
    }
}
