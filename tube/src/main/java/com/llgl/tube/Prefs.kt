package com.llgl.tube

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Settings and the little bit of live state (now playing, status) shared by the app and the wallpaper. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("tube", Context.MODE_PRIVATE)

    /** What the user typed: a handle, id or url. */
    var channel: String
        get() = p.getString(KEY_CHANNEL, DEFAULT_CHANNEL) ?: DEFAULT_CHANNEL
        set(value) = p.edit { putString(KEY_CHANNEL, value) }

    /** The resolved channel id the player actually uses. */
    var channelId: String
        get() = p.getString(KEY_CHANNEL_ID, DEFAULT_CHANNEL_ID) ?: DEFAULT_CHANNEL_ID
        set(value) = p.edit { putString(KEY_CHANNEL_ID, value) }

    var channelTitle: String
        get() = p.getString(KEY_CHANNEL_TITLE, DEFAULT_CHANNEL_TITLE) ?: DEFAULT_CHANNEL_TITLE
        set(value) = p.edit { putString(KEY_CHANNEL_TITLE, value) }

    /** cover (crop to fill the screen) or contain (letterbox). */
    var fit: String
        get() = p.getString(KEY_FIT, "cover") ?: "cover"
        set(value) = p.edit { putString(KEY_FIT, value) }

    var muted: Boolean
        get() = p.getBoolean(KEY_MUTED, false)
        set(value) = p.edit { putBoolean(KEY_MUTED, value) }

    /** Only play on Wi-Fi / ethernet; otherwise the wallpaper shows a note and waits. */
    var wifiOnly: Boolean
        get() = p.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = p.edit { putBoolean(KEY_WIFI_ONLY, value) }

    var nowPlaying: String
        get() = p.getString(KEY_NOW_PLAYING, "") ?: ""
        set(value) = p.edit { putString(KEY_NOW_PLAYING, value) }

    var status: String
        get() = p.getString(KEY_STATUS, "") ?: ""
        set(value) = p.edit { putString(KEY_STATUS, value) }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_CHANNEL_TITLE = "channel_title"
        const val KEY_FIT = "fit"
        const val KEY_MUTED = "muted"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_NOW_PLAYING = "now_playing"
        const val KEY_STATUS = "status"

        const val DEFAULT_CHANNEL = "@MentalOutlaw"
        /** Verified against YouTube's feed: this id's feed title is "Mental Outlaw". */
        const val DEFAULT_CHANNEL_ID = "UC7YOGHUfC1Tb6E4pudI9STA"
        const val DEFAULT_CHANNEL_TITLE = "Mental Outlaw"
    }
}
