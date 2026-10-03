package com.llgl.vibe.capture

import com.llgl.vibe.haptics.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the live-capture service is doing, shared with the screen. */
object LiveState {
    data class Live(
        val running: Boolean = false,
        val starting: Boolean = false,
        val mode: Mode = Mode.FULL,
        val intensity: Float = 1f,
        val muted: Boolean = true,
        /** Steady background sounds are subtracted before anything vibrates. */
        val suppress: Boolean = true,
        /** Meter values for the screen, 0..1. */
        val loud: Float = 0f,
        val bass: Float = 0f,
        /** Count of beats so far; the screen flashes when it changes. */
        val beats: Int = 0,
        /** How long nothing has been captured, in ms. */
        val silentMs: Int = 0,
        val error: String? = null,
    )

    private val _flow = MutableStateFlow(Live())
    val flow: StateFlow<Live> = _flow

    val value: Live get() = _flow.value

    fun update(change: (Live) -> Live) {
        _flow.value = change(_flow.value)
    }
}
