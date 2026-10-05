package com.llgl.xnl.term

/**
 * Where a command's output comes from. The router decides once and is the single place that keeps
 * scripted commands — the session flavour and, above all, the easter eggs — away from the real
 * shell: a command carrying a script is always [Route.Canned], shown as text, executed by nothing.
 */
sealed interface Route {
    /** Run this line in the phone's real shell. */
    data class Shell(val line: String) : Route

    /** Answer from the app's own device APIs (an `xnl` built-in). */
    data class Device(val command: String) : Route

    /** Show these canned lines; nothing runs. */
    data class Canned(val lines: List<String>) : Route
}

object CommandRouter {
    fun route(command: Command): Route = when {
        command.script != null -> Route.Canned(command.script)
        command.builtin -> Route.Device(command.text)
        else -> Route.Shell(command.text)
    }
}
