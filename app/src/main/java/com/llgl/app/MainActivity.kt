package com.llgl.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.llgl.app.dial.DialTables
import com.llgl.app.ime.KeyboardSettings
import com.llgl.app.ui.theme.LlglTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    val store = remember { KeyboardSettings(context) }
    var values by remember { mutableStateOf(store.load()) }
    var enabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var selected by remember { mutableStateOf(isKeyboardSelected(context)) }
    var practice by remember { mutableStateOf("") }
    var resetDone by remember { mutableStateOf(false) }

    // Enabling happens in system settings and selecting in a system dialog, so poll for the status.
    LaunchedEffect(Unit) {
        while (true) {
            enabled = isKeyboardEnabled(context)
            selected = isKeyboardSelected(context)
            delay(1_000)
        }
    }

    fun update(block: KeyboardSettings.Values.() -> KeyboardSettings.Values) {
        values = values.block()
        store.save(values)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)

            StepCard(
                title = stringResource(R.string.step_enable_title),
                description = stringResource(R.string.step_enable_desc),
                done = enabled,
                pendingStatus = stringResource(R.string.status_not_enabled),
                buttonLabel = stringResource(R.string.step_enable_button),
                buttonWhenDone = false,
                onClick = { openInputMethodSettings(context) },
            )
            StepCard(
                title = stringResource(R.string.step_select_title),
                description = stringResource(R.string.step_select_desc),
                done = selected,
                pendingStatus = stringResource(R.string.status_not_selected),
                buttonLabel = stringResource(R.string.step_select_button),
                buttonWhenDone = true,
                onClick = { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.practice_title), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = practice,
                        onValueChange = { practice = it },
                        label = { Text(stringResource(R.string.practice_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)

                    Text(text = stringResource(R.string.setting_height, values.heightDp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = values.heightDp.toFloat(),
                        onValueChange = { v -> update { copy(heightDp = (v / 10f).roundToInt() * 10) } },
                        valueRange = KeyboardSettings.MIN_HEIGHT_DP.toFloat()..KeyboardSettings.MAX_HEIGHT_DP.toFloat(),
                        steps = (KeyboardSettings.MAX_HEIGHT_DP - KeyboardSettings.MIN_HEIGHT_DP) / 10 - 1,
                    )

                    Text(text = stringResource(R.string.setting_tick, values.tickDegrees), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = values.tickDegrees.toFloat(),
                        onValueChange = { v -> update { copy(tickDegrees = v.roundToInt()) } },
                        valueRange = KeyboardSettings.MIN_TICK_DEGREES.toFloat()..KeyboardSettings.MAX_TICK_DEGREES.toFloat(),
                        steps = KeyboardSettings.MAX_TICK_DEGREES - KeyboardSettings.MIN_TICK_DEGREES - 1,
                    )

                    SwitchRow(stringResource(R.string.setting_haptics), values.haptics) { on -> update { copy(haptics = on) } }
                    SwitchRow(stringResource(R.string.setting_hints), values.hints) { on -> update { copy(hints = on) } }
                    SwitchRow(stringResource(R.string.setting_left), values.leftHanded) { on -> update { copy(leftHanded = on) } }
                    SwitchRow(stringResource(R.string.setting_auto_space), values.autoSpace) { on -> update { copy(autoSpace = on) } }
                    SwitchRow(stringResource(R.string.setting_trail), values.showTrail) { on -> update { copy(showTrail = on) } }

                    OutlinedButton(
                        onClick = {
                            store.requestModelReset()
                            resetDone = true
                        },
                        enabled = !resetDone,
                    ) {
                        Text(stringResource(if (resetDone) R.string.reset_model_done else R.string.reset_model))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.guide_title), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(R.string.guide_body), style = MaterialTheme.typography.bodySmall)
                    LegendRow(stringResource(R.string.legend_vowel_a), DialTables.VOWELS_A)
                    LegendRow(stringResource(R.string.legend_vowel_b), DialTables.VOWELS_B)
                    LegendRow(stringResource(R.string.legend_final), DialTables.FINALS)
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    description: String,
    done: Boolean,
    pendingStatus: String,
    buttonLabel: String,
    buttonWhenDone: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (done) stringResource(R.string.status_done) else pendingStatus,
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!done || buttonWhenDone) {
                OutlinedButton(onClick = onClick) { Text(buttonLabel) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One pulse table as a line: counter-clockwise entries on the left, the zero entry in brackets, clockwise on the right. */
@Composable
private fun LegendRow(title: String, table: Map<Int, Char>) {
    val down = table.keys.filter { it < 0 }.sorted().joinToString(" ") { table.getValue(it).toString() }
    val up = table.keys.filter { it > 0 }.sorted().joinToString(" ") { table.getValue(it).toString() }
    Column {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        Text(text = "↓ $down [${table.getValue(0)}] $up ↑", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isKeyboardSelected(context: Context): Boolean {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    return current.startsWith(context.packageName + "/")
}

private fun openInputMethodSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        // The status line keeps saying the keyboard is off; nothing else to do without the settings screen.
    }
}
