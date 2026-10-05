package com.llgl.sandbox

import android.content.Context
import androidx.core.content.edit
import com.llgl.sandbox.sim.Elements
import com.llgl.sandbox.sim.Grid
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** What the controls were set to last time. */
class SandboxPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sandbox", Context.MODE_PRIVATE)

    var element: Byte
        get() = prefs.getInt(KEY_ELEMENT, Elements.SAND.toInt()).toByte().let { if (it in Elements.palette) it else Elements.SAND }
        set(value) = prefs.edit { putInt(KEY_ELEMENT, value.toInt()) }

    var brush: Int
        get() = prefs.getInt(KEY_BRUSH, 3).coerceIn(1, 12)
        set(value) = prefs.edit { putInt(KEY_BRUSH, value) }

    private companion object {
        const val KEY_ELEMENT = "element"
        const val KEY_BRUSH = "brush"
    }
}

/** The grid on disk, one file per grid size. */
class GridStore(context: Context) {
    private val dir: File = context.filesDir

    private fun file(grid: Grid): File = File(dir, "sandbox_${grid.width}x${grid.height}.bin")

    fun save(grid: Grid) {
        try {
            val target = file(grid)
            val tmp = File(dir, target.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                grid.save(out)
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (_: Exception) {
        }
    }

    fun load(grid: Grid): Boolean {
        val f = file(grid)
        if (!f.exists()) return false
        return try {
            DataInputStream(f.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return false
                grid.load(input)
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val MAGIC = 0x53414E44
        const val VERSION = 1
    }
}
