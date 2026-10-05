package com.llgl.xnl.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybookTest {
    private fun cmd(text: String, weight: Float = 1f, period: Float = 0f) = Command(text, text.startsWith("xnl"), weight, period)

    @Test
    fun `the session opens in order, then nothing follows itself`() {
        val pb = Playbook(Rng(3), Commands.list(shell = true))
        assertEquals("uname -a", pb.next(0f)!!.text)
        assertEquals("neofetch", pb.next(1f)!!.text)
        assertEquals("uptime", pb.next(2f)!!.text)
        var last = "uptime"
        for (i in 0 until 300) {
            val c = pb.next(3f + i * 2f)!!
            assertNotEquals(last, c.text)
            last = c.text
        }
    }

    @Test
    fun `broken commands are left out and dropping one tells the host`() {
        val pb = Playbook(Rng(1), Commands.list(shell = true), broken = setOf("uptime", "ps -A"))
        assertTrue(pb.commands.none { it.text == "uptime" || it.text == "ps -A" })
        for (i in 0 until 400) assertNotEquals("uptime", pb.next(i * 1f)!!.text)
        var told: Command? = null
        pb.onBroken = { told = it }
        val victim = pb.commands.first { it.text == "free -m" }
        pb.markBroken(victim)
        assertEquals(victim, told)
        assertTrue(pb.commands.none { it.text == "free -m" })
        for (i in 0 until 400) assertNotEquals("free -m", pb.next(1000f + i)!!.text)
    }

    @Test
    fun `a command waits out its period while something else is due`() {
        val pb = Playbook(Rng(7), listOf(cmd("slow", period = 100f), cmd("a"), cmd("b")), opening = emptyList())
        val slowTimes = ArrayList<Float>()
        for (t in 0 until 250) {
            if (pb.next(t.toFloat())!!.text == "slow") slowTimes += t.toFloat()
        }
        assertTrue("slow ran at $slowTimes", slowTimes.isNotEmpty())
        for (i in 1 until slowTimes.size) assertTrue("slow ran at $slowTimes", slowTimes[i] - slowTimes[i - 1] >= 100f)
    }

    @Test
    fun `an empty pool yields nothing and every built-in has help`() {
        assertNull(Playbook(Rng(1), emptyList()).next(0f))
        val help = Commands.HELP.map { it.first }.toSet()
        for (c in Commands.BUILTIN) assertTrue(c.text, c.text in help)
        assertNotNull(Commands.list(shell = false).firstOrNull { it.text == "xnl battery" })
        assertTrue(Commands.list(shell = false).none { CommandRouter.route(it) is Route.Shell })
    }
}
