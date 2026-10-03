package com.llgl.app

import android.content.Context
import androidx.core.content.edit
import com.llgl.app.world.Terrarium
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.random.Random

/** Small settings and the seed the box grows from. */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("terrarium", Context.MODE_PRIVATE)

    val seed: Long
        get() {
            if (!prefs.contains(KEY_SEED)) prefs.edit { putLong(KEY_SEED, Random.nextLong()) }
            return prefs.getLong(KEY_SEED, 1L)
        }

    /** When this terrarium was planted, for plant growth. */
    val plantedAt: Long
        get() {
            if (!prefs.contains(KEY_PLANTED)) prefs.edit { putLong(KEY_PLANTED, System.currentTimeMillis()) }
            return prefs.getLong(KEY_PLANTED, System.currentTimeMillis())
        }

    val ageDays: Float
        get() = ((System.currentTimeMillis() - plantedAt) / 86_400_000.0).toFloat().coerceAtLeast(0f)

    var sound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND, value) }

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTICS, value) }

    /** The wallpaper stays quiet unless asked: a box clicking on the home screen gets old fast. */
    var wallpaperSound: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER_SOUND, false)
        set(value) = prefs.edit { putBoolean(KEY_WALLPAPER_SOUND, value) }

    var wallpaperHaptics: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER_HAPTICS, false)
        set(value) = prefs.edit { putBoolean(KEY_WALLPAPER_HAPTICS, value) }

    /** Starts over with a new seed and a fresh planting date. */
    fun replant() {
        prefs.edit {
            putLong(KEY_SEED, Random.nextLong())
            putLong(KEY_PLANTED, System.currentTimeMillis())
        }
    }

    private companion object {
        const val KEY_SEED = "seed"
        const val KEY_PLANTED = "plantedAt"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_WALLPAPER_SOUND = "wallpaperSound"
        const val KEY_WALLPAPER_HAPTICS = "wallpaperHaptics"
    }
}

/**
 * Where everything lies when the app goes away, so it is still there when it comes back. One file
 * per box size, so the app and the wallpaper share a box when their surfaces match and never
 * overwrite each other when they do not.
 */
class TerrariumStore(context: Context) {
    private val dir: File = context.filesDir

    private fun file(world: Terrarium): File = File(dir, "terrarium_${world.width.toInt()}x${world.height.toInt()}.bin")

    fun save(world: Terrarium) {
        try {
            val target = file(world)
            val tmp = File(dir, target.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeFloat(world.width)
                out.writeFloat(world.height)
                world.save(out)
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (_: Exception) {
        }
    }

    /** Restores into [world] if a save exists for a box of the same size. */
    fun load(world: Terrarium): Boolean {
        val f = file(world)
        if (!f.exists()) return false
        return try {
            DataInputStream(f.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return false
                val w = input.readFloat()
                val h = input.readFloat()
                if (w != world.width || h != world.height) return false
                world.load(input)
            }
        } catch (_: Exception) {
            false
        }
    }

    /** When the save for [world]'s size was last written, or 0 when there is none. */
    fun lastModified(world: Terrarium): Long = file(world).let { if (it.exists()) it.lastModified() else 0L }

    fun deleteAll() {
        dir.listFiles { f -> f.name.startsWith("terrarium_") && f.name.endsWith(".bin") }?.forEach { it.delete() }
        File(dir, "terrarium.bin").delete()
    }

    private companion object {
        const val MAGIC = 0x54455252
        const val VERSION = 1
    }
}
