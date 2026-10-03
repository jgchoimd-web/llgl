package com.llgl.sandbox.sim

import com.llgl.sandbox.sim.Elements.ACID
import com.llgl.sandbox.sim.Elements.BATTERY
import com.llgl.sandbox.sim.Elements.EMPTY
import com.llgl.sandbox.sim.Elements.FIRE
import com.llgl.sandbox.sim.Elements.GLASS
import com.llgl.sandbox.sim.Elements.GUNPOWDER
import com.llgl.sandbox.sim.Elements.HEATER
import com.llgl.sandbox.sim.Elements.KIND_GAS
import com.llgl.sandbox.sim.Elements.KIND_LIQUID
import com.llgl.sandbox.sim.Elements.KIND_POWDER
import com.llgl.sandbox.sim.Elements.KIND_SOLID
import com.llgl.sandbox.sim.Elements.LAMP
import com.llgl.sandbox.sim.Elements.LAVA
import com.llgl.sandbox.sim.Elements.LIGHTNING
import com.llgl.sandbox.sim.Elements.PLANT
import com.llgl.sandbox.sim.Elements.SAND
import com.llgl.sandbox.sim.Elements.SMOKE
import com.llgl.sandbox.sim.Elements.STEAM
import com.llgl.sandbox.sim.Elements.STONE
import com.llgl.sandbox.sim.Elements.SWITCH_OFF
import com.llgl.sandbox.sim.Elements.SWITCH_ON
import com.llgl.sandbox.sim.Elements.WALL
import com.llgl.sandbox.sim.Elements.WATER
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * A falling-sand world: one byte per cell for the element, one for its remaining life (fire,
 * smoke, steam, a lamp's glow) and one for electric charge. Each tick sweeps bottom-up, alternating
 * direction, so grains fall one cell per tick and never twice; then a separate pass moves electric
 * pulses one cell along every conductor. Pure Kotlin; the view paints into it and reads it back.
 */
class Grid(val width: Int, val height: Int, seed: Int = 0x1234567) {
    val size = width * height
    val cells = ByteArray(size)

    /** Unsigned: fire/smoke/steam ticks left, or a lamp's afterglow. */
    val life = ByteArray(size)

    /** Positive: charged for that many more ticks. Negative: cooling down, cannot recharge yet. */
    val charge = ByteArray(size)

    private val moved = ByteArray(size)
    private val pending = IntArray(size)
    private var pendingCount = 0
    private var rng = if (seed == 0) 0x1234567 else seed

    var tick = 0L
        private set

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    /** The element at (x, y); outside the grid everything is wall. */
    fun at(x: Int, y: Int): Byte = if (inBounds(x, y)) cells[y * width + x] else WALL

    fun count(element: Byte): Int {
        var n = 0
        for (i in 0 until size) if (cells[i] == element) n++
        return n
    }

    fun lifeOf(i: Int): Int = life[i].toInt() and 0xFF

    // ---------------------------------------------------------------------------------------
    // Painting
    // ---------------------------------------------------------------------------------------

    /** Pours or builds [element] in a disc. Solids replace what is there; everything else fills only empty cells. */
    fun paint(cx: Int, cy: Int, radius: Int, element: Byte) {
        val building = Elements.kind[element.toInt()] == KIND_SOLID
        disc(cx, cy, radius) { i -> if (building || cells[i] == EMPTY) set(i, element) }
    }

    fun erase(cx: Int, cy: Int, radius: Int) {
        disc(cx, cy, radius) { i -> set(i, EMPTY) }
    }

    /** Lightning: charges conductors under the brush and arcs through empty cells. */
    fun spark(cx: Int, cy: Int, radius: Int) {
        disc(cx, cy, radius) { i ->
            val e = cells[i]
            if (Elements.conducts[e.toInt()]) {
                charge[i] = SPARK_LIFE
            } else if (e == EMPTY) {
                set(i, LIGHTNING)
            }
        }
    }

    fun toggle(cx: Int, cy: Int, radius: Int) {
        disc(cx, cy, radius) { i ->
            when (cells[i]) {
                SWITCH_OFF -> cells[i] = SWITCH_ON
                SWITCH_ON -> {
                    cells[i] = SWITCH_OFF
                    charge[i] = 0
                }
            }
        }
    }

    fun clear() {
        cells.fill(EMPTY)
        life.fill(0)
        charge.fill(0)
    }

    private inline fun disc(cx: Int, cy: Int, radius: Int, action: (Int) -> Unit) {
        val r2 = radius * radius + radius
        for (dy in -radius..radius) {
            val y = cy + dy
            if (y < 0 || y >= height) continue
            for (dx in -radius..radius) {
                val x = cx + dx
                if (x < 0 || x >= width || dx * dx + dy * dy > r2) continue
                action(y * width + x)
            }
        }
    }

    private fun set(i: Int, e: Byte) {
        cells[i] = e
        charge[i] = 0
        life[i] = initialLife(e).toByte()
    }

    private fun initialLife(e: Byte): Int = when (e) {
        FIRE -> 30 + (next() and 15)
        SMOKE -> 90 + (next() and 63)
        STEAM -> 150 + (next() and 63)
        LIGHTNING -> 3
        else -> 0
    }

    // ---------------------------------------------------------------------------------------
    // Stepping
    // ---------------------------------------------------------------------------------------

    private fun next(): Int {
        var s = rng
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        rng = s
        return s
    }

    private fun chance(outOf256: Int): Boolean = (next() and 0xFF) < outOf256

    private fun coin(): Boolean = (next() and 1) == 0

    fun step() {
        moved.fill(0)
        val leftToRight = (tick and 1L) == 0L
        var y = height - 1
        while (y >= 0) {
            val row = y * width
            if (leftToRight) {
                var x = 0
                while (x < width) {
                    update(x, y, row + x)
                    x++
                }
            } else {
                var x = width - 1
                while (x >= 0) {
                    update(x, y, row + x)
                    x--
                }
            }
            y--
        }
        electricity()
        tick++
    }

    private fun update(x: Int, y: Int, i: Int) {
        val e = cells[i]
        if (e == EMPTY || moved[i].toInt() != 0) return
        when (Elements.kind[e.toInt()]) {
            KIND_POWDER -> powder(x, y, i, e)
            KIND_LIQUID -> liquid(x, y, i, e)
            KIND_GAS -> gas(x, y, i, e)
            KIND_SOLID -> solid(x, y, i, e)
        }
    }

    private fun swap(i: Int, j: Int) {
        val c = cells[i]
        cells[i] = cells[j]
        cells[j] = c
        val l = life[i]
        life[i] = life[j]
        life[j] = l
        val q = charge[i]
        charge[i] = charge[j]
        charge[j] = q
        moved[i] = 1
        moved[j] = 1
    }

    /** Can [e] move into a cell holding [t]: empty, or a lighter fluid that gets pushed aside. */
    private fun displaces(e: Byte, t: Byte): Boolean {
        if (t == EMPTY) return true
        return Elements.isFluid(t) && Elements.density[t.toInt()] < Elements.density[e.toInt()]
    }

    private fun powder(x: Int, y: Int, i: Int, e: Byte) {
        if (y + 1 >= height) return
        val below = i + width
        if (displaces(e, cells[below])) {
            swap(i, below)
            return
        }
        val dir = if (coin()) 1 else -1
        var nx = x + dir
        if (nx in 0 until width && displaces(e, cells[below + dir])) {
            swap(i, below + dir)
            return
        }
        nx = x - dir
        if (nx in 0 until width && displaces(e, cells[below - dir])) swap(i, below - dir)
    }

    private fun liquid(x: Int, y: Int, i: Int, e: Byte) {
        when (e) {
            LAVA -> {
                lavaReact(x, y, i)
                if (cells[i] != LAVA || !chance(110)) return
            }
            ACID -> {
                acidReact(x, y, i)
                if (cells[i] != ACID) return
            }
        }
        if (y + 1 < height) {
            val below = i + width
            if (displaces(e, cells[below])) {
                swap(i, below)
                return
            }
            val dir = if (coin()) 1 else -1
            if (x + dir in 0 until width && displaces(e, cells[below + dir])) {
                swap(i, below + dir)
                return
            }
            if (x - dir in 0 until width && displaces(e, cells[below - dir])) {
                swap(i, below - dir)
                return
            }
        }
        // Sideways: slide to the farthest empty cell within reach, or push aside a lighter fluid.
        val dir = if (coin()) 1 else -1
        var reach = Elements.dispersion[e.toInt()]
        var tx = x
        var target = -1
        while (reach > 0) {
            val nx = tx + dir
            if (nx < 0 || nx >= width) break
            val j = i + (nx - x)
            val t = cells[j]
            if (t == EMPTY) {
                target = j
                tx = nx
            } else if (Elements.isFluid(t) && Elements.density[t.toInt()] < Elements.density[e.toInt()] && Elements.kind[t.toInt()] == KIND_LIQUID) {
                target = j
                break
            } else {
                break
            }
            reach--
        }
        if (target >= 0) swap(i, target)
    }

    private fun gas(x: Int, y: Int, i: Int, e: Byte) {
        var clings = false
        when (e) {
            FIRE -> {
                clings = fireReact(x, y, i)
                if (cells[i] != FIRE) return
            }
            LIGHTNING -> lightningReact(x, y, i)
            STEAM -> if (chance(2) && touchesCoolSolid(x, y)) {
                cells[i] = WATER
                life[i] = 0
                return
            }
        }
        var l = lifeOf(i)
        if (l > 0) l--
        if (l == 0) {
            expire(i, e)
            return
        }
        life[i] = l.toByte()
        if (e == LIGHTNING) return
        // Fire sits on whatever it is burning; a free flame and the other gases drift upward.
        val rise = if (e != FIRE) 200 else if (clings) FIRE_RISE_ON_FUEL else 150
        if (y > 0 && chance(rise)) {
            val above = i - width
            if (rises(e, cells[above])) {
                swap(i, above)
                return
            }
            val dir = if (coin()) 1 else -1
            if (x + dir in 0 until width && rises(e, cells[above + dir])) {
                swap(i, above + dir)
                return
            }
            if (x - dir in 0 until width && rises(e, cells[above - dir])) {
                swap(i, above - dir)
                return
            }
        }
        if (chance(if (clings) 20 else 100)) {
            val dir = if (coin()) 1 else -1
            if (x + dir in 0 until width && cells[i + dir] == EMPTY) swap(i, i + dir)
        }
    }

    /** Gases rise into empty cells, bubble up through liquids, and pass heavier gases. */
    private fun rises(e: Byte, t: Byte): Boolean {
        if (t == EMPTY) return true
        val k = Elements.kind[t.toInt()]
        if (k == KIND_LIQUID) return e != FIRE
        return k == KIND_GAS && Elements.density[t.toInt()] > Elements.density[e.toInt()]
    }

    private fun expire(i: Int, e: Byte) {
        when (e) {
            FIRE -> if (chance(100)) {
                cells[i] = SMOKE
                life[i] = (80 + (next() and 63)).toByte()
            } else {
                cells[i] = EMPTY
            }
            STEAM -> cells[i] = WATER
            else -> cells[i] = EMPTY
        }
        charge[i] = 0
    }

    private fun touchesCoolSolid(x: Int, y: Int): Boolean {
        val up = at(x, y - 1)
        val left = at(x - 1, y)
        val right = at(x + 1, y)
        return up == WALL || up == GLASS || up == STONE || left == WALL || left == GLASS || right == WALL || right == GLASS
    }

    // ---------------------------------------------------------------------------------------
    // Reactions
    // ---------------------------------------------------------------------------------------

    /** Fire spreads to its 8 neighbours and turns to steam on water. Returns whether it is touching fuel. */
    private fun fireReact(x: Int, y: Int, i: Int): Boolean {
        var fuel = false
        for (dy in -1..1) {
            val ny = y + dy
            if (ny < 0 || ny >= height) continue
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                if (nx < 0 || nx >= width) continue
                val j = ny * width + nx
                val t = cells[j]
                if (t == WATER) {
                    cells[i] = STEAM
                    life[i] = (120 + (next() and 63)).toByte()
                    charge[i] = 0
                    return false
                }
                val f = Elements.flammability[t.toInt()]
                if (f > 0) {
                    fuel = true
                    if (chance(f)) ignite(nx, ny, j, t)
                }
            }
        }
        return fuel
    }

    private fun ignite(x: Int, y: Int, j: Int, t: Byte) {
        if (t == GUNPOWDER) {
            explode(x, y)
            return
        }
        cells[j] = FIRE
        life[j] = (Elements.burnLife[t.toInt()] + (next() and 15)).coerceAtMost(255).toByte()
        charge[j] = 0
    }

    private fun explode(cx: Int, cy: Int) {
        val r = EXPLOSION_RADIUS
        for (dy in -r..r) {
            val y = cy + dy
            if (y < 0 || y >= height) continue
            for (dx in -r..r) {
                val x = cx + dx
                val d2 = dx * dx + dy * dy
                if (x < 0 || x >= width || d2 > r * r) continue
                val j = y * width + x
                val t = cells[j]
                if (t == WALL || t == GLASS || t == STONE) continue
                if (Elements.kind[t.toInt()] == KIND_SOLID && d2 > 6) continue
                cells[j] = FIRE
                life[j] = (30 + (next() and 31)).toByte()
                charge[j] = 0
            }
        }
    }

    private fun lavaReact(x: Int, y: Int, i: Int) {
        if ((next() and 0x3FF) == 0) {
            cells[i] = STONE
            life[i] = 0
            return
        }
        for (k in 0 until 4) {
            val nx = x + DX[k]
            val ny = y + DY[k]
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
            val j = ny * width + nx
            val t = cells[j]
            when {
                t == WATER -> {
                    cells[j] = STEAM
                    life[j] = (150 + (next() and 63)).toByte()
                    charge[j] = 0
                    if (chance(128)) {
                        cells[i] = STONE
                        life[i] = 0
                        return
                    }
                }
                t == SAND -> if (chance(4)) cells[j] = GLASS
                Elements.flammability[t.toInt()] > 0 -> if (chance((Elements.flammability[t.toInt()] * 2).coerceAtMost(255))) ignite(nx, ny, j, t)
            }
        }
    }

    private fun acidReact(x: Int, y: Int, i: Int) {
        for (k in 0 until 4) {
            val nx = x + DX[k]
            val ny = y + DY[k]
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
            val j = ny * width + nx
            val t = cells[j]
            if (t == EMPTY || t == ACID || t == WALL || t == GLASS || Elements.kind[t.toInt()] == KIND_GAS) continue
            if (!chance(16)) continue
            if (chance(50)) {
                cells[j] = SMOKE
                life[j] = (60 + (next() and 31)).toByte()
            } else {
                cells[j] = EMPTY
                life[j] = 0
            }
            charge[j] = 0
            if (chance(96)) {
                cells[i] = EMPTY
                life[i] = 0
                charge[i] = 0
                return
            }
        }
    }

    private fun lightningReact(x: Int, y: Int, i: Int) {
        for (k in 0 until 4) {
            val nx = x + DX[k]
            val ny = y + DY[k]
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
            val j = ny * width + nx
            val t = cells[j]
            if (Elements.conducts[t.toInt()] && charge[j] == 0.toByte()) charge[j] = SPARK_LIFE
            val f = Elements.flammability[t.toInt()]
            if (f > 0 && chance((f * 4).coerceAtMost(255))) ignite(nx, ny, j, t)
        }
    }

    private fun solid(x: Int, y: Int, i: Int, e: Byte) {
        when (e) {
            PLANT -> for (k in 0 until 4) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                val j = ny * width + nx
                if (cells[j] == WATER && chance(3)) {
                    cells[j] = PLANT
                    life[j] = 0
                    charge[j] = 0
                }
            }
            BATTERY -> if (tick % BATTERY_PERIOD == 0L) {
                for (k in 0 until 4) {
                    val nx = x + DX[k]
                    val ny = y + DY[k]
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                    val j = ny * width + nx
                    if (Elements.conducts[cells[j].toInt()] && charge[j] == 0.toByte()) charge[j] = SPARK_LIFE
                }
            }
            HEATER -> if (charge[i] > 0) {
                for (k in 0 until 4) {
                    val nx = x + DX[k]
                    val ny = y + DY[k]
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                    val j = ny * width + nx
                    val t = cells[j]
                    when {
                        t == WATER -> if (chance(40)) {
                            cells[j] = STEAM
                            life[j] = (150 + (next() and 63)).toByte()
                            charge[j] = 0
                        }
                        t == SAND -> if (chance(2)) cells[j] = GLASS
                        Elements.flammability[t.toInt()] > 0 -> if (chance((Elements.flammability[t.toInt()] * 3).coerceAtMost(255))) ignite(nx, ny, j, t)
                    }
                }
            }
            LAMP -> if (charge[i] > 0) life[i] = LAMP_GLOW.toByte() else if (life[i].toInt() != 0) life[i] = (lifeOf(i) - 1).toByte()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------------------------------
    // Electricity
    // ---------------------------------------------------------------------------------------

    /**
     * Charged conductors wake idle 4-neighbours, then every charge ticks down (into a cooldown once
     * spent), then the woken cells light up. Reading before writing keeps the pass direction-neutral,
     * so a pulse walks one cell per tick whichever way it travels, and a spent cell cannot be re-lit
     * by the pulse that just left it.
     */
    private fun electricity() {
        pendingCount = 0
        for (i in 0 until size) {
            if (charge[i] > 0) {
                val x = i % width
                if (x > 0) wake(i - 1)
                if (x < width - 1) wake(i + 1)
                if (i >= width) wake(i - width)
                if (i + width < size) wake(i + width)
            }
        }
        for (i in 0 until size) {
            val c = charge[i].toInt()
            if (c > 0) {
                charge[i] = (if (c == 1) -Elements.cooldown[cells[i].toInt()] else c - 1).toByte()
            } else if (c < 0) {
                charge[i] = (c + 1).toByte()
            }
        }
        for (k in 0 until pendingCount) {
            val j = pending[k]
            if (charge[j] == 0.toByte() && Elements.conducts[cells[j].toInt()]) charge[j] = SPARK_LIFE
        }
    }

    /** Queues an idle conductor once, using the sweep's `moved` flags (free after the sweep) to skip duplicates. */
    private fun wake(j: Int) {
        if (charge[j] == 0.toByte() && moved[j] != PENDING && Elements.conducts[cells[j].toInt()]) {
            moved[j] = PENDING
            pending[pendingCount++] = j
        }
    }

    // ---------------------------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------------------------

    fun save(out: DataOutputStream) {
        out.writeInt(width)
        out.writeInt(height)
        out.writeLong(tick)
        out.write(cells)
        out.write(life)
        out.write(charge)
    }

    /** Restores a [save] of the same size; returns false and leaves the grid alone otherwise. */
    fun load(input: DataInputStream): Boolean {
        if (input.readInt() != width || input.readInt() != height) return false
        val savedTick = input.readLong()
        val c = ByteArray(size)
        val l = ByteArray(size)
        val q = ByteArray(size)
        input.readFully(c)
        input.readFully(l)
        input.readFully(q)
        for (i in 0 until size) if (c[i] < 0 || c[i] >= Elements.COUNT) return false
        c.copyInto(cells)
        l.copyInto(life)
        q.copyInto(charge)
        tick = savedTick
        return true
    }

    companion object {
        const val SPARK_LIFE: Byte = 2
        const val BATTERY_PERIOD = 10L
        const val LAMP_GLOW = 6
        const val EXPLOSION_RADIUS = 4

        /** Chance out of 256 per tick that a flame touching fuel drifts up instead of staying to burn it. */
        const val FIRE_RISE_ON_FUEL = 12
        private const val PENDING: Byte = 2
        private val DX = intArrayOf(-1, 1, 0, 0)
        private val DY = intArrayOf(0, 0, -1, 1)
    }
}
