package com.llgl.xnl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** The wallpaper's settings, shared by the app and the wallpaper service. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("xnl", Context.MODE_PRIVATE)

    /** Index into the renderer's accent palette. */
    var accent: Int
        get() = p.getInt("accent", 0).coerceIn(0, 4)
        set(value) = p.edit { putInt("accent", value.coerceIn(0, 4)) }

    /** The provisional wordmark; off hides the slot until a real logo exists. */
    var wordmark: Boolean
        get() = p.getBoolean("wordmark", true)
        set(value) = p.edit { putBoolean("wordmark", value) }

    var logSpeed: Float
        get() = p.getFloat("log_speed", 1f).coerceIn(0.3f, 3f)
        set(value) = p.edit { putFloat("log_speed", value.coerceIn(0.3f, 3f)) }

    var tilt: Boolean
        get() = p.getBoolean("tilt", true)
        set(value) = p.edit { putBoolean("tilt", value) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(listener)
}
