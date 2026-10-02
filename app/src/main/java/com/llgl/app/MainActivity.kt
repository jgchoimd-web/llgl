package com.llgl.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.llgl.app.pet.Facing
import com.llgl.app.pet.PetService
import com.llgl.app.pet.PetSprites
import com.llgl.app.pet.PetState
import com.llgl.app.pet.PetStore
import com.llgl.app.pet.SpriteSheet
import com.llgl.app.ui.theme.LlglTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LlglTheme {
                SetupScreen()
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val store = remember { PetStore(context) }
    var name by remember { mutableStateOf(store.name) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotifications by remember { mutableStateOf(hasNotificationPermission(context)) }
    val running by PetService.isRunning
    val sheet = remember { SpriteSheet() }

    // Permission toggles happen in system settings; re-check whenever we come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(context)
                hasNotifications = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifications = granted
    }
    val stats by produceState(initialValue = store.statsNow(System.currentTimeMillis()), running) {
        while (true) {
            value = store.statsNow(System.currentTimeMillis())
            delay(5_000)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(96.dp, 64.dp)) {
                    drawImage(
                        image = sheet.frame(PetSprites.Pose.WALK_A, Facing.RIGHT),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
                    Text(text = stringResource(R.string.setup_intro, name), style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    store.name = it
                },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PermissionCard(
                title = stringResource(R.string.permission_overlay_title),
                description = stringResource(R.string.permission_overlay_desc),
                granted = canOverlay,
                buttonLabel = stringResource(R.string.permission_overlay_button),
                onClick = { openOverlaySettings(context) },
            )
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionCard(
                    title = stringResource(R.string.permission_notifications_title),
                    description = stringResource(R.string.permission_notifications_desc),
                    granted = hasNotifications,
                    buttonLabel = stringResource(R.string.permission_notifications_button),
                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            Button(
                onClick = { if (running) PetService.stop(context) else PetService.start(context) },
                enabled = running || canOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(if (running) R.string.stop_pet else R.string.start_pet))
            }
            Text(
                text = stringResource(if (running) R.string.pet_running else R.string.pet_stopped, name),
                style = MaterialTheme.typography.bodyMedium,
            )

            StatsCard(stats)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = stringResource(R.string.tips_title), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(R.string.tips_body), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, buttonLabel: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            if (granted) {
                Text(text = stringResource(R.string.permission_granted), color = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(onClick = onClick) { Text(buttonLabel) }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: PetState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
            StatBar(label = stringResource(R.string.stat_fullness), fraction = (100f - stats.hunger) / 100f)
            StatBar(label = stringResource(R.string.stat_happiness), fraction = stats.happiness / 100f)
        }
    }
}

@Composable
private fun StatBar(label: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openOverlaySettings(context: Context) {
    val withPackage = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
    try {
        context.startActivity(withPackage)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        } catch (e: ActivityNotFoundException) {
            // Some devices (Android Go) never allow overlays; the screen explains this.
        }
    }
}
