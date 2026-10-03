package com.llgl.vibe.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.llgl.vibe.MainActivity
import com.llgl.vibe.R
import com.llgl.vibe.analysis.LiveAnalyzer
import com.llgl.vibe.haptics.LiveScore
import com.llgl.vibe.haptics.Mode
import com.llgl.vibe.haptics.VibeEngine
import kotlin.math.max

/**
 * Live mode: captures what other apps play (AudioPlaybackCapture, Android 10+), analyses it block
 * by block and drives the motor in 100 ms windows. A media-projection foreground service, so the
 * capture and the vibration are allowed while another app is on screen. Muting is the media
 * volume set to zero, restored when the mode stops; the capture taps each player's own stream
 * before the volume is applied, so it keeps hearing at full level.
 */
class CaptureService : Service() {
    private lateinit var engine: VibeEngine
    private lateinit var audio: AudioManager
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false
    private var savedVolume = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = VibeEngine(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        channel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEverything()
            ACTION_TOGGLE_MUTE -> {
                if (running) {
                    LiveState.update { it.copy(muted = !it.muted) }
                    applyMute()
                    refreshNotification()
                } else {
                    stopEverything()
                }
            }
            ACTION_START -> {
                if (running) return START_NOT_STICKY
                // startForegroundService() was used to reach here: go foreground before anything can fail.
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    } else {
                        startForeground(NOTIFICATION_ID, notification())
                    }
                } catch (e: Exception) {
                    fail("포그라운드 서비스를 시작할 수 없어요: ${e.message}")
                    return START_NOT_STICKY
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    fail("Android 10 이상에서만 됩니다")
                    return START_NOT_STICKY
                }
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                if (data == null) {
                    fail("캡처 동의 정보가 없어요")
                    return START_NOT_STICKY
                }
                if (!startProjection(code, data)) return START_NOT_STICKY
                applyMute()
                LiveState.update { it.copy(running = true, starting = false, error = null, beats = 0, silentMs = 0) }
                refreshNotification()
            }
            else -> if (!running) stopEverything()
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startProjection(code: Int, data: Intent): Boolean {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            manager.getMediaProjection(code, data)
        } catch (e: Exception) {
            null
        }
        if (mp == null) {
            fail("캡처 권한을 받지 못했어요. 다시 시도해 주세요.")
            return false
        }
        mp.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopEverything()
                }
            },
            mainHandler,
        )
        projection = mp
        return startCapture(mp)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startCapture(mp: MediaProjection): Boolean {
        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(max(minBuffer, RATE / 5 * 2))
                .build()
        } catch (e: SecurityException) {
            fail("오디오 캡처 권한(마이크)이 없어요")
            return false
        } catch (e: Exception) {
            fail("캡처를 열 수 없어요: ${e.message}")
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            fail("이 폰에서는 재생 캡처가 안 돼요")
            return false
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            fail("캡처를 시작할 수 없어요: ${e.message}")
            return false
        }
        record = rec
        running = true
        worker = Thread({ loop(rec) }, "vibe-live").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    private fun loop(rec: AudioRecord) {
        val analyzer = LiveAnalyzer(RATE, BLOCK_MS)
        val score = LiveScore()
        val shorts = ShortArray(analyzer.blockSize)
        val floats = FloatArray(analyzer.blockSize)
        val amps = IntArray(WINDOW_BLOCKS)
        val frames = ArrayList<LiveAnalyzer.Frame>(WINDOW_BLOCKS)
        var filled = 0
        var silentMs = 0
        var beats = 0
        var lastMode: Mode? = null
        while (running) {
            val n = try {
                rec.read(shorts, 0, shorts.size)
            } catch (e: Exception) {
                -1
            }
            if (!running) return
            if (n < 0) {
                mainHandler.post { if (running) fail("캡처가 끊겼어요 ($n)") }
                return
            }
            if (n < shorts.size) continue
            for (i in shorts.indices) floats[i] = shorts[i] / 32768f
            val frame = analyzer.feed(floats) ?: continue
            val live = LiveState.value
            score.mode = live.mode
            score.intensity = live.intensity
            if (lastMode != null && lastMode != live.mode) {
                // Switching modes: let the first window of the new mode start clean.
                engine.stop()
            }
            lastMode = live.mode
            silentMs = if (analyzer.lastRms < SILENCE_RMS) silentMs + BLOCK_MS else 0
            if (frame.beat) beats++
            amps[filled] = if (silentMs > 0) 0 else score.amplitude(frame, BLOCK_MS.toFloat())
            frames += frame
            filled++
            if (filled == WINDOW_BLOCKS) {
                if (silentMs < WINDOW_BLOCKS * BLOCK_MS) issue(score, amps, frames, live.mode)
                filled = 0
                frames.clear()
                val l = frame.loud
                val b = frame.bass
                val count = beats
                val silent = silentMs
                LiveState.update { it.copy(loud = l, bass = b, beats = count, silentMs = silent) }
            }
        }
    }

    private fun issue(score: LiveScore, amps: IntArray, frames: List<LiveAnalyzer.Frame>, mode: Mode) {
        if (mode == Mode.MELODY && engine.envelopes) {
            val points = score.envelope(frames, BLOCK_MS.toLong(), engine.envMinPointMs, engine.envMaxSize, engine.minHz, engine.maxHz)
            if (engine.playEnvelope(points)) return
        }
        engine.playSegments(score.segments(amps, BLOCK_MS.toLong()))
    }

    private fun applyMute() {
        try {
            if (LiveState.value.muted) {
                if (savedVolume < 0) savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else if (savedVolume >= 0) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                savedVolume = -1
            }
        } catch (_: Exception) {
        }
    }

    private fun fail(message: String) {
        LiveState.update { it.copy(error = message, starting = false) }
        stopEverything()
    }

    private fun stopEverything() {
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }
        record = null
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        engine.stop()
        if (savedVolume >= 0) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            } catch (_: Exception) {
            }
            savedVolume = -1
        }
        LiveState.update { it.copy(running = false, starting = false, loud = 0f, bass = 0f) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stopEverything()
        super.onDestroy()
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "실시간 진동", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        }
    }

    private fun refreshNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(NOTIFICATION_ID, notification())
        } catch (_: Exception) {
        }
    }

    private fun notification(): Notification {
        val live = LiveState.value
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val mute = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_TOGGLE_MUTE), flags)
        val stop = PendingIntent.getService(this, 2, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vibe)
            .setContentTitle("실시간 진동 켜짐")
            .setContentText("${live.mode.label} · ${if (live.muted) "소리 끔" else "소리 켬"} · 다른 앱의 소리를 진동으로")
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (live.muted) "소리 켜기" else "소리 끄기", mute)
            .addAction(0, "정지", stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.llgl.vibe.live.START"
        const val ACTION_STOP = "com.llgl.vibe.live.STOP"
        const val ACTION_TOGGLE_MUTE = "com.llgl.vibe.live.MUTE"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "live"
        private const val NOTIFICATION_ID = 7
        private const val RATE = 16000
        private const val BLOCK_MS = 20
        private const val WINDOW_BLOCKS = 5
        private const val SILENCE_RMS = 0.002f

        fun start(context: Context, resultCode: Int, data: Intent) {
            LiveState.update { it.copy(starting = true, error = null) }
            val intent = Intent(context, CaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_STOP))
        }

        fun toggleMute(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_TOGGLE_MUTE))
        }
    }
}
