package com.llgl.tube

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

/**
 * The wallpaper. A WebView cannot draw into a wallpaper surface by itself, so the engine makes a
 * private virtual display whose output surface *is* the wallpaper surface, and shows the player on
 * it as a Presentation. Whatever the player renders, video included, is composited into the wallpaper.
 * Playback runs only while the wallpaper is visible. A tap pauses or resumes, a double tap skips.
 */
class TubeWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = TubeEngine()

    private inner class TubeEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = Prefs(this@TubeWallpaper)
        private val handler = Handler(Looper.getMainLooper())
        private var display: VirtualDisplay? = null
        private var presentation: PlayerPresentation? = null
        private var visible = false
        private var width = 0
        private var height = 0
        private var lastTap = 0L
        private val singleTap = Runnable { presentation?.toggle() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs.register(this)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            if (w == width && h == height && display != null) return
            width = w
            height = h
            rebuild(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            teardown()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            presentation?.setVisible(isVisible)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val now = event.eventTime
                if (now - lastTap < DOUBLE_TAP_MS) {
                    handler.removeCallbacks(singleTap)
                    lastTap = 0L
                    presentation?.next()
                } else {
                    lastTap = now
                    handler.removeCallbacks(singleTap)
                    handler.postDelayed(singleTap, DOUBLE_TAP_MS)
                }
            }
            super.onTouchEvent(event)
        }

        override fun onDestroy() {
            handler.removeCallbacks(singleTap)
            prefs.unregister(this)
            teardown()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            presentation?.applyPref(key)
        }

        private fun rebuild(holder: SurfaceHolder) {
            teardown()
            val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val dpi = resources.displayMetrics.densityDpi
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            val vd = try {
                dm.createVirtualDisplay("tube-wallpaper", width, height, dpi, holder.surface, flags)
            } catch (e: Exception) {
                prefs.status = "가상 디스플레이를 만들지 못했어요: ${e.message}"
                return
            }
            display = vd
            try {
                val p = PlayerPresentation(this@TubeWallpaper, vd.display, prefs)
                p.show()
                presentation = p
                if (visible) p.setVisible(true)
            } catch (e: Exception) {
                prefs.status = "플레이어 창을 열지 못했어요: ${e.message}"
                vd.release()
                display = null
            }
        }

        private fun teardown() {
            presentation?.destroy()
            presentation = null
            display?.release()
            display = null
        }
    }

    private companion object {
        const val DOUBLE_TAP_MS = 320L
    }
}
