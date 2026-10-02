package com.llgl.app.pet

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.llgl.app.MainActivity
import kotlin.random.Random

/**
 * Foreground service that owns the turtle while it roams. It must be a foreground service so the
 * overlay survives the app being backgrounded; "specialUse" is the declared type for this kind of
 * user-visible overlay.
 */
class PetService : Service(), OverlayWindow.Listener {

    private lateinit var store: PetStore
    private var brain: PetBrain? = null
    private var overlay: OverlayWindow? = null
    private var userPaused = false
    private var screenOff = false
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    syncOverlayVisibility()
                }
                Intent.ACTION_USER_PRESENT -> {
                    screenOff = false
                    syncOverlayVisibility()
                }
                Intent.ACTION_SCREEN_ON -> {
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    if (keyguard == null || !keyguard.isKeyguardLocked) {
                        screenOff = false
                        syncOverlayVisibility()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PetStore(this)
        PetNotification.ensureChannel(this)
        if (!startForegroundSafely()) return
        registerScreenReceiver()
        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                userPaused = true
                isPaused.value = true
                syncOverlayVisibility()
                startForegroundSafely()
            }
            ACTION_RESUME -> {
                userPaused = false
                isPaused.value = false
                syncOverlayVisibility()
                startForegroundSafely()
            }
            else -> ensureOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        persist()
        overlay?.dismiss()
        overlay = null
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
        isRunning.value = false
        isPaused.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onOpenSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onPersist() = persist()

    override fun onOverlayFailed() = stopSelf()

    private fun ensureOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val activeBrain = brain ?: PetBrain(Random(System.nanoTime()), SystemPetClock(), restored = store.load()).also { brain = it }
        overlay = OverlayWindow(this, activeBrain, this).also { it.show() }
        syncOverlayVisibility()
    }

    private fun syncOverlayVisibility() {
        val window = overlay ?: return
        if (userPaused || screenOff) window.pause() else window.resume()
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundSafely(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            PetNotification.ID,
            PetNotification.build(this, store.name, userPaused),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        true
    } catch (e: IllegalStateException) {
        // Android 12+ refuses foreground starts from the background; nothing to do but give up.
        stopSelf()
        false
    }

    private fun persist() {
        brain?.let { store.save(it.exportState()) }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    companion object {
        const val ACTION_START = "com.llgl.app.pet.START"
        const val ACTION_STOP = "com.llgl.app.pet.STOP"
        const val ACTION_PAUSE = "com.llgl.app.pet.PAUSE"
        const val ACTION_RESUME = "com.llgl.app.pet.RESUME"

        /** Observed by the setup screen; the service updates these on the main thread. */
        val isRunning = mutableStateOf(false)
        val isPaused = mutableStateOf(false)

        fun intent(context: Context, action: String): Intent =
            Intent(context, PetService::class.java).setAction(action)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }
    }
}
