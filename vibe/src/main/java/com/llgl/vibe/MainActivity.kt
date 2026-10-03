package com.llgl.vibe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.llgl.vibe.ui.LiveScreen
import com.llgl.vibe.ui.VibeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deps = (application as VibeApp).deps
        setContent {
            VibeTheme {
                LiveScreen(deps)
            }
        }
    }
}
