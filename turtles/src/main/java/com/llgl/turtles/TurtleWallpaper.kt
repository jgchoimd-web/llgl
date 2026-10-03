package com.llgl.turtles

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.llgl.turtles.render.SkyRenderer
import com.llgl.turtles.sky.SkyScene

/**
 * The sky as a live wallpaper. It runs only while visible, at 30 frames a second, follows the
 * clock for its colours, tilts with the phone when that is on, and lets a tap roll a turtle.
 * When the system says it is on the lock screen (Android 14+), the turtles keep below the clock.
 */
class TurtleWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = SkyEngine()

    private inner class SkyEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val context: Context = this@TurtleWallpaper
        private val prefs = Prefs(context)
        private val scene = SkyScene((System.nanoTime() and 0x7FFFFFFF).toInt())
        private val renderer = SkyRenderer()
        private val handler = Handler(Looper.getMainLooper())
        private var tilt: TiltSensor? = null
        private var visible = false
        private var lastNanos = 0L
        private var zoom = 0f
        private var clockFrames = 0

        private val frame = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            applyPrefs()
            prefs.register(this)
            readFlags()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            scene.resize(width, height)
            if (visible) drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                lastNanos = 0L
                scene.hour = Prefs.currentHour()
                readFlags()
                startTilt()
                handler.removeCallbacks(frame)
                handler.post(frame)
            } else {
                handler.removeCallbacks(frame)
                tilt?.stop()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) scene.tap(event.x, event.y)
            super.onTouchEvent(event)
        }

        override fun onZoomChanged(zoom: Float) {
            this.zoom = zoom
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            scene.lockLayout = (which and WallpaperManager.FLAG_LOCK) != 0
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            tilt?.stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            tilt?.stop()
            prefs.unregister(this)
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            applyPrefs()
            if (visible) startTilt()
        }

        private fun applyPrefs() {
            scene.count = prefs.count
            scene.speedFactor = prefs.speed
        }

        private fun startTilt() {
            if (prefs.tilt) {
                if (tilt == null) {
                    tilt = TiltSensor(context) { x, y ->
                        scene.tiltX = x
                        scene.tiltY = y
                    }
                }
                tilt?.start()
            } else {
                tilt?.stop()
                scene.tiltX = 0f
                scene.tiltY = 0f
            }
        }

        /** Android 14+ tells an engine whether it is showing on the lock screen, the home screen or both. */
        private fun readFlags() {
            if (Build.VERSION.SDK_INT >= 34) {
                scene.lockLayout = (wallpaperFlags and WallpaperManager.FLAG_LOCK) != 0
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
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
                val now = System.nanoTime()
                val dt = if (lastNanos == 0L) FRAME_MS / 1000f else (now - lastNanos) / 1e9f
                lastNanos = now
                if (++clockFrames >= 60) {
                    clockFrames = 0
                    scene.hour = Prefs.currentHour()
                }
                scene.step(dt)
                renderer.draw(canvas, scene, zoom)
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
        /** 30 frames a second: the glide still looks smooth, the battery notices less. */
        const val FRAME_MS = 33L
    }
}
