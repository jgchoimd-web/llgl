package com.llgl.vibe

import android.app.Application
import com.llgl.vibe.player.Session

class Deps(app: Application) {
    val prefs = Prefs(app)
    val cache = AnalysisCache(app)
    val session = Session(app, prefs, cache)
}

class VibeApp : Application() {
    lateinit var deps: Deps
        private set

    override fun onCreate() {
        super.onCreate()
        deps = Deps(this)
    }
}
