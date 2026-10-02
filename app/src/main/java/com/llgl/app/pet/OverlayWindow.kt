package com.llgl.app.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** What the overlay composable needs to draw one frame. Position lives in the window, not here. */
data class OverlaySnapshot(
    val pose: PetSprites.Pose = PetSprites.Pose.WALK_A,
    val bob: Int = 0,
    val facing: Facing = Facing.RIGHT,
    val bubble: Line? = null,
    val menuOpen: Boolean = false,
    val spriteOffsetX: Int = 0,
    val lettuceOffsetX: Int? = null,
)

/**
 * The floating window that hosts the turtle: creates the system overlay, moves it as the brain
 * walks, and turns gestures into brain calls. The window is normally exactly the size of the
 * sprite so it does not swallow taps meant for the home screen; it grows upward only while a
 * speech bubble, the menu or a lettuce is showing.
 */
@SuppressLint("RtlHardcoded")
class OverlayWindow(
    service: Context,
    private val brain: PetBrain,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpenSettings()
        fun onPersist()
        fun onOverlayFailed()
    }

    private val context: Context = createOverlayContext(service)
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    /** Device pixels per sprite pixel. */
    val pixelScale: Int = (4f * density).roundToInt().coerceAtLeast(2)
    val spriteW: Int = PetSprites.WIDTH * pixelScale
    val spriteH: Int = PetSprites.HEIGHT * pixelScale
    val lettuceSize: Int = PetSprites.LETTUCE_SIZE * pixelScale
    private val wideWidth = spriteW * 3
    private val topExtra = (72f * density).roundToInt()

    private val host = OverlayHost()
    private val params = WindowManager.LayoutParams(
        spriteW,
        spriteH,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 34) setCanPlayMoveAnimation(false)
    }
    private val view = OverlayView(context, ::onRawTouch) { MaterialTheme { PetOverlay(this) } }

    var snapshot: OverlaySnapshot by mutableStateOf(OverlaySnapshot())
        private set

    private var menuOpen = false
    private var menuTimer = 0f
    private var usableWidth = 1
    private var usableHeight = 1
    private var attached = false
    private var spriteOffsetX = 0
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragging = false
    private var sincePersist = 0f

    fun show() {
        measureUsable()
        view.id = View.generateViewId()
        view.setViewTreeLifecycleOwner(host)
        view.setViewTreeSavedStateRegistryOwner(host)
        applyLayout(force = true)
        host.start()
        try {
            windowManager.addView(view, params)
            attached = true
        } catch (e: WindowManager.BadTokenException) {
            listener.onOverlayFailed()
        } catch (e: SecurityException) {
            listener.onOverlayFailed()
        }
    }

    /** Hides the turtle and stops its frame clock (screen off, or paused from the notification). */
    fun pause() {
        view.visibility = View.GONE
        host.pause()
    }

    fun resume() {
        host.resume()
        view.visibility = View.VISIBLE
    }

    fun dismiss() {
        if (attached) {
            attached = false
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                // Already gone.
            }
        }
        host.destroy()
    }

    fun onConfigurationChanged() {
        measureUsable()
        applyLayout(force = true)
    }

    /** Called by the composable once per rendered frame. */
    fun onFrame(dt: Float) {
        brain.advance(dt)
        for (event in brain.drainEvents()) {
            if (event == PetEvent.Persist) listener.onPersist()
        }
        sincePersist += dt
        if (sincePersist >= PERSIST_INTERVAL_SECONDS) {
            sincePersist = 0f
            listener.onPersist()
        }
        if (menuOpen) {
            menuTimer -= dt
            if (menuTimer <= 0f) menuOpen = false
        }
        if (!dragging) applyLayout(force = false)
        publishSnapshot()
    }

    fun onTap() {
        if (menuOpen) {
            menuOpen = false
            return
        }
        brain.onTap()
    }

    fun onLongPress() {
        if (brain.activity == Activity.CARRIED || brain.activity == Activity.FALL) return
        menuOpen = true
        menuTimer = MENU_SECONDS
    }

    fun onDragStart() {
        menuOpen = false
        brain.onDragStart()
        dragging = true
        // Collapse to the plain sprite-sized window before moving it under the finger.
        applyLayout(force = true)
        dragStartRawX = lastRawX
        dragStartRawY = lastRawY
        dragStartX = params.x
        dragStartY = params.y
    }

    fun onDragMove() {
        if (!dragging) return
        params.x = (dragStartX + (lastRawX - dragStartRawX)).roundToInt()
            .coerceIn(0, (usableWidth - params.width).coerceAtLeast(0))
        params.y = (dragStartY - (lastRawY - dragStartRawY)).roundToInt()
            .coerceIn(0, (usableHeight - params.height).coerceAtLeast(0))
        brain.onDrag(params.x / walkable(), params.y / spriteH.toFloat())
        push()
    }

    fun onDragEnd() {
        if (!dragging) return
        dragging = false
        brain.onDragEnd(params.x / walkable(), params.y / spriteH.toFloat())
        applyLayout(force = true)
    }

    fun feed() {
        menuOpen = false
        brain.feed()
    }

    fun pet() {
        menuOpen = false
        brain.pet()
    }

    fun openSettings() {
        menuOpen = false
        listener.onOpenSettings()
    }

    fun closeMenu() {
        menuOpen = false
    }

    private fun walkable(): Float = (usableWidth - spriteW).coerceAtLeast(1).toFloat()

    private fun onRawTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (menuOpen) menuOpen = false
            return
        }
        lastRawX = event.rawX
        lastRawY = event.rawY
    }

    private fun applyLayout(force: Boolean) {
        val wide = menuOpen || brain.bubble != null || brain.lettuceX01 != null
        val layout = WindowGeometry.layout(
            x01 = brain.x01,
            yUnits = brain.yUnits,
            wide = wide,
            usableWidth = usableWidth,
            spriteW = spriteW,
            spriteH = spriteH,
            wideWidth = wideWidth,
            topExtra = topExtra,
            quantum = pixelScale,
        )
        spriteOffsetX = layout.spriteOffsetX
        val changed = force ||
            layout.x != params.x || layout.y != params.y ||
            layout.width != params.width || layout.height != params.height
        if (!changed) return
        params.x = layout.x
        params.y = layout.y
        params.width = layout.width
        params.height = layout.height
        push()
    }

    private fun push() {
        if (!attached) return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            attached = false
            listener.onOverlayFailed()
        }
    }

    private fun publishSnapshot() {
        val cel = PetSprites.celFor(brain.activity, brain.animTime)
        val lettuce = brain.lettuceX01?.let { l ->
            val absolute = (l * walkable()).roundToInt() + (spriteW - lettuceSize) / 2
            (absolute - params.x).coerceIn(0, (params.width - lettuceSize).coerceAtLeast(0))
        }
        val next = OverlaySnapshot(
            pose = cel.pose,
            bob = cel.bob,
            facing = brain.facing,
            bubble = brain.bubble,
            menuOpen = menuOpen,
            spriteOffsetX = spriteOffsetX,
            lettuceOffsetX = lettuce,
        )
        if (next != snapshot) snapshot = next
    }

    private fun measureUsable() {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            usableWidth = (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(spriteW)
            usableHeight = (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(spriteH)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            usableWidth = metrics.widthPixels.coerceAtLeast(spriteW)
            usableHeight = metrics.heightPixels.coerceAtLeast(spriteH)
        }
    }

    private companion object {
        const val PERSIST_INTERVAL_SECONDS = 60f
        const val MENU_SECONDS = 6f

        fun overlayType(): Int =
            if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        /** A window context gives correct metrics and avoids "non-visual context" warnings on API 30+. */
        fun createOverlayContext(service: Context): Context {
            if (Build.VERSION.SDK_INT < 30) return service
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?: return service
            return service.createDisplayContext(display).createWindowContext(overlayType(), null)
        }
    }
}
