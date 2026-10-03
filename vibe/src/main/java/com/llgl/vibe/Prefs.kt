package com.llgl.vibe

import android.content.Context
import androidx.core.content.edit
import com.llgl.vibe.haptics.Mode

/** The three settings worth remembering between launches. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("vibe", Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: Mode.FULL
        set(value) = p.edit { putString("mode", value.name) }

    var intensity: Float
        get() = p.getFloat("intensity", 1f).coerceIn(0.3f, 1.5f)
        set(value) = p.edit { putFloat("intensity", value) }

    /** Media volume to zero while listening; on by default, since feeling instead of hearing is the point. */
    var muted: Boolean
        get() = p.getBoolean("live_muted", true)
        set(value) = p.edit { putBoolean("live_muted", value) }

    /** Steady background sounds subtracted before anything vibrates; on by default. */
    var suppress: Boolean
        get() = p.getBoolean("suppress", true)
        set(value) = p.edit { putBoolean("suppress", value) }
}
