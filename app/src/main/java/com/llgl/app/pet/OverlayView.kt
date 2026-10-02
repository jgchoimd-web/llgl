package com.llgl.app.pet

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView

/**
 * The overlay's root view. It sees every touch before Compose does, so the window can record raw
 * screen coordinates for dragging (local coordinates shift under the finger as the window moves)
 * and notice taps outside the window (ACTION_OUTSIDE) to close the menu.
 */
class OverlayView(
    context: Context,
    private val onTouch: (MotionEvent) -> Unit,
    private val content: @Composable () -> Unit,
) : AbstractComposeView(context) {

    @Composable
    override fun Content() {
        content()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }
}
