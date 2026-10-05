package com.llgl.xnl

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import com.llgl.xnl.kernel.KernelScene

/** The real numbers on the status line: uptime, memory, battery, host kernel, cores. No permissions needed. */
class SystemFacts(context: Context) {
    private val app = context.applicationContext
    private val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val battery = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val info = ActivityManager.MemoryInfo()
    private val kernel: String = (System.getProperty("os.version") ?: "").substringBefore('-').take(24)
    private val cores: Int = Runtime.getRuntime().availableProcessors()

    /** The cheap part, every frame. */
    fun tick(scene: KernelScene) {
        scene.uptimeMs = SystemClock.elapsedRealtime()
    }

    /** The slower part, every few seconds. */
    fun refresh(scene: KernelScene) {
        scene.hostKernel = kernel
        if (scene.cores != cores) scene.cores = cores
        try {
            activity?.getMemoryInfo(info)
            scene.memTotalMb = info.totalMem / (1024L * 1024L)
            scene.memUsedMb = (info.totalMem - info.availMem) / (1024L * 1024L)
        } catch (_: Exception) {
        }
        try {
            val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            scene.battery = if (level in 0..100) level else -1
        } catch (_: Exception) {
        }
    }
}
