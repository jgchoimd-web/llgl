package com.llgl.gameforge

import android.app.Application
import android.content.ComponentCallbacks2
import com.llgl.gameforge.games.GameStore
import com.llgl.gameforge.gen.GenerationController
import com.llgl.gameforge.llm.Engine
import com.llgl.gameforge.model.Downloader
import com.llgl.gameforge.model.ModelFiles
import java.io.File

/** Everything the screens share. Lives as long as the process. */
class Deps(app: Application) {
    val settings = Settings(app)
    val files = ModelFiles(app)
    val store = GameStore(File(app.filesDir, "games"))
    val downloader = Downloader(app, settings, files)
    val generation = GenerationController(app, settings, files, store)
}

class GameForgeApp : Application() {
    lateinit var deps: Deps
        private set

    override fun onCreate() {
        super.onCreate()
        deps = Deps(this)
        deps.downloader.resumeTracking()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // A loaded Gemma is the biggest thing we hold; drop it when the system is squeezed and we are idle.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && !deps.generation.running && !Engine.busy) {
            Engine.unload()
        }
    }
}
