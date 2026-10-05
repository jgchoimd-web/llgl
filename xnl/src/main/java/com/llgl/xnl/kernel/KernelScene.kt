package com.llgl.xnl.kernel

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One line of the kernel log; [age] in seconds since it appeared, for fading. */
class LogLine(val text: String, val warn: Boolean) {
    var age = 0f
}

/** An interrupt on its way from where it was raised to core [core]'s tile; [t] runs 0..1 over [seconds]. */
class Spark(val x0: Float, val y0: Float, val core: Int, var t: Float, val seconds: Float) {
    var x = x0
    var y = y0
}

/**
 * The inside of the XNL kernel, as a scene: a page map that fills and empties, cores whose load
 * breathes, interrupts that race to a core, a log that keeps talking, and a status line with the
 * phone's real uptime, memory, battery and host kernel. Pure Kotlin so the motion is unit-tested;
 * the renderer only draws what is here.
 */
class KernelScene(seed: Int = 11) {
    private val rng = Rng(seed)

    var width = 1080f
        private set
    var height = 2400f
        private set

    /** On the lock screen the clock owns the top; the dense parts keep below it. */
    var lockLayout = false

    /** Tilt in g, already smoothed, for the parallax. */
    var tiltX = 0f
    var tiltY = 0f

    /** Multiplies how often the log speaks; 1 is the designed pace. */
    var logSpeed = 1f
    var showWordmark = true
    var accent = 0

    // Facts the host fills in; the scene only displays them.
    var uptimeMs = 0L
    var memUsedMb = 0L
    var memTotalMb = 0L
    var battery = -1
    var hostKernel = ""

    var cores = 8
        set(value) {
            field = value.coerceIn(1, 16)
            coreLoad = FloatArray(field) { 0.25f }
            coreTarget = FloatArray(field) { rng.range(0.1f, 0.6f) }
            coreRetarget = FloatArray(field) { rng.range(0.5f, 2.5f) }
        }
    var coreLoad = FloatArray(8) { 0.25f }
        private set
    private var coreTarget = FloatArray(8) { 0.3f }
    private var coreRetarget = FloatArray(8) { 1f }

    val log = ArrayList<LogLine>()
    val sparks = ArrayList<Spark>()

    /** The page map: [pageCols] × [pageRows] cells of [cellPx]; 0 free, 1 allocated, 2 hot (just touched). */
    var pageCols = 0
        private set
    var pageRows = 0
        private set
    var pages = ByteArray(0)
        private set
    var heat = FloatArray(0)
        private set
    var cellPx = 24f
        private set
    var usedPages = 0
        private set

    /** The row the reclaim sweep is on, or -1 between sweeps. */
    var scanRow = -1
        private set
    var scans = 0
        private set

    /** Seconds since the scene started. */
    var time = 0f
        private set

    private var nextLogIn = 0.4f
    private var nextSparkIn = 1.5f
    private var nextScanIn = 20f
    private var scanPos = 0f
    private var scanReclaimed = 0
    private var irqCounter = 16
    private val tile = FloatArray(4)

    init {
        cores = 8
        resize(1080, 2400)
        boot()
    }

    // --- layout, as fractions of the screen ---

    val coresTop: Float get() = height * (if (lockLayout) 0.44f else 0.09f)
    val coreStripHeight: Float get() = height * 0.055f
    val wordmarkY: Float get() = height * (if (lockLayout) 0.60f else 0.38f)
    val logTop: Float get() = height * 0.70f
    val logBottom: Float get() = height * 0.915f
    val statusY: Float get() = height * 0.95f

