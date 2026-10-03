package com.llgl.gameforge.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.llgl.gameforge.Deps
import java.io.File

/** Full-screen WebView with a slim bar: back, restart, errors, ask-for-changes, more. */
@Composable
fun PlayScreen(
    deps: Deps,
    gameId: String,
    onBack: () -> Unit,
    onRevise: (String) -> Unit,
    onFix: (List<String>) -> Unit,
    onCode: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val game = remember(gameId, version) { deps.store.meta(gameId) }
    val html = remember(gameId, version) { deps.store.html(gameId) ?: MISSING }
    val errors = remember { mutableStateListOf<String>() }
    var reloadKey by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<GameWebView?>(null) }
    var showRevise by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reviseText by rememberSaveable { mutableStateOf("") }
    val smallContext = remember { deps.settings.selectedModel?.let { deps.settings.contextFor(it) <= 2048 } ?: false }

    BackHandler(onBack = onBack)
    KeepScreenOn()
    HideSystemBars()
    LifecycleResumeEffect(webView) {
        webView?.onResume()
        onPauseOrDispose { webView?.onPause() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text(
                game?.title ?: "게임",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = {
                errors.clear()
                reloadKey++
            }) { Icon(Icons.Default.Refresh, contentDescription = "다시 시작") }
            BadgedBox(badge = { if (errors.isNotEmpty()) Badge { Text("${errors.size}") } }) {
                IconButton(onClick = { showErrors = true }) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "오류",
                        tint = if (errors.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { showRevise = true }) { Icon(Icons.Default.Edit, contentDescription = "수정 요청") }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "더보기") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("코드 직접 편집") }, onClick = {
                        showMenu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("코드 보기") }, onClick = {
                        showMenu = false
                        onCode()
                    })
                    DropdownMenuItem(text = { Text("HTML 파일 공유") }, onClick = {
                        showMenu = false
                        share(context, game?.title ?: "game", html)
                    })
                    if (deps.store.hasHistory(gameId)) {
                        DropdownMenuItem(text = { Text("이전 버전으로 되돌리기") }, onClick = {
                            showMenu = false
                            if (deps.store.revert(gameId)) {
                                errors.clear()
                                version++
                            }
                        })
                    }
                    DropdownMenuItem(text = { Text("삭제") }, onClick = {
                        showMenu = false
                        confirmDelete = true
                    })
                }
            }
        }
        AndroidView(
            factory = { ctx ->
                GameWebView(ctx) { message ->
                    if (!errors.contains(message) && errors.size < 30) errors.add(message)
                }.also { webView = it }
            },
            update = { view -> view.show(html, version * 100_000 + reloadKey) },
            onRelease = { view ->
                if (webView === view) webView = null
                view.destroy()
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }

    if (showRevise) {
        AlertDialog(
            onDismissRequest = { showRevise = false },
            title = { Text("무엇을 바꿀까요?") },
            text = {
                Column {
                    OutlinedTextField(
                        value = reviseText,
                        onValueChange = { reviseText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("예: 공을 더 빠르게, 목숨 3개 추가, 배경을 우주로") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    if (smallContext) {
                        Text(
                            "지금 모델은 컨텍스트가 작아서 수정 요청이 잘 안 될 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reviseText.isNotBlank(),
                    onClick = {
                        showRevise = false
                        val request = reviseText.trim()
                        reviseText = ""
                        onRevise(request)
                    },
                ) { Text("AI에게 요청") }
            },
            dismissButton = { TextButton(onClick = { showRevise = false }) { Text("취소") } },
        )
    }

    if (showErrors) {
        AlertDialog(
            onDismissRequest = { showErrors = false },
            title = { Text(if (errors.isEmpty()) "오류 없음" else "자바스크립트 오류 ${errors.size}개") },
            text = {
                if (errors.isEmpty()) {
                    Text("이 게임은 지금까지 오류 없이 돌아가고 있어요. 뭔가 안 움직이면 ‘수정 요청’으로 말하거나 코드를 직접 고쳐 보세요.")
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        errors.forEach { e ->
                            Text("• $e", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        if (smallContext) {
                            Text(
                                "지금 모델은 컨텍스트가 작아서 고치기 요청이 잘 안 될 수 있어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (errors.isNotEmpty()) {
                    TextButton(onClick = {
                        showErrors = false
                        onFix(errors.toList())
                    }) { Text("AI에게 고쳐 달라고 하기") }
                }
            },
            dismissButton = { TextButton(onClick = { showErrors = false }) { Text("닫기") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제할까요?") },
            text = { Text("“${game?.title ?: "게임"}”을(를) 지웁니다. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deps.store.delete(gameId)
                    onDeleted()
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

private const val MISSING = "<!DOCTYPE html><html><body style=\"background:#000;color:#fff;font-family:sans-serif;padding:24px\">" +
    "<h2>게임 파일이 없어요</h2><p>삭제되었거나 저장에 실패했습니다.</p></body></html>"

/** Writes the game to the cache and offers it to other apps as an .html file. */
internal fun share(context: Context, title: String, html: String) {
    try {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val safe = title.replace(Regex("[^0-9A-Za-z가-힣._ -]"), "_").trim().ifEmpty { "game" }
        val file = File(dir, "$safe.html")
        file.writeText(html, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "게임 공유"))
    } catch (_: Exception) {
        Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show()
    }
}
