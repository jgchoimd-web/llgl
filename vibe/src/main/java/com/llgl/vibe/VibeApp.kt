package com.llgl.vibe

import android.app.Application
import com.llgl.vibe.capture.LiveState
import com.llgl.vibe.haptics.VibeEngine

class Deps(app: Application) {
    val prefs = Prefs(app)
    val engine = VibeEngine(app)
}

class VibeApp : Application() {
    lateinit var deps: Deps
        private set

    override fun onCreate() {
        super.onCreate()
        deps = Deps(this)
        val prefs = deps.prefs
        LiveState.update { it.copy(mode = prefs.mode, intensity = prefs.intensity, muted = prefs.muted, suppress = prefs.suppress) }
    }
}
