package com.llgl.xnl.info

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Display
import android.view.InputDevice
import androidx.core.content.ContextCompat
import com.llgl.xnl.term.CommandResult
import com.llgl.xnl.term.Commands
import com.llgl.xnl.term.Format
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The `xnl` built-ins: real facts read through Android's own APIs (no permissions needed), printed
 * the way a command-line tool prints its status. Runs on the session's worker thread.
 */
class DeviceInfo(private val context: Context) {
    /** Set by the session so `xnl wallpaper` can report the running engine. */
    var wallpaperInfo: (() -> List<String>)? = null

    /** The real user this app runs as, in Android's u0_aNNN form, at the project's host name. */
    fun prompt(): String {
        val uid = Process.myUid()
        val user = if (uid >= 10000) "u${uid / 100000}_a${uid % 100000 - 10000}" else "uid$uid"
        return "$user@xnl:/ $ "
    }

    fun banner(): List<String> = listOf(
        "XNL terminal $VERSION · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "kernel ${System.getProperty("os.version") ?: "?"} · ${Runtime.getRuntime().availableProcessors()} cpus · booted ${stamp(bootTime())}",
        "every line below is this phone's real output",
    )

    fun run(command: String): CommandResult {
        val topic = command.removePrefix("xnl").trim()
        val lines = try {
            when (topic) {
                "device" -> device()
                "battery" -> battery()
                "memory" -> memory()
                "uptime" -> uptime()
                "thermal" -> thermal()
                "display" -> display()
                "storage" -> storage()
                "net" -> net()
                "sensors" -> sensors()
                "cameras" -> cameras()
                "audio" -> audio()
                "input" -> input()
                "haptics" -> haptics()
                "wallpaper" -> wallpaperInfo?.invoke() ?: listOf("xnl wallpaper: no engine attached")
                "help" -> help()
                else -> return CommandResult(listOf("xnl: unknown topic '$topic'"), false)
            }
        } catch (e: Exception) {
            return CommandResult(listOf("xnl $topic: ${e.javaClass.simpleName}: ${e.message}"), false)
        }
        return CommandResult(lines, lines.isNotEmpty())
    }

    private fun device(): List<String> = Format.table(
        listOf(
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "device" to "${Build.DEVICE} (${Build.PRODUCT})",
            "board" to Build.BOARD,
            "hardware" to Build.HARDWARE,
            "soc" to if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "n/a before API 31",
            "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "security patch" to Build.VERSION.SECURITY_PATCH,
            "kernel" to (System.getProperty("os.version") ?: "?"),
            "abi" to Build.SUPPORTED_ABIS.joinToString(","),
            "bootloader" to Build.BOOTLOADER,
            "radio" to (Build.getRadioVersion() ?: "n/a"),
            "build" to Build.DISPLAY,
            "fingerprint" to Build.FINGERPRINT,
        ),
    )

