package com.llgl.xnl

import android.app.WallpaperManager
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

/**
 * The terminal as a live wallpaper. Draws only while visible: 30 frames a second while something
 * types, prints or scrolls, ten a second while the cursor just blinks. A tap starts the next
 * command. On the lock screen (Android 14+) the rows under the clock fade.
 */
class XnlWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = TermEngine()

    private inner class TermEngine : Engine() {
        private val prefs = Prefs(this@XnlWallpaper)
        private val handler = Handler(Looper.getMainLooper())
        private var session: TermSession? = null
        private var visible = false

        private val frame = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                handler.postDelayed(this, if (session?.animating == true) FRAME_MS else IDLE_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            session = TermSession(this@XnlWallpaper, prefs)
            readFlags()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            session?.resize(width, height)
            if (visible) drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (isVisible) {
                session?.pause()
                readFlags()
                handler.post(frame)
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) session?.tap()
            super.onTouchEvent(event)
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            session?.lockFade = (which and WallpaperManager.FLAG_LOCK) != 0
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            session?.close()
            session = null
            super.onDestroy()
        }

        /** Android 14+ tells an engine whether it is showing on the lock screen, the home screen or both. */
        private fun readFlags() {
            if (Build.VERSION.SDK_INT >= 34) {
                session?.lockFade = (wallpaperFlags and WallpaperManager.FLAG_LOCK) != 0
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val s = session ?: return
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        holder.lockHardwareCanvas()
                    } catch (_: Exception) {
                        holder.lockCanvas()
                    }
                } else {
                    holder.lockCanvas()
                }
                if (canvas == null) return
                s.frame(canvas)
            } catch (_: Exception) {
                // A surface that went away mid-frame is not worth a crash; the next frame checks again.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private companion object {
        const val FRAME_MS = 33L
        const val IDLE_MS = 100L
    }
}
