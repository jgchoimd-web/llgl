package com.llgl.xnl.term

/**
 * Everything the terminal cycles through.
 *
 *  - [SHELL]   real Linux commands, run in the phone's own `/system/bin/sh` (toybox, mksh); the
 *              output is whatever the phone actually prints. This list is read-only by construction.
 *  - [SESSION] classic Linux commands the phone does not ship (git, make, apt, docker, neofetch …),
 *              scripted with believable output so the stream reads like a real workstation.
 *  - [EGGS]    rare hidden gags (a fork bomb, `sudo rm -rf /` …). Scripted: they are *shown*, with a
 *              harmless or refusing reply, and executed by nothing. The router guarantees this.
 *  - [BUILTIN] `xnl` topics answered from Android's APIs; only used when the shell is turned off, so
 *              the wallpaper still shows real values on a phone that blocks command execution.
 *
 * Weights decide how often, periods how soon a command may come round again.
 */
object Commands {
    private fun shell(text: String, weight: Float, period: Float) = Command(text, false, weight, period)
    private fun builtin(text: String, weight: Float, period: Float) = Command(text, true, weight, period)
    private fun scripted(text: String, weight: Float, period: Float, lines: List<String>) = Command(text, false, weight, period, lines)

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
        shell("cat /sys/class/power_supply/battery/capacity /sys/class/power_supply/battery/status", 2f, 30f),
        shell("ls /sys/class/net", 1f, 180f),
        shell("for i in /sys/class/net/*; do echo \"\$(basename \$i) \$(cat \$i/operstate 2>/dev/null) mtu \$(cat \$i/mtu 2>/dev/null)\"; done", 1f, 60f),
        shell("getprop ro.product.model", 1f, 300f),
        shell("getprop ro.build.version.release", 1f, 300f),
        shell("getprop ro.build.version.security_patch", 1f, 300f),
        shell("getprop ro.board.platform", 0.5f, 300f),
        shell("getprop ro.hardware", 0.5f, 300f),
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

    val SESSION: List<Command> = listOf(
        scripted(
            "neofetch", 1.2f, 45f,
            listOf(
                "        #####          dev@xnl",
                "       #######         -----------------",
                "       ##O#O##         OS: XNL GNU/Linux x86_64",
                "       #######         Kernel: 6.9.0-xnl",
                "       #######         Uptime: 3 days, 4 hours",
                "        #####          Shell: bash 5.2.21",
                "         ###           CPU: XNL Core (8) @ 3.40GHz",
                "          #            Memory: 2048MiB / 16032MiB",
            ),
        ),
        scripted(
            "git status", 1.2f, 40f,
            listOf(
                "On branch main",
                "Your branch is up to date with 'origin/main'.",
                "",
                "nothing to commit, working tree clean",
            ),
        ),
        scripted(
            "git log --oneline -5", 1f, 70f,
            listOf(
                "c842355 term: scroll real commands and real output",
                "e256221 xnl: kernel-space build",
                "fa46039 wallpaper engine online",
                "0b4fd95 renderer: hardware canvas",
                "fc5b582 initial import",
            ),
        ),
        scripted(
            "ls -la ~/src/xnl", 1f, 50f,
            listOf(
                "total 48",
                "drwxr-xr-x  6 dev dev 4096 Oct  5 09:32 .",
                "drwxr-xr-x 21 dev dev 4096 Oct  1 11:02 ..",
                "-rw-r--r--  1 dev dev  312 Oct  2 14:10 Makefile",
                "drwxr-xr-x  3 dev dev 4096 Oct  5 09:30 src",
                "drwxr-xr-x  2 dev dev 4096 Oct  4 18:21 build",
                "-rw-r--r--  1 dev dev 1083 Sep 28 22:51 README.md",
            ),
        ),
        scripted(
            "make -j8", 1.2f, 60f,
            listOf(
                "  CC      src/sched.o",
                "  CC      src/mm.o",
                "  CC      src/vfs.o",
                "  CC      src/irq.o",
                "  LD      xnl",
                "Build complete.  (4.12s, 0 warnings)",
            ),
        ),
        scripted(
            "cat /etc/os-release", 0.8f, 120f,
            listOf(
                "NAME=\"XNL GNU/Linux\"",
                "VERSION=\"1.0 (kernelspace)\"",
                "ID=xnl",
                "ID_LIKE=debian",
                "PRETTY_NAME=\"XNL GNU/Linux 1.0\"",
                "HOME_URL=\"https://xnl.example\"",
            ),
        ),
        scripted(
            "apt list --upgradable", 0.8f, 90f,
            listOf(
                "Listing... Done",
                "coreutils/stable 9.4-1 amd64 [upgradable from: 9.3-2]",
                "libc6/stable 2.39-3 amd64 [upgradable from: 2.38-1]",
                "openssl/stable 3.3.1-1 amd64 [upgradable from: 3.2.2-1]",
                "3 packages can be upgraded. Run 'apt upgrade' to install them.",
            ),
        ),
        scripted(
            "docker ps", 0.9f, 80f,
            listOf(
                "CONTAINER ID   IMAGE            STATUS         NAMES",
                "a1b2c3d4e5f6   xnl/build:rc     Up 3 hours     ci-runner",
                "f6e5d4c3b2a1   redis:7-alpine   Up 2 days      cache",
                "9a8b7c6d5e4f   postgres:16      Up 2 days      db",
            ),
        ),
        scripted(
            "systemctl --failed", 0.7f, 120f,
            listOf(
                "  UNIT  LOAD  ACTIVE  SUB  DESCRIPTION",
                "",
                "0 loaded units listed.",
            ),
        ),
        scripted(
            "ping -c 3 1.1.1.1", 1f, 45f,
            listOf(
                "PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.",
                "64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms",
                "64 bytes from 1.1.1.1: icmp_seq=2 ttl=57 time=11.8 ms",
                "64 bytes from 1.1.1.1: icmp_seq=3 ttl=57 time=12.0 ms",
                "--- 1.1.1.1 ping statistics ---",
                "3 packets transmitted, 3 received, 0% packet loss, time 2003ms",
            ),
        ),
        scripted(
            "vmstat 1 2", 0.8f, 70f,
            listOf(
                "procs -----------memory---------- ---swap-- -----io---- -system-- ------cpu-----",
                " r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st",
                " 2  0      0 812340  24112 391020    0    0    12    30  210  540  4  2 93  1  0",
                " 1  0      0 811020  24112 391020    0    0     0     0  180  420  3  1 96  0  0",
            ),
        ),
        scripted(
            "dmesg | tail -4", 0.9f, 60f,
            listOf(
                "[ 1123.447201] xnl: scheduler tick calibrated",
                "[ 1190.022110] wlan0: associated with ap 9c:... link up",
                "[ 1201.773004] xnl: thermal zone 0 nominal (41 C)",
                "[ 1244.190882] input: touchscreen registered as /devices/virtual/input0",
            ),
        ),
        scripted(
            "cowsay hello", 0.6f, 150f,
            listOf(
                " _______ ",
                "< hello >",
                " ------- ",
                "        \\   ^__^",
                "         \\  (oo)\\_______",
                "            (__)\\       )\\/\\",
                "                ||----w |",
                "                ||     ||",
            ),
        ),
    )

