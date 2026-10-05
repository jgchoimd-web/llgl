package com.llgl.app

import android.content.Context
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.Display
import android.view.SurfaceHolder

/**
 * The terrarium as a live wallpaper: the same box behind the home screen, moved by tilt, shakes
 * and darkness. Touches are left to the launcher. It runs only while visible, at a gentler frame
 * rate than the app, and shares its saved state with the app so both show the same box.
 */
class TerrariumWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = TerrariumEngine()

    private inner class TerrariumEngine : Engine(), Sensors.Listener {
        private val context: Context = this@TerrariumWallpaper
        private val prefs = Prefs(context)
        private val session = TerrariumSession(context)
        private val sensors = Sensors(context, this, rotation = { displayRotation() })
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var haptics: Haptics? = null

        private val frame = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            session.resize(width, height)
            if (visible) drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                applyPrefs()
                session.reloadIfNewer()
                session.markResumed()
                sensors.start()
                handler.removeCallbacks(frame)
                handler.post(frame)
            } else {
                handler.removeCallbacks(frame)
                sensors.stop()
                session.sounds?.pause()
                session.save()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            sensors.stop()
            session.save()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            sensors.stop()
            session.release()
            super.onDestroy()
        }

        /** Sound and vibration are off on the wallpaper unless the app's menu turned them on. */
        private fun applyPrefs() {
            val wantSound = prefs.wallpaperSound
            if (wantSound && session.sounds == null) session.sounds = SoundBank(context)
            if (!wantSound && session.sounds != null) {
                session.sounds?.release()
                session.sounds = null
            }
            session.sounds?.resume()
            val wantHaptics = prefs.wallpaperHaptics
            if (wantHaptics && haptics == null) haptics = Haptics(context)
            session.haptics = if (wantHaptics) haptics else null
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
                session.tick(System.nanoTime())
                session.draw(canvas)
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

        private fun displayRotation(): Int = try {
            val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            manager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        } catch (_: Exception) {
            0
        }

        override fun onTilt(gx: Float, gy: Float) = session.setTilt(gx, gy)

        override fun onShake(strength: Float) = session.shake(strength)

        override fun onLight(lux: Float) = session.setLux(lux)
    }

    private companion object {
        /** 30 frames a second: the box still moves smoothly, the battery notices less. */
        const val FRAME_MS = 33L
    }
}