    /** Left, top, right, bottom of core [i]'s tile into [out]. */
    fun coreTile(i: Int, out: FloatArray) {
        val margin = width * 0.05f
        val gap = width * 0.012f
        val w = (width - 2f * margin - gap * (cores - 1)) / cores
        out[0] = margin + i * (w + gap)
        out[1] = coresTop
        out[2] = out[0] + w
        out[3] = coresTop + coreStripHeight
    }

    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        width = w.toFloat()
        height = h.toFloat()
        cellPx = width / 45f
        pageCols = 45
        pageRows = max(1, (height / cellPx).toInt())
        pages = ByteArray(pageCols * pageRows)
        heat = FloatArray(pages.size)
        usedPages = 0
        // Start with a plausible occupancy so the first frame is not empty.
        val want = (pages.size * 0.3f).toInt()
        var guard = 0
        while (usedPages < want && guard++ < 10_000) allocate(rng.nextInt(pages.size), 4 + rng.nextInt(30), hot = false)
        sparks.clear()
        scanRow = -1
    }

    fun step(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        time += dt

        for (i in 0 until cores) {
            coreRetarget[i] -= dt
            if (coreRetarget[i] <= 0f) {
                coreTarget[i] = rng.range(0.05f, 0.85f)
                coreRetarget[i] = rng.range(0.6f, 3f)
            }
            coreLoad[i] += (coreTarget[i] - coreLoad[i]) * min(1f, dt * 2.5f)
            coreLoad[i] = coreLoad[i].coerceIn(0f, 1f)
        }

        // Pages: allocation bursts when the map is emptier, frees when it is fuller, so it hovers around a third used.
        val used = usedPages.toFloat() / pages.size
        val allocRate = 6f * max(0.05f, 0.55f - used)
        if (rng.nextFloat() < allocRate * dt) allocate(rng.nextInt(pages.size), 4 + rng.nextInt(36), hot = true)
        val freeRate = 6f * max(0.05f, used - 0.15f)
        if (rng.nextFloat() < freeRate * dt) free(rng.nextInt(pages.size), 4 + rng.nextInt(30))
        for (k in heat.indices) {
            if (heat[k] > 0f) {
                heat[k] -= dt / 1.5f
                if (heat[k] <= 0f) {
                    heat[k] = 0f
                    if (pages[k] == HOT) pages[k] = USED
                }
            }
        }

        // The reclaim sweep: a row at a time, top to bottom, over three seconds.
        nextScanIn -= dt
        if (scanRow < 0 && nextScanIn <= 0f) {
            scanRow = 0
            scanPos = 0f
            scanReclaimed = reclaimRow(0)
            scans++
        } else if (scanRow >= 0) {
            scanPos += dt * pageRows / 3f
            val newRow = min(pageRows - 1, scanPos.toInt())
            while (scanRow < newRow) {
                scanRow++
                scanReclaimed += reclaimRow(scanRow)
            }
            if (scanPos >= pageRows) {
                scanRow = -1
                nextScanIn = rng.range(20f, 40f)
                push("xnl-mm: kswapd reclaimed $scanReclaimed pages, zone Normal ok", false)
            }
        }

        nextLogIn -= dt * logSpeed
        if (nextLogIn <= 0f) {
            val warn = rng.nextFloat() < 0.12f
            push(if (warn) warnLine() else infoLine(), warn)
            nextLogIn = rng.range(0.5f, 1.8f)
        }
        for (l in log) l.age += dt

        nextSparkIn -= dt
        if (nextSparkIn <= 0f) {
            val side = rng.nextInt(3)
            val x0 = if (side == 0) 0f else if (side == 1) width else rng.range(0f, width)
            val y0 = if (side == 2) height else rng.range(0f, height)
            sparks += Spark(x0, y0, rng.nextInt(cores), 0f, rng.range(0.6f, 1.1f))
            nextSparkIn = rng.range(2f, 5f)
        }
        var i = 0
        while (i < sparks.size) {
            val s = sparks[i]
            s.t += dt / s.seconds
            coreTile(s.core, tile)
            val tx = (tile[0] + tile[2]) / 2f
            val ty = (tile[1] + tile[3]) / 2f
            val e = s.t.coerceIn(0f, 1f)
            val ease = e * e * (3f - 2f * e)
            s.x = s.x0 + (tx - s.x0) * ease
            s.y = s.y0 + (ty - s.y0) * ease - sin(e * PI).toFloat() * height * 0.03f
            if (s.t >= 1f) {
                spike(s.core, 0.35f)
                val irq = irqCounter++
                push("xnl-irq: IRQ $irq (${DEVICES[irq % DEVICES.size]}) -> cpu${s.core}", false)
                sparks.removeAt(i)
            } else {
                i++
            }
        }
    }

    /** A touch: a core tile gets a boost; anywhere else raises an interrupt and touches the pages around the finger. */
    fun tap(px: Float, py: Float): Boolean {
        for (i in 0 until cores) {
            coreTile(i, tile)
            if (px >= tile[0] && px <= tile[2] && py >= tile[1] && py <= tile[3]) {
                spike(i, 0.5f)
                push("xnl-sched: cpu$i boosted by user, slice 4000 us", false)
                return true
            }
        }
        val col = (px / cellPx).toInt().coerceIn(0, pageCols - 1)
        val row = (py / cellPx).toInt().coerceIn(0, pageRows - 1)
        var touched = 0
        for (dr in -2..2) for (dc in -2..2) {
            val rr = row + dr
            val cc = col + dc
            if (rr in 0 until pageRows && cc in 0 until pageCols) {
                val k = rr * pageCols + cc
                if (pages[k] == FREE) usedPages++
                pages[k] = HOT
                heat[k] = 1f
                touched++
            }
        }
        val core = rng.nextInt(cores)
        sparks += Spark(px, py, core, 0f, 0.7f)
        push("xnl-irq: user interrupt at ($col,$row), $touched pages touched -> cpu$core", false)
        return true
    }

    private fun spike(core: Int, amount: Float) {
        coreLoad[core] = min(1f, coreLoad[core] + amount)
        coreTarget[core] = min(1f, coreTarget[core] + amount * 0.5f)
    }

    /** Allocates up to [n] free cells from [start] onward (wrapping); returns how many it took. */
    private fun allocate(start: Int, n: Int, hot: Boolean): Int {
        var k = start
        var done = 0
        var scanned = 0
        while (done < n && scanned < pages.size) {
            if (pages[k] == FREE) {
                pages[k] = if (hot) HOT else USED
                if (hot) heat[k] = 1f
                usedPages++
                done++
            } else if (done > 0) {
                break
            }
            k = (k + 1) % pages.size
            scanned++
        }
        return done
    }

    private fun free(start: Int, n: Int): Int {
        var k = start
        var done = 0
        var scanned = 0
        while (done < n && scanned < pages.size) {
            if (pages[k] != FREE) {
                pages[k] = FREE
                heat[k] = 0f
                usedPages--
                done++
            } else if (done > 0) {
                break
            }
            k = (k + 1) % pages.size
            scanned++
        }
        return done
    }

    private fun reclaimRow(row: Int): Int {
        var n = 0
        for (c in 0 until pageCols) {
            val k = row * pageCols + c
            if (pages[k] == USED && rng.nextFloat() < 0.25f) {
                pages[k] = FREE
                heat[k] = 0f
                usedPages--
                n++
            }
        }
        return n
    }

    private fun boot() {
        val lines = listOf(
            "XNL 0.1.0-rc3 (build@xnl) #1 SMP PREEMPT",
            "Command line: console=ttyS0 root=/dev/vda1 ro xnl.abi=linux",
            "xnl-core: closed-source kernel, linux-compatible ABI",
            "xnl-sched: CFS-compatible scheduler online, $cores cpus",
            "xnl-mm: ${(pageCols * pageRows)} pages mapped, 2 zones",
            "xnl-abi: linux syscall table loaded (452 entries)",
            "xnl-vfs: mounted root (ext4) read-only",
            "xnl-net: eth0 link up, 1000 Mbps full duplex",
        )
        for ((i, l) in lines.withIndex()) push(l, false, uptimeMs + i * 137L)
    }

    private fun push(text: String, warn: Boolean, stampMs: Long = uptimeMs) {
        val s = stampMs / 1000
        val us = (stampMs % 1000) * 1000 + (rng.nextInt(1000))
        log += LogLine("[%6d.%06d] %s".format(s, us, text), warn)
        while (log.size > LOG_CAP) log.removeAt(0)
    }

    private fun infoLine(): String = when (rng.nextInt(12)) {
        0 -> "xnl-sched: migrated pid ${rng.nextInt(9000) + 100} (${PROCS.random()}) cpu${rng.nextInt(cores)} -> cpu${rng.nextInt(cores)}"
        1 -> "xnl-mm: zone Normal: ${(pages.size - usedPages)} free pages, watermark ok"
        2 -> "xnl-abi: syscall 0x${(rng.nextInt(300)).toString(16).padStart(2, '0')} (${SYSCALLS.random()}) via linux compat"
        3 -> "xnl-vfs: ${FS.random()}: ${rng.nextInt(120)} dirty inodes flushed"
        4 -> "xnl-net: ${NETS.random()}: rx ${rng.nextInt(9000)} pkts, tx ${rng.nextInt(9000)} pkts"
        5 -> "xnl-irq: IRQ ${rng.nextInt(200)} (${DEVICES.random()}) rate ${rng.nextInt(500)} Hz"
        6 -> "xnl-sec: module '${MODULES.random()}' integrity ok (closed source)"
        7 -> "xnl-power: cpu${rng.nextInt(cores)} freq ${800 + rng.nextInt(2200)} MHz"
        8 -> "xnl-sched: pid ${rng.nextInt(9000) + 100} (${PROCS.random()}) nice ${rng.nextInt(20) - 10}, slice ${1000 + rng.nextInt(5000)} us"
        9 -> "xnl-mm: compacted ${rng.nextInt(400)} pages in zone ${if (rng.nextFloat() < 0.5f) "Normal" else "DMA32"}"
        10 -> "xnl-fs: journal checkpoint ${rng.nextInt(99999)} ok"
        else -> "xnl-core: watchdog ping ok, uptime ${Uptime.format(uptimeMs)}"
    }

    private fun warnLine(): String = when (rng.nextInt(4)) {
        0 -> "xnl-abi: ptrace denied for pid ${rng.nextInt(9000) + 100} (policy: closed)"
        1 -> "xnl-thermal: zone ${rng.nextInt(4)} at ${48 + rng.nextInt(30)} C, throttling cpu${rng.nextInt(cores)}"
        2 -> "xnl-mm: order-${rng.nextInt(4)} allocation retried (zone Normal)"
        else -> "xnl-net: ${NETS.random()}: ${rng.nextInt(40)} dropped packets"
    }

    private fun <T> List<T>.random(): T = this[rng.nextInt(size)]

    companion object {
        const val FREE: Byte = 0
        const val USED: Byte = 1
        const val HOT: Byte = 2
        const val LOG_CAP = 40
        private val PROCS = listOf("compositor", "xnld", "shell", "logd", "init", "zygote-compat", "gpu-worker", "audio-hal", "netd", "vold")
        private val SYSCALLS = listOf("read", "write", "openat", "mmap", "futex", "clone3", "epoll_wait", "ioctl", "sendmsg", "nanosleep")
        private val FS = listOf("ext4", "f2fs", "tmpfs", "overlay")
        private val NETS = listOf("eth0", "wlan0", "rmnet0")
        private val DEVICES = listOf("gpu", "touch", "audio", "nvme", "usb", "modem", "display", "sensor-hub")
        private val MODULES = listOf("xnl_core", "xnl_sched", "xnl_mm", "xnl_abi", "xnl_vfs", "xnl_net")
    }
}

/** The phone's uptime as the status line shows it: `3d 04:12:33`, or `04:12:33` on the first day. */
object Uptime {
    fun format(ms: Long): String {
        val total = max(0L, ms / 1000)
        val days = total / 86400
        val h = (total % 86400) / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val hms = "%02d:%02d:%02d".format(h, m, s)
        return if (days > 0) "${days}d $hms" else hms
    }
}
