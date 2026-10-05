package com.llgl.gameforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.llgl.gameforge.ui.GameForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deps = (application as GameForgeApp).deps
        setContent {
            GameForgeTheme {
                GameForgeRoot(deps)
            }
        }
    }
}
