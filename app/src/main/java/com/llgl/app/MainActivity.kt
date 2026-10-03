package com.llgl.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.llgl.app.ui.theme.LlglTheme

class MainActivity : ComponentActivity() {
    private var view: TerrariumView? = null
    private var resumed = false
    private lateinit var prefs: Prefs
    private lateinit var sensors: Sensors

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        prefs = Prefs(this)
        sensors = Sensors(
            this,
            object : Sensors.Listener {
                override fun onTilt(gx: Float, gy: Float) {
                    view?.setTilt(gx, gy)
                }

                override fun onShake(strength: Float) {
                    view?.shake(strength)
                }

                override fun onLight(lux: Float) {
                    view?.setLux(lux)
                }
            },
            rotation = { displayRotation() },
        )
        setContent {
            LlglTheme(darkTheme = true, dynamicColor = false) {
                var showMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { context ->
                            TerrariumView(context).also { v ->
                                v.soundEnabled = prefs.sound
                                v.hapticsEnabled = prefs.haptics
                                v.onMenu = { showMenu = true }
                                view = v
                                if (resumed) v.resume()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (showMenu) {
                        MenuPanel(
                            sound = prefs.sound,
                            haptics = prefs.haptics,
                            onSound = { on ->
                                prefs.sound = on
                                view?.soundEnabled = on
                            },
                            onHaptics = { on ->
                                prefs.haptics = on
                                view?.hapticsEnabled = on
                            },
                            onReset = {
                                view?.reset()
                                showMenu = false
                            },
                            onClose = { showMenu = false },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hideSystemBars()
        sensors.start()
        view?.resume()
    }

    override fun onPause() {
        resumed = false
        sensors.stop()
        view?.pause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Before API 30 the system clears the immersive flags when focus is lost; put them back.
        if (hasFocus) hideSystemBars()
    }

    private fun displayRotation(): Int = try {
        if (Build.VERSION.SDK_INT >= 30) {
            display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    } catch (_: Exception) {
        0
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun MenuPanel(
    sound: Boolean,
    haptics: Boolean,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var soundOn by remember { mutableStateOf(sound) }
    var hapticsOn by remember { mutableStateOf(haptics) }
    // The scrim swallows touches so the box underneath is not poked, and a tap on it closes the menu.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(text = stringResource(R.string.menu_hint), style = MaterialTheme.typography.bodySmall)
                MenuSwitch(stringResource(R.string.menu_sound), soundOn) {
                    soundOn = it
                    onSound(it)
                }
                MenuSwitch(stringResource(R.string.menu_haptics), hapticsOn) {
                    hapticsOn = it
                    onHaptics(it)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onReset) { Text(stringResource(R.string.menu_reset)) }
                    Button(onClick = onClose) { Text(stringResource(R.string.menu_close)) }
                }
            }
        }
    }
}

@Composable
private fun MenuSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
