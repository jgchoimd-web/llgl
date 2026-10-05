package com.llgl.gameforge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.gameforge.Deps
import com.llgl.gameforge.games.Game
import com.llgl.gameforge.gen.Prompts
import com.llgl.gameforge.model.ModelCatalog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    deps: Deps,
    refresh: Int,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onModel: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val settingsVersion by deps.settings.version.collectAsStateWithLifecycle()
    val selected = remember(settingsVersion) { deps.settings.selectedModel }
    val modelReady = remember(settingsVersion) { selected != null && deps.files.file(selected).exists() }
    val modelName = remember(settingsVersion) {
        when {
            selected == null -> null
            else -> ModelCatalog.byFile(selected)?.name ?: selected.take(18)
        }
    }
    val games = remember(refresh) { deps.store.list() }
    val suggestions = remember { Prompts.suggestions.shuffled().take(6) }
    var idea by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Game?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 게임 공방") },
                actions = {
                    TextButton(onClick = onModel) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                modelName == null -> "모델 받기"
                                !modelReady -> "모델 파일 없음"
                                else -> modelName
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("어떤 게임을 만들까요?", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = idea,
                            onValueChange = { idea = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("예: 공룡이 선인장을 뛰어넘는 게임. 탭하면 점프.") },
                            minLines = 2,
                            maxLines = 5,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            suggestions.forEach { s ->
                                SuggestionChip(onClick = { idea = s }, label = { Text(s) })
                            }
                        }
                        Button(
                            onClick = { onCreate(idea.trim()) },
                            enabled = idea.isNotBlank() && modelReady && !deps.generation.running,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("✨ 만들기")
                        }
                        Text(
                            text = when {
                                !modelReady -> "먼저 오른쪽 위 ‘모델 받기’에서 Gemma 모델을 받아야 해요."
                                else -> "폰 안의 $modelName 이(가) 코드를 씁니다. 인터넷 없이 돌아가고, 몇 분 걸릴 수 있어요."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (modelReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item { Text("내 게임 ${games.size}", style = MaterialTheme.typography.titleMedium) }
            if (games.isEmpty()) {
                item {
                    Text(
                        "아직 없어요. 아이디어를 적고 만들기를 누르면 여기에 쌓입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(games, key = { it.id }) { game ->
                GameRow(game, onClick = { onOpen(game.id) }, onDelete = { confirmDelete = game })
            }
        }
    }

    confirmDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("삭제할까요?") },
            text = { Text("“${game.title}”을(를) 지웁니다. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(game.id)
                    confirmDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun GameRow(game: Game, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    game.prompt.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDate(game.updatedAt)} · ${game.model} · v${game.versions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
        }
    }
}
