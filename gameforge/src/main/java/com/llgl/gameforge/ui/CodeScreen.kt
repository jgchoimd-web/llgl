package com.llgl.gameforge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llgl.gameforge.Deps

/** The game script (what the model and the person edit) or the whole page, read-only, for copying out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(deps: Deps, gameId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val game = remember(gameId) { deps.store.meta(gameId) }
    val script = remember(gameId) { deps.store.gameJs(gameId) ?: "" }
    val html = remember(gameId) { deps.store.html(gameId) ?: "" }
    var wholePage by remember { mutableStateOf(false) }
    val text = if (wholePage) html else script
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${game?.title ?: "게임"} · ${text.lines().size}줄", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(if (wholePage) "index.html" else "game.js", text))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "복사했어요", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("복사") }
                    IconButton(onClick = { share(context, game?.title ?: "game", html) }) {
                        Icon(Icons.Default.Share, contentDescription = "HTML 공유")
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "편집") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = !wholePage, onClick = { wholePage = false }, label = { Text("게임 코드") })
                FilterChip(selected = wholePage, onClick = { wholePage = true }, label = { Text("전체 HTML (엔진 포함)") })
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFFCFD6E4),
                    softWrap = false,
                )
            }
        }
    }
}
