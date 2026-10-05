package com.llgl.xnl.term

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `durations read like a kernel`() {
        assertEquals("3d 04:12:33", Format.duration(3L * 86400_000L + 4L * 3600_000L + 12L * 60_000L + 33_000L))
        assertEquals("00:00:59", Format.duration(59_000L))
        assertEquals("00:00:00", Format.duration(-5L))
    }

    @Test
    fun `bytes pick a unit`() {
        assertEquals("0B", Format.bytes(0L))
        assertEquals("900B", Format.bytes(900L))
        assertEquals("1.5K", Format.bytes(1536L))
        assertEquals("512M", Format.bytes(512L * 1024L * 1024L))
        assertEquals("3.0G", Format.bytes(3L * 1024L * 1024L * 1024L))
        assertEquals("?", Format.bytes(-1L))
    }

    @Test
    fun `tables align, tabs expand and control characters go`() {
        val t = Format.table(listOf("a" to "1", "long key" to "2"))
        assertEquals("a".padEnd(8) + "  1", t[0])
        assertEquals("long key  2", t[1])
        assertEquals("a" + " ".repeat(7) + "b", Format.expandTabs("a\tb"))
        assertEquals("abcdefgh" + " ".repeat(8) + "x", Format.expandTabs("abcdefgh\tx"))
        assertEquals("Name:   com.llgl.xnl", Format.clean("Name:\tcom.llgl.xnl\r\n"))
        assertEquals("ab", Format.clean("a\u001b[0mb"))
        assertEquals(400, Format.clean("x".repeat(1000)).length)
    }
}
