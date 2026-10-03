package com.llgl.turtles

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** The accelerometer, low-passed into a tilt in g for the parallax. Nothing happens without one. */
class TiltSensor(context: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var gx = 0f
    private var gy = 0f

    fun start() {
        val s = accel ?: return
        manager?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
        val ay = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
        gx += (ax - gx) * 0.12f
        gy += (ay - gy) * 0.12f
        onTilt(gx, gy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
