package com.llgl.app.pet

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.llgl.app.R

/** SharedPreferences persistence for the turtle's name and stats. */
class PetStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var name: String
        get() = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.default_pet_name)
        set(value) = prefs.edit { putString(KEY_NAME, value.trim()) }

    fun load(): PetState? {
        val map = HashMap<String, String>()
        for (key in PET_KEYS) prefs.getString(key, null)?.let { map[key] = it }
        return PetState.fromMap(map)
    }

    fun save(state: PetState) {
        prefs.edit {
            for ((key, value) in state.toMap()) putString(key, value)
        }
    }

    /** The stored stats brought up to date, for the setup screen while the pet is not running. */
    fun statsNow(nowEpochMs: Long): PetState = PetRules.decayed(load() ?: PetState(), nowEpochMs)

    private companion object {
        const val PREFS_NAME = "pet"
        const val KEY_NAME = "name"
        val PET_KEYS = listOf(
            PetState.KEY_HUNGER,
            PetState.KEY_HAPPINESS,
            PetState.KEY_LAST_SEEN,
            PetState.KEY_GREETING_DAY,
            PetState.KEY_X,
        )
    }
}
