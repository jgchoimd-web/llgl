package com.llgl.vibe.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.vibe.Deps
import com.llgl.vibe.library.MusicLibrary
import com.llgl.vibe.library.Song
import com.llgl.vibe.player.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(deps: Deps, onOpen: (Song) -> Unit, onNowPlaying: () -> Unit, onComposer: () -> Unit, onLive: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(MusicLibrary.hasPermission(context)) }
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    val prefsVersion by deps.prefs.version.collectAsStateWithLifecycle()
    val recents = remember(prefsVersion) { deps.prefs.recents }
    val sessionState by deps.session.state.collectAsStateWithLifecycle()
    val playing by deps.session.playing.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            onOpen(MusicLibrary.fromDocument(context, uri))
        }
    }

    LaunchedEffect(granted) {
        if (granted) songs = withContext(Dispatchers.IO) { MusicLibrary.query(context) }
    }

    val current = deps.session.song
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("진동 뮤직") },
                actions = { TextButton(onClick = onComposer) { Text("🎛 작곡기") } },
            )
        },
        bottomBar = {
            if (current != null && sessionState !is Session.State.Failed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onNowPlaying)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (playing) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (val s = sessionState) {
                                is Session.State.Analyzing -> "${s.step} ${(s.progress * 100).toInt()}%"
                                else -> if (playing) "재생 중 · 탭해서 열기" else "일시 정지 · 탭해서 열기"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("노래를 고르면 비트·베이스·멜로디를 뽑아 진동 모터로 연주합니다.", style = MaterialTheme.typography.bodyMedium)
                        Text(deps.session.engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("진동이 약하면 시스템 설정 › 소리 및 진동 › ‘미디어 진동’을 올리세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(onClick = onLive, modifier = Modifier.fillMaxWidth()) { Text("🎧 실시간 모드 · 다른 앱의 소리를 진동으로") }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f)) { Text("파일에서 열기") }
                    if (!granted) {
                        Button(onClick = { permissionLauncher.launch(MusicLibrary.permission) }, modifier = Modifier.weight(1f)) { Text("내 음악 목록") }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item { Text("최근", style = MaterialTheme.typography.titleMedium) }
                items(recents, key = { "r" + it.key }) { song -> SongRow(song, onClick = { onOpen(song) }) }
            }
            if (granted) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("제목이나 가수 검색") },
                    )
                }
                val list = songs
                if (list == null) {
                    item { Text("음악 목록을 읽는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    val filtered = if (query.isBlank()) list else list.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
                    item { Text("내 음악 ${filtered.size}", style = MaterialTheme.typography.titleMedium) }
                    if (filtered.isEmpty()) {
                        item { Text("음악 파일이 없어요. ‘파일에서 열기’로 하나 골라 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(filtered, key = { it.key }) { song -> SongRow(song, onClick = { onOpen(song) }) }
                }
            } else {
                item {
                    Text(
                        "‘내 음악 목록’을 허용하면 폰에 있는 노래가 여기 나옵니다. 허용하지 않아도 ‘파일에서 열기’로 한 곡씩 고를 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (song.artist.isNotEmpty()) Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (song.durationMs > 0) Text(formatTime(song.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
