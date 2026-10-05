package com.llgl.xnl.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalTest {
    private val prompt = "u0_a123@xnl:/ $ "

    private fun cmd(text: String) = Command(text, false, 1f, 0f)

    private fun terminal(commands: List<Command>, columns: Int = 40, rows: Int = 12, seed: Int = 1): Terminal {
        val pb = Playbook(Rng(seed), commands, opening = commands.take(1).map { it.text })
        return Terminal(seed, pb, prompt).apply {
            resize(columns, rows)
            start(listOf("banner one", "banner two"))
        }
    }

    private fun run(t: Terminal, seconds: Float) {
        repeat((seconds * 60).toInt()) { t.step(1f / 60f) }
    }

    /** Steps until the terminal asks for a command, answers it, and runs until the next prompt. */
    private fun runCommand(t: Terminal, answer: (Command) -> CommandResult): Command {
        var req: Command? = null
        var guard = 0
        while (req == null && guard++ < 2000) {
            t.step(1f / 60f)
            req = t.takeRequest()
        }
        assertNotNull("no command requested", req)
        run(t, 3f)
        t.deliver(answer(req!!))
        guard = 0
        while (t.state != Terminal.State.IDLE && guard++ < 6000) t.step(1f / 60f)
        assertEquals(Terminal.State.IDLE, t.state)
        return req!!
    }

    private fun runCommand(t: Terminal, lines: List<String>, ok: Boolean = true): Command = runCommand(t) { CommandResult(lines, ok) }

    @Test
    fun `a session types the command, waits for the real result, prints it and shows a new prompt`() {
        val t = terminal(listOf(cmd("echo hi"), cmd("date")))
        assertEquals(3, t.lineCount)
        assertEquals(Kind.BANNER, t.lines[0].kind)
        assertEquals(prompt, t.lastLine!!.text)
        assertEquals(Kind.PROMPT, t.lastLine!!.kind)

        var req: Command? = null
        var steps = 0
        while (req == null && steps++ < 600) {
            t.step(1f / 60f)
            req = t.takeRequest()
        }
        assertEquals("echo hi", req!!.text)
        assertEquals(Terminal.State.TYPING, t.state)
        assertTrue(t.lastLine!!.text.startsWith(prompt))
        run(t, 1.5f)
        assertEquals(prompt + "echo hi", t.lastLine!!.text)
        assertEquals(Terminal.State.RUNNING, t.state)
        run(t, 1f)
        assertEquals("waits for the host", Terminal.State.RUNNING, t.state)

        t.deliver(CommandResult(listOf("hi", "there"), true))
        t.step(1f / 60f)
        assertEquals(Terminal.State.OUTPUT, t.state)
        run(t, 1f)
        assertEquals(Terminal.State.IDLE, t.state)
        assertEquals(6, t.lineCount)
        assertEquals("hi", t.lines[3].text)
        assertEquals(Kind.OUTPUT, t.lines[3].kind)
        assertEquals("there", t.lines[4].text)
        assertEquals(prompt, t.lines[5].text)
        assertEquals(1, t.commandsRun)
        assertTrue(t.cursorColumn == prompt.length)
    }

    @Test
    fun `long lines wrap at the column count and the rows add up`() {
        val t = terminal(listOf(cmd("x")), columns = 20, rows = 30)
        val long = "0123456789".repeat(5)
        runCommand(t, listOf(long, "short"))
        val line = t.lines.first { it.text == long }
        assertEquals(3, t.rowsOf(line))
        val rows = ArrayList<VisualRow>()
        val n = t.visible(rows)
        val pieces = (0 until n).map { rows[it] }.filter { it.line === line }
        assertEquals(3, pieces.size)
        assertTrue(pieces.all { it.length <= 20 })
        assertEquals(long, pieces.joinToString("") { it.text })
        assertEquals(t.lines.sumOf { t.rowsOf(it) }, t.totalRows)
        t.resize(10, 30)
        assertEquals(5, t.rowsOf(line))
        assertEquals(t.lines.sumOf { t.rowsOf(it) }, t.totalRows)
    }

    @Test
    fun `new rows beyond the viewport slide the content up and settle`() {
        val t = terminal(listOf(cmd("x")), columns = 40, rows = 6)
        var req: Command? = null
        var guard = 0
        while (req == null && guard++ < 2000) {
            t.step(1f / 60f)
            req = t.takeRequest()
        }
        run(t, 2f)
        t.deliver(CommandResult((1..10).map { "line $it" }, true))
        var slid = false
        guard = 0
        while (t.state != Terminal.State.IDLE && guard++ < 6000) {
            t.step(1f / 60f)
            if (t.slide > 0f) slid = true
        }
        assertTrue("content slid", slid)
        guard = 0
        while (t.slide > 0f && guard++ < 600) t.step(1f / 60f)
        assertEquals(0f, t.slide, 0.0001f)
        assertEquals("the pause before the next command is still on", Terminal.State.IDLE, t.state)
        assertEquals(14, t.totalRows)
        val rows = ArrayList<VisualRow>()
        val n = t.visible(rows)
        assertEquals(6 + Terminal.EXTRA_ROWS, n)
        assertEquals(prompt, rows[n - 1].text)
        assertEquals("line 6", rows[n - 2 - 4].text)
    }

    @Test
    fun `a failed or empty result drops the command for good`() {
        val good = cmd("good")
        val bad = cmd("bad")
        val empty = cmd("empty")
        val t = terminal(listOf(bad, good, empty), rows = 40)
        val dropped = ArrayList<String>()
        t.playbook.onBroken = { dropped += it.text }
        assertEquals("bad", runCommand(t, listOf("sh: bad: inaccessible or not found"), ok = false).text)
        assertEquals(listOf("bad"), dropped)
        assertEquals(Kind.ERROR, t.lines[t.lineCount - 2].kind)
        for (i in 0 until 30) {
            val c = runCommand(t) { if (it.text == "empty") CommandResult(emptyList(), true) else CommandResult(listOf("ok"), true) }
            assertTrue(c.text != "bad")
            if (i > 2) assertEquals("good", c.text)
        }
        assertEquals(listOf("bad", "empty"), dropped)
        assertTrue(t.playbook.commands.none { it.text == "bad" || it.text == "empty" })
        assertTrue(t.lines.none { it.text.isEmpty() && it.kind == Kind.OUTPUT })
    }

    @Test
    fun `a tap ends the pause and speed shortens the typing`() {
        val t = terminal(listOf(cmd("first command here"), cmd("second")))
        runCommand(t, listOf("out"))
        assertEquals(Terminal.State.IDLE, t.state)
        t.step(1f / 60f)
        assertEquals(Terminal.State.IDLE, t.state)
        t.tap()
        t.step(1f / 60f)
        assertEquals(Terminal.State.TYPING, t.state)
        assertNotNull(t.takeRequest())

        fun typingSteps(speed: Float): Int {
            val s = terminal(listOf(cmd("a command of some length to type")), seed = 9)
            s.speed = speed
            var guard = 0
            while (s.takeRequest() == null && guard++ < 2000) s.step(1f / 60f)
            var steps = 0
            while (s.state == Terminal.State.TYPING && steps++ < 2000) s.step(1f / 60f)
            return steps
        }
        val slow = typingSteps(1f)
        val fast = typingSteps(3f)
        assertTrue("$slow vs $fast", fast * 2 < slow)
    }

    @Test
    fun `the scrollback stays capped and consistent`() {
        val t = terminal(listOf(cmd("spam")), columns = 30, rows = 20)
        val chunk = (1..50).map { "row $it " + "x".repeat(it % 40) }
        for (i in 0 until 12) runCommand(t, chunk)
        assertTrue(t.lineCount <= Terminal.MAX_LINES)
        assertEquals(t.lines.sumOf { t.rowsOf(it) }, t.totalRows)
        assertEquals(12, t.commandsRun)
        val rows = ArrayList<VisualRow>()
        val n = t.visible(rows)
        assertEquals(20 + Terminal.EXTRA_ROWS, n)
        assertEquals(Kind.PROMPT, rows[n - 1].line.kind)
    }
}
