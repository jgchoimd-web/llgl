package com.llgl.vibe

import android.content.Context
import androidx.core.content.edit
import com.llgl.vibe.haptics.Mode
import com.llgl.vibe.library.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("vibe", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun touch() {
        _version.value = _version.value + 1
    }

    var mode: Mode
        get() = Mode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: Mode.FULL
        set(value) {
            p.edit { putString("mode", value.name) }
            touch()
        }

    var intensity: Float
        get() = p.getFloat("intensity", 1f)
        set(value) {
            p.edit { putFloat("intensity", value) }
            touch()
        }

    var muted: Boolean
        get() = p.getBoolean("muted", false)
        set(value) {
            p.edit { putBoolean("muted", value) }
            touch()
        }

    var recents: List<Song>
        get() = (p.getString("recents", "") ?: "").split('\n').filter { it.isNotBlank() }.mapNotNull { Song.load(it) }
        set(value) {
            p.edit { putString("recents", value.take(12).joinToString("\n") { it.save() }) }
            touch()
        }

    fun remember(song: Song) {
        recents = listOf(song) + recents.filter { it.key != song.key }
    }

    fun forget(song: Song) {
        recents = recents.filter { it.key != song.key }
    }

    fun pattern(slot: Int): String? = p.getString("pattern_$slot", null)

    fun setPattern(slot: Int, saved: String?) {
        p.edit { if (saved == null) remove("pattern_$slot") else putString("pattern_$slot", saved) }
        touch()
    }
}