    private fun battery(): List<String> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val i = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = when (i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
            else -> "unknown"
        }
        val plugged = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            0 -> "unplugged"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            8 -> "dock"
            else -> "other"
        }
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
            else -> "unknown"
        }
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val volt = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?"
        val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val charge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val cycles = if (Build.VERSION.SDK_INT >= 34) i?.getIntExtra(EXTRA_CYCLE_COUNT, -1) ?: -1 else -1
        val rows = ArrayList<Pair<String, String>>()
        rows += "level" to "$percent%"
        rows += "status" to "$status ($plugged)"
        rows += "health" to health
        if (temp != Int.MIN_VALUE) rows += "temperature" to String.format(Locale.US, "%.1f C", temp / 10f)
        if (volt > 0) rows += "voltage" to "$volt mV"
        if (current != Int.MIN_VALUE && current != 0) rows += "current" to String.format(Locale.US, "%.0f mA", current / 1000f)
        if (charge != Int.MIN_VALUE && charge > 0) rows += "charge" to String.format(Locale.US, "%.0f mAh", charge / 1000f)
        if (cycles >= 0) rows += "cycles" to "$cycles"
        rows += "technology" to tech
        return Format.table(rows)
    }

    private fun memory(): List<String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        return Format.table(
            listOf(
                "total" to Format.bytes(mi.totalMem),
                "available" to Format.bytes(mi.availMem),
                "low threshold" to Format.bytes(mi.threshold),
                "low memory" to if (mi.lowMemory) "yes" else "no",
                "app heap limit" to "${am.memoryClass}M (large ${am.largeMemoryClass}M)",
                "this process" to "heap ${Format.bytes(rt.totalMemory() - rt.freeMemory())} of ${Format.bytes(rt.totalMemory())}, max ${Format.bytes(rt.maxMemory())}",
                "native heap" to Format.bytes(Debug.getNativeHeapAllocatedSize()),
                "pss" to Format.bytes(Debug.getPss() * 1024L),
            ),
        )
    }

    private fun uptime(): List<String> {
        val elapsed = SystemClock.elapsedRealtime()
        val awake = SystemClock.uptimeMillis()
        return Format.table(
            listOf(
                "up" to Format.duration(elapsed),
                "awake" to Format.duration(awake),
                "asleep" to Format.duration(elapsed - awake),
                "booted" to stamp(bootTime()),
                "now" to stamp(System.currentTimeMillis()),
            ),
        )
    }

    private fun thermal(): List<String> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val rows = ArrayList<Pair<String, String>>()
        if (Build.VERSION.SDK_INT >= 29) {
            rows += "thermal status" to when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "none"
                PowerManager.THERMAL_STATUS_LIGHT -> "light"
                PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "?"
            }
        } else {
            rows += "thermal status" to "n/a before API 29"
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val headroom = pm.getThermalHeadroom(10)
            rows += "headroom (10 s)" to if (headroom.isNaN()) "n/a" else String.format(Locale.US, "%.2f", headroom)
        }
        rows += "power save" to if (pm.isPowerSaveMode) "on" else "off"
        rows += "device idle" to if (pm.isDeviceIdleMode) "yes" else "no"
        rows += "interactive" to if (pm.isInteractive) "yes" else "no"
        rows += "sustained perf" to if (pm.isSustainedPerformanceModeSupported) "supported" else "no"
        val i = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        if (temp != Int.MIN_VALUE) rows += "battery temp" to String.format(Locale.US, "%.1f C", temp / 10f)
        return Format.table(rows)
    }

    private fun display(): List<String> {
        val dm = context.resources.displayMetrics
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val rows = ArrayList<Pair<String, String>>()
        rows += "size" to "${dm.widthPixels}x${dm.heightPixels} px"
        rows += "density" to "${dm.densityDpi} dpi (x${String.format(Locale.US, "%.2f", dm.density)})"
        if (display != null) {
            rows += "name" to display.name
            rows += "refresh" to String.format(Locale.US, "%.1f Hz", display.refreshRate)
            val modes = display.supportedModes.map { "${it.physicalWidth}x${it.physicalHeight}@${String.format(Locale.US, "%.0f", it.refreshRate)}" }.distinct()
            rows += "modes" to (modes.take(6).joinToString(" ") + if (modes.size > 6) " +${modes.size - 6}" else "")
            val hdr: IntArray = if (Build.VERSION.SDK_INT >= 34) {
                display.mode.supportedHdrTypes
            } else {
                @Suppress("DEPRECATION")
                display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
            }
            rows += "hdr" to if (hdr.isEmpty()) "none" else hdr.joinToString(",") { hdrName(it) }
            rows += "rotation" to "${display.rotation * 90} deg"
            rows += "state" to when (display.state) {
                Display.STATE_ON -> "on"
                Display.STATE_OFF -> "off"
                Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "doze"
                else -> "state ${display.state}"
            }
        }
        return Format.table(rows)
    }

    private fun hdrName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "dolby-vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "hdr10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "hlg"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "hdr10+"
        else -> "type$type"
    }

    private fun storage(): List<String> {
        val rows = ArrayList<Pair<String, String>>()
        val data = StatFs(context.filesDir.path)
        rows += "data" to "${Format.bytes(data.availableBytes)} free of ${Format.bytes(data.totalBytes)}"
        val ext = context.getExternalFilesDir(null)
        if (ext != null) {
            val s = StatFs(ext.path)
            rows += "external" to "${Format.bytes(s.availableBytes)} free of ${Format.bytes(s.totalBytes)} (${Environment.getExternalStorageState()})"
            rows += "emulated" to if (Environment.isExternalStorageEmulated()) "yes" else "no"
        }
        rows += "cache" to Format.bytes(context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
        return Format.table(rows)
    }

    private fun net(): List<String> {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return listOf("no interfaces")
        val rows = ifaces.sortedBy { it.name }.map { ni ->
            val addrs = ni.inetAddresses.toList()
            val v4 = addrs.count { it.address.size == 4 }
            val v6 = addrs.size - v4
            val flags = buildString {
                append(if (ni.isUp) "UP" else "DOWN")
                if (ni.isLoopback) append(",LOOPBACK")
                if (ni.isPointToPoint) append(",P2P")
                if (ni.isVirtual) append(",VIRTUAL")
            }
            ni.name to "$flags mtu ${ni.mtu} inet $v4 inet6 $v6"
        }
        return Format.table(rows.take(14)) + if (rows.size > 14) listOf("... ${rows.size - 14} more") else emptyList()
    }

    private fun sensors(): List<String> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val all = sm.getSensorList(Sensor.TYPE_ALL)
        val lines = all.take(18).map { s ->
            "${s.name} (${s.vendor}) ${s.stringType.removePrefix("android.sensor.")} ${String.format(Locale.US, "%.2f", s.power)}mA" + if (s.isWakeUpSensor) " wakeup" else ""
        }
        return listOf("${all.size} sensors") + lines + if (all.size > 18) listOf("... ${all.size - 18} more") else emptyList()
    }

    private fun cameras(): List<String> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cm.cameraIdList
        if (ids.isEmpty()) return listOf("no cameras")
        return ids.map { id ->
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "?"
            }
            val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "legacy"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "limited"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "full"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "level3"
                else -> "?"
            }
            val px = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString("/") { String.format(Locale.US, "%.1f", it) } ?: "?"
            val aperture = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.joinToString("/") { String.format(Locale.US, "f%.1f", it) } ?: ""
            "camera $id: $facing $level ${px?.width ?: 0}x${px?.height ?: 0} ${focal}mm $aperture".trim()
        }
    }

    private fun audio(): List<String> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { "${deviceType(it.type)} ${it.productName}".trim() }.distinct()
        val ins = am.getDevices(AudioManager.GET_DEVICES_INPUTS).map { "${deviceType(it.type)} ${it.productName}".trim() }.distinct()
        val rows = ArrayList<Pair<String, String>>()
        rows += "outputs" to outs.take(6).joinToString(", ")
        rows += "inputs" to ins.take(6).joinToString(", ")
        rows += "sample rate" to "${am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "?"} Hz"
        rows += "buffer" to "${am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "?"} frames"
        rows += "media volume" to "${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
        rows += "ringer" to when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }
        return Format.table(rows)
    }

    private fun deviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "mic"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired-headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired-headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb-headset"
        AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
        AudioDeviceInfo.TYPE_FM_TUNER -> "fm"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "submix"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        26 -> "ble-headset"
        27 -> "ble-speaker"
        else -> "type$type"
    }

    private fun input(): List<String> {
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        if (devices.isEmpty()) return listOf("no input devices")
        return devices.take(12).map { d ->
            val src = ArrayList<String>()
            fun has(mask: Int, name: String) {
                if (d.sources and mask == mask) src += name
            }
            has(InputDevice.SOURCE_TOUCHSCREEN, "touchscreen")
            has(InputDevice.SOURCE_KEYBOARD, "keyboard")
            has(InputDevice.SOURCE_MOUSE, "mouse")
            has(InputDevice.SOURCE_STYLUS, "stylus")
            has(InputDevice.SOURCE_JOYSTICK, "joystick")
            has(InputDevice.SOURCE_GAMEPAD, "gamepad")
            has(InputDevice.SOURCE_DPAD, "dpad")
            has(SOURCE_ROTARY_ENCODER, "rotary")
            has(InputDevice.SOURCE_TOUCHPAD, "touchpad")
            "${d.name} [${src.joinToString(",")}]" + if (d.vendorId != 0 || d.productId != 0) String.format(Locale.US, " %04x:%04x", d.vendorId, d.productId) else ""
        }
    }

    private fun haptics(): List<String> {
        val rows = ArrayList<Pair<String, String>>()
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            rows += "vibrators" to vm.vibratorIds.joinToString(",").ifEmpty { "none" }
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        rows += "present" to if (vibrator.hasVibrator()) "yes" else "no"
        if (Build.VERSION.SDK_INT >= 26) rows += "amplitude control" to if (vibrator.hasAmplitudeControl()) "yes" else "no"
        if (Build.VERSION.SDK_INT >= 30) {
            val support = vibrator.areEffectsSupported(VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_DOUBLE_CLICK, VibrationEffect.EFFECT_HEAVY_CLICK)
            rows += "effects" to listOf("click", "tick", "double-click", "heavy-click").filterIndexed { i, _ -> support[i] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES }.joinToString(",").ifEmpty { "none reported" }
            rows += "primitives" to if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK)) "click,tick" else "not all"
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val f = vibrator.resonantFrequency
            rows += "resonant freq" to if (f.isNaN()) "n/a" else String.format(Locale.US, "%.0f Hz", f)
            val q = vibrator.qFactor
            rows += "q factor" to if (q.isNaN()) "n/a" else String.format(Locale.US, "%.1f", q)
        }
        return Format.table(rows)
    }

    private fun help(): List<String> = Format.table(Commands.HELP)

    private fun bootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private fun stamp(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US).format(Date(ms))

    private companion object {
        const val VERSION = "0.2.0"
        /** BatteryManager.EXTRA_CYCLE_COUNT, API 34. */
        const val EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT"
        /** InputDevice.SOURCE_ROTARY_ENCODER, API 26. */
        const val SOURCE_ROTARY_ENCODER = 0x00400000
    }
}
