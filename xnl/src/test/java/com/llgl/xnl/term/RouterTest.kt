package com.llgl.xnl.term

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterTest {
    @Test
    fun `scripted commands show canned lines and never reach the shell`() {
        for (c in Commands.SESSION + Commands.EGGS) {
            assertTrue("${c.text} should be scripted", c.scripted)
            val r = CommandRouter.route(c)
            assertTrue("${c.text} routed to $r", r is Route.Canned)
            assertTrue("${c.text} has no output", (r as Route.Canned).lines.isNotEmpty())
        }
    }

    @Test
    fun `the easter eggs are present and can never be executed`() {
        val texts = Commands.EGGS.map { it.text }
        assertTrue("the rm gag is missing", texts.any { it.contains("rm -rf") })
        assertTrue("the fork bomb is missing", texts.any { it.contains(":|:") })
        for (c in Commands.EGGS) {
            assertTrue("an egg must be scripted: ${c.text}", c.scripted)
            assertFalse("an egg must never be a real shell line: ${c.text}", CommandRouter.route(c) is Route.Shell)
        }
    }

    @Test
    fun `real shell commands route to the shell and the whole list is read-only`() {
        val destructive = listOf("rm ", "rmdir", " dd ", "dd if", "mkfs", "mkswap", "shred", "mv ", ":|:", ">>")
        for (c in Commands.SHELL) {
            assertTrue("${c.text} should run in the shell", CommandRouter.route(c) is Route.Shell)
            for (token in destructive) assertFalse("'$token' in ${c.text}", c.text.contains(token))
        }
    }

    @Test
    fun `built-ins route to the device info`() {
        for (c in Commands.BUILTIN) assertTrue(c.text, CommandRouter.route(c) is Route.Device)
    }

    @Test
    fun `with the shell off nothing is executed, with it on real commands and eggs ride along`() {
        assertTrue(Commands.list(shell = false).none { CommandRouter.route(it) is Route.Shell })
        assertTrue(Commands.list(shell = true).any { CommandRouter.route(it) is Route.Shell })
        assertTrue(Commands.list(shell = true).any { it in Commands.EGGS })
        assertTrue(Commands.list(shell = true).none { it.builtin })
    }
}
