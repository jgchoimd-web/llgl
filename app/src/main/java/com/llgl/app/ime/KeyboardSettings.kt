package com.llgl.app.ime

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** User-tunable keyboard parameters, stored in SharedPreferences. */
class KeyboardSettings(context: Context) {

    data class Values(
        val heightDp: Int = 400,
        /** One pulse of the dial: how far the thumb turns between neighbouring vowels or finals. */
        val tickDegrees: Int = 9,
        val longFlickDp: Int = 42,
        val tapRadiusDp: Int = 12,
        val haptics: Boolean = true,
        val hints: Boolean = true,
        val leftHanded: Boolean = false,
        val autoSpace: Boolean = true,
        val showTrail: Boolean = true,
    )

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Values {
        val defaults = Values()
        return Values(
            heightDp = prefs.getInt(KEY_HEIGHT, defaults.heightDp).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
            tickDegrees = prefs.getInt(KEY_TICK, defaults.tickDegrees).coerceIn(MIN_TICK_DEGREES, MAX_TICK_DEGREES),
            longFlickDp = prefs.getInt(KEY_LONG_FLICK, defaults.longFlickDp).coerceIn(24, 80),
            tapRadiusDp = prefs.getInt(KEY_TAP_RADIUS, defaults.tapRadiusDp).coerceIn(6, 24),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            hints = prefs.getBoolean(KEY_HINTS, defaults.hints),
            leftHanded = prefs.getBoolean(KEY_LEFT_HANDED, defaults.leftHanded),
            autoSpace = prefs.getBoolean(KEY_AUTO_SPACE, defaults.autoSpace),
            showTrail = prefs.getBoolean(KEY_TRAIL, defaults.showTrail),
        )
    }

    fun save(values: Values) {
        prefs.edit {
            putInt(KEY_HEIGHT, values.heightDp)
            putInt(KEY_TICK, values.tickDegrees)
            putInt(KEY_LONG_FLICK, values.longFlickDp)
            putInt(KEY_TAP_RADIUS, values.tapRadiusDp)
            putBoolean(KEY_HAPTICS, values.haptics)
            putBoolean(KEY_HINTS, values.hints)
            putBoolean(KEY_LEFT_HANDED, values.leftHanded)
            putBoolean(KEY_AUTO_SPACE, values.autoSpace)
            putBoolean(KEY_TRAIL, values.showTrail)
        }
    }

    /** Asks a running keyboard to forget every learned word (it listens for this key changing). */
    fun requestModelReset() {
        prefs.edit { putInt(KEY_MODEL_RESET, prefs.getInt(KEY_MODEL_RESET, 0) + 1) }
    }

    fun addListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)

    fun removeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val MIN_HEIGHT_DP = 300
        const val MAX_HEIGHT_DP = 520
        const val MIN_TICK_DEGREES = 6
        const val MAX_TICK_DEGREES = 14
        const val KEY_MODEL_RESET = "modelReset"
        private const val PREFS = "keyboard"
        private const val KEY_HEIGHT = "heightDp"
        private const val KEY_TICK = "tickDegrees"
        private const val KEY_LONG_FLICK = "longFlickDp"
        private const val KEY_TAP_RADIUS = "tapRadiusDp"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_HINTS = "hints"
        private const val KEY_LEFT_HANDED = "leftHanded"
        private const val KEY_AUTO_SPACE = "autoSpace"
        private const val KEY_TRAIL = "trail"
    }
}