    val EGGS: List<Command> = listOf(
        scripted(
            "sudo rm -rf /", 0.25f, 600f,
            listOf(
                "[sudo] password for dev: ",
                "rm: it is dangerous to operate recursively on '/'",
                "rm: use --no-preserve-root to override this failsafe",
                "(nothing was removed)",
            ),
        ),
        scripted(
            ":(){ :|:& };:", 0.25f, 600f,
            listOf(
                "bash: fork: retry: Resource temporarily unavailable",
                "bash: fork: retry: Resource temporarily unavailable",
                "bash: fork: retry: No child processes",
                "bash: fork: Interrupted system call",
                "(process limit held — nothing forked)",
            ),
        ),
        scripted(
            "sudo make me a sandwich", 0.2f, 600f,
            listOf(
                "make: *** No rule to make target 'me'.  Stop.",
                "okay.",
            ),
        ),
        scripted(
            "vim", 0.2f, 600f,
            listOf(
                "~",
                "~                VIM - Vi IMproved",
                "~",
                "~        to quit: press Esc, type :q! then Enter",
                "~        (yes, everyone gets stuck here)",
            ),
        ),
        scripted(
            "sl", 0.2f, 600f,
            listOf(
                "      ====        ________                ___________",
                "  _D _|  |_______/        \\__I_I_____===__|_________|",
                "   |(_)---  |   H\\________/ |   |        =|___ ___|",
                "   /     |  |   H  |  |     |   |         ||_| |_||",
                "  woo woo   (standard error: you typed sl, not ls)",
            ),
        ),
        scripted(
            "telnet towel.blinkenlights.nl", 0.2f, 600f,
            listOf(
                "Trying 213.136.8.188...",
                "Connected to towel.blinkenlights.nl.",
                "Escape character is '^]'.",
                "        Star Wars, Episode IV — asciimation",
                "        .  *  .   .  pew pew  .   *  .  .",
            ),
        ),
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
    val OPENING: List<String> = listOf("uname -a", "neofetch", "uptime")

    /**
     * The rotation. With the shell on it is real Linux commands plus the scripted session and eggs;
     * with it off the `xnl` built-ins stand in for the real commands so the wallpaper still shows
     * real values. The eggs ride along either way.
     */
    fun list(shell: Boolean): List<Command> = buildList {
        addAll(SESSION)
        addAll(EGGS)
        if (shell) addAll(SHELL) else addAll(BUILTIN)
    }
}
