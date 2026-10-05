package com.llgl.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Reads the phone: tilt (low-passed gravity, in screen terms for a portrait phone), shakes (the
 * high-passed remainder), and ambient light.
 */
class Sensors(
    context: Context,
    private val listener: Listener,
    /** The display rotation (Surface.ROTATION_*), since sensor axes stay fixed to the device. */
    private val rotation: () -> Int = { 0 },
) : SensorEventListener {
    interface Listener {
        /** In-plane gravity in g: +x toward the right edge, +y toward the bottom edge. */
        fun onTilt(gx: Float, gy: Float)

        fun onShake(strength: Float)
        fun onLight(lux: Float)
    }

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val light = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var gx = 0f
    private var gy = 0f
    private var gz = SensorManager.STANDARD_GRAVITY
    private var lastShakeAt = 0L

    fun start() {
        val m = manager ?: return
        accelerometer?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        light?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                gx += (ax - gx) * LOW_PASS
                gy += (ay - gy) * LOW_PASS
                gz += (az - gz) * LOW_PASS
                val g = SensorManager.STANDARD_GRAVITY
                // Right edge down → the sensor reads -x; top edge down → it reads -y (screen y points down).
                var tx = -gx / g
                var ty = gy / g
                when (rotation()) {
                    1 -> { // ROTATION_90: the device's +y edge is now the screen's right
                        val t = tx
                        tx = -ty
                        ty = -t
                    }
                    2 -> { // ROTATION_180
                        tx = -tx
                        ty = -ty
                    }
                    3 -> { // ROTATION_270
                        val t = tx
                        tx = ty
                        ty = t
                    }
                }
                listener.onTilt(tx.coerceIn(-1f, 1f), ty.coerceIn(-1f, 1f))
                val rx = ax - gx
                val ry = ay - gy
                val rz = az - gz
                val residual = sqrt(rx * rx + ry * ry + rz * rz)
                val now = SystemClock.uptimeMillis()
                if (residual > SHAKE_THRESHOLD && now - lastShakeAt > SHAKE_GAP_MS) {
                    lastShakeAt = now
                    listener.onShake((residual / SHAKE_THRESHOLD).coerceIn(1f, 2f))
                }
            }
            Sensor.TYPE_LIGHT -> listener.onLight(event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val LOW_PASS = 0.15f
        const val SHAKE_THRESHOLD = 12f
        const val SHAKE_GAP_MS = 700L
    }
}
