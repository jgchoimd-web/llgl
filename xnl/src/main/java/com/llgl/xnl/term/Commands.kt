package com.llgl.xnl.term

/**
 * The real commands the terminal cycles through. Shell lines run in the phone's own /system/bin/sh
 * (toybox tools, mksh) and print whatever they print; `xnl` topics are answered by the app through
 * Android's APIs. Weights decide how often, periods how soon a command may come round again.
 */
object Commands {
    private fun shell(text: String, weight: Float, period: Float) = Command(text, false, weight, period)
    private fun builtin(text: String, weight: Float, period: Float) = Command(text, true, weight, period)

    val SHELL: List<Command> = listOf(
        shell("uname -a", 1f, 300f),
        shell("cat /proc/version", 1f, 300f),
        shell("uptime", 3f, 20f),
        shell("uptime -p", 1f, 90f),
        shell("cat /proc/uptime", 2f, 25f),
        shell("cat /proc/loadavg", 2f, 25f),
        shell("date", 2f, 30f),
        shell("date -u '+%Y-%m-%d %H:%M:%S UTC (%s)'", 1f, 60f),
        shell("free -m", 2f, 40f),
        shell("head -n 6 /proc/meminfo", 1f, 60f),
        shell("df -h /data /system", 1f, 120f),
        shell("head -n 12 /proc/cpuinfo", 1f, 240f),
        shell("grep -c ^processor /proc/cpuinfo", 1f, 240f),
        shell("cat /sys/devices/system/cpu/online", 1f, 180f),
        shell("cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq", 3f, 15f),
        shell("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies", 0.5f, 300f),
        shell("for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type) \$(cat \$z/temp)\"; done | head -n 10", 2f, 30f),
        shell("cat /sys/class/power_supply/battery/capacity /sys/class/power_supply/battery/status /sys/class/power_supply/battery/temp", 2f, 30f),
        shell("ls /sys/class/net", 1f, 180f),
        shell("for i in /sys/class/net/*; do echo \"\$(basename \$i) \$(cat \$i/operstate 2>/dev/null) mtu \$(cat \$i/mtu 2>/dev/null)\"; done", 1f, 60f),
        shell("getprop ro.product.model", 1f, 300f),
        shell("getprop ro.product.manufacturer", 0.5f, 300f),
        shell("getprop ro.build.version.release", 1f, 300f),
        shell("getprop ro.build.version.security_patch", 1f, 300f),
        shell("getprop ro.board.platform", 0.5f, 300f),
        shell("getprop ro.hardware", 0.5f, 300f),
        shell("getprop ro.build.fingerprint", 0.5f, 300f),
        shell("id", 1f, 300f),
        shell("head -n 12 /proc/\$PPID/status", 1f, 90f),
        shell("ps -A", 1f, 120f),
        shell("env | grep -v CLASSPATH | sort", 0.5f, 240f),
        shell("ls -la /", 0.7f, 300f),
        shell("ls /system/bin | wc -l", 0.5f, 300f),
        shell("toybox --version", 0.5f, 300f),
        shell("echo \$KSH_VERSION", 0.5f, 300f),
        shell("cat /proc/sys/kernel/hostname", 0.5f, 300f),
        shell("head -n 8 /proc/mounts", 0.5f, 300f),
        shell("cat /proc/swaps", 0.5f, 300f),
    )

    val BUILTIN: List<Command> = listOf(
        builtin("xnl device", 1f, 300f),
        builtin("xnl battery", 3f, 30f),
        builtin("xnl memory", 2f, 40f),
        builtin("xnl uptime", 2f, 30f),
        builtin("xnl thermal", 2f, 45f),
        builtin("xnl display", 1f, 240f),
        builtin("xnl storage", 1f, 120f),
        builtin("xnl net", 1f, 60f),
        builtin("xnl sensors", 1f, 300f),
        builtin("xnl cameras", 0.7f, 300f),
        builtin("xnl audio", 0.7f, 300f),
        builtin("xnl input", 0.7f, 300f),
        builtin("xnl haptics", 0.5f, 300f),
        builtin("xnl wallpaper", 1f, 90f),
        builtin("xnl help", 0.3f, 600f),
    )

    /** What `xnl help` prints, one entry per built-in. */
    val HELP: List<Pair<String, String>> = listOf(
        "xnl device" to "model, board, soc, android, kernel, abi, build",
        "xnl battery" to "level, status, health, temperature, voltage, current",
        "xnl memory" to "system memory and this process",
        "xnl uptime" to "up, awake, asleep, boot time",
        "xnl thermal" to "thermal status, headroom, power save",
        "xnl display" to "size, density, refresh rate, modes, hdr",
        "xnl storage" to "data and external storage",
        "xnl net" to "network interfaces",
        "xnl sensors" to "hardware sensors",
        "xnl cameras" to "camera ids and characteristics",
        "xnl audio" to "audio devices and properties",
        "xnl input" to "input devices",
        "xnl haptics" to "vibrator capabilities",
        "xnl wallpaper" to "this wallpaper engine",
        "xnl help" to "this list",
    )

    /** The session opens with these, in order, when they are in the pool. */
    val OPENING: List<String> = listOf("uname -a", "xnl device", "uptime")

    fun list(shell: Boolean): List<Command> = if (shell) SHELL + BUILTIN else BUILTIN
}
