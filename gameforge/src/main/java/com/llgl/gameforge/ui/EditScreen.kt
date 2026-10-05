package com.llgl.gameforge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llgl.gameforge.Deps
import com.llgl.gameforge.harness.JsDecls
import com.llgl.gameforge.harness.Shell
import kotlinx.coroutines.launch

/** A plain monospace editor for the game script: jump to a declaration, read the engine API, save and run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(deps: Deps, gameId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val game = remember(gameId) { deps.store.meta(gameId) }
    val original = remember(gameId) { deps.store.gameJs(gameId) ?: "" }
    var value by remember(gameId) { mutableStateOf(TextFieldValue(original)) }
    var showApi by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dirty = value.text != original
    val decls = remember(value.text) { JsDecls.parse(value.text) }
    val cursorLine = remember(value.text, value.selection) {
        value.text.take(value.selection.start.coerceIn(0, value.text.length)).count { it == '\n' } + 1
    }

    val leave = { if (dirty) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    fun save() {
        deps.store.update(gameId, value.text, "직접 편집", 0, 0, "직접 편집", Shell.titleFrom(value.text))
        onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(game?.title ?: "코드", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "줄 $cursorLine · ${value.text.lines().size}줄 · 선언 ${decls.count { it.name != null }}개${if (dirty) " · 수정됨" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showJump = true }) { Text("함수 ▾") }
                        DropdownMenu(expanded = showJump, onDismissRequest = { showJump = false }) {
                            val named = decls.filter { it.name != null }
                            if (named.isEmpty()) DropdownMenuItem(text = { Text("선언이 없어요") }, onClick = { showJump = false })
                            named.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d.header.take(40), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                    onClick = {
                                        showJump = false
                                        val offset = value.text.lineSequence().take(d.startLine - 1).sumOf { it.length + 1 }
                                        value = value.copy(selection = TextRange(offset.coerceIn(0, value.text.length)))
                                        val lineHeightPx = with(density) { 17.sp.toPx() }
                                        scope.launch { scroll.animateScrollTo(((d.startLine - 2).coerceAtLeast(0) * lineHeightPx).toInt()) }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showApi = true }) { Icon(Icons.Default.Info, contentDescription = "엔진 API") }
                    Button(onClick = { save() }, enabled = dirty, modifier = Modifier.padding(end = 8.dp)) { Text("저장·실행") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFE2E6F0),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
        }
    }

    if (showApi) {
        AlertDialog(
            onDismissRequest = { showApi = false },
            title = { Text("엔진이 주는 것") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(Shell.apiGuideKo, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showApi = false }) { Text("닫기") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("저장하지 않은 변경이 있어요") },
            text = { Text("나가면 지금까지 고친 내용이 사라집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    save()
                }) { Text("저장하고 실행") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onBack()
                }) { Text("버리고 나가기") }
            },
        )
    }
}
