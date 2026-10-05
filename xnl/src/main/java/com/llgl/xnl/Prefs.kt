package com.llgl.xnl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** The wallpaper's settings, shared by the app and the wallpaper service. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("xnl", Context.MODE_PRIVATE)

    /** Index into the renderer's accent palette. */
    var accent: Int
        get() = p.getInt(KEY_ACCENT, 0).coerceIn(0, 4)
        set(value) = p.edit { putInt(KEY_ACCENT, value.coerceIn(0, 4)) }

    /** How many characters fit across the screen; the font follows. */
    var columns: Int
        get() = p.getInt(KEY_COLUMNS, 56).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        set(value) = p.edit { putInt(KEY_COLUMNS, value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)) }

    /** Typing, printing and pauses, scaled. */
    var speed: Float
        get() = p.getFloat(KEY_SPEED, 1f).coerceIn(0.5f, 3f)
        set(value) = p.edit { putFloat(KEY_SPEED, value.coerceIn(0.5f, 3f)) }

    /** Run real shell commands in /system/bin/sh; off keeps only the `xnl` built-ins. */
    var shell: Boolean
        get() = p.getBoolean(KEY_SHELL, true)
        set(value) = p.edit { putBoolean(KEY_SHELL, value) }

    /** Commands that failed on this phone once and are never offered again. */
    val broken: Set<String>
        get() = p.getStringSet(KEY_BROKEN, null)?.let { HashSet(it) } ?: emptySet()

    fun markBroken(text: String) {
        val set = HashSet(broken)
        if (set.add(text)) p.edit { putStringSet(KEY_BROKEN, set) }
    }

    fun resetBroken() = p.edit { remove(KEY_BROKEN) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_COLUMNS = "columns"
        const val KEY_SPEED = "speed"
        const val KEY_SHELL = "shell"
        const val KEY_BROKEN = "broken"
        const val MIN_COLUMNS = 40
        const val MAX_COLUMNS = 80
    }
}
