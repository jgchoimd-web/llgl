package com.llgl.turtles

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Calendar

/** The wallpaper's settings, shared by the app and the wallpaper service. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("turtles", Context.MODE_PRIVATE)

    var count: Int
        get() = p.getInt("count", 6).coerceIn(1, 14)
        set(value) = p.edit { putInt("count", value.coerceIn(1, 14)) }

    var speed: Float
        get() = p.getFloat("speed", 1f).coerceIn(0.5f, 2f)
        set(value) = p.edit { putFloat("speed", value.coerceIn(0.5f, 2f)) }

    /** Tilting the phone shifts the layers a little. */
    var tilt: Boolean
        get() = p.getBoolean("tilt", true)
        set(value) = p.edit { putBoolean("tilt", value) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        /** The hour of the day as a fraction, for the sky colours. */
        fun currentHour(): Float {
            val c = Calendar.getInstance()
            return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f + c.get(Calendar.SECOND) / 3600f
        }
    }
}
