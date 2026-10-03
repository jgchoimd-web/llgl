package com.llgl.vibe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.llgl.vibe.ui.VibeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deps = (application as VibeApp).deps
        setContent {
            VibeTheme {
                VibeRoot(deps)
            }
        }
    }

    override fun onStop() {
        // The system does not let a background app vibrate, so the music pauses with the screen.
        (application as VibeApp).deps.session.pause()
        super.onStop()
    }
}
