package com.llgl.gameforge.ui

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.gameforge.Deps
import com.llgl.gameforge.llm.Engine
import com.llgl.gameforge.model.DownloadTarget
import com.llgl.gameforge.model.Downloader
import com.llgl.gameforge.model.ModelCatalog
import com.llgl.gameforge.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Get a Gemma onto the phone (download, import, custom URL), pick one, tune how it runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(deps: Deps, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsVersion by deps.settings.version.collectAsStateWithLifecycle()
    val download by deps.downloader.state.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    var filesVersion by remember { mutableIntStateOf(0) }
    val installed = remember(filesVersion, download, settingsVersion) { deps.files.installed() }
    val selected = remember(settingsVersion) { deps.settings.selectedModel }
    val backend = remember(settingsVersion) { deps.settings.backend }
    val temperature = remember(settingsVersion) { deps.settings.temperature }
    val manualTemplate = remember(settingsVersion) { deps.settings.manualTemplate }
    var token by remember { mutableStateOf(deps.settings.hfToken) }
    var customUrl by rememberSaveable { mutableStateOf("") }
    var importProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val ramGb = remember { totalRamGb(context) }
    val downloaderActive = deps.downloader.active

    BackHandler(onBack = onBack)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importProgress = 0L to -1L
            try {
                val result = withContext(Dispatchers.IO) {
                    deps.files.import(uri) { done, total -> importProgress = done to total }
                }
                val known = ModelCatalog.bySha(result.sha256)
                deps.settings.setContextFor(result.file.name, known?.contextTokens ?: ModelCatalog.defaultContext(result.file.name))
                deps.settings.selectedModel = result.file.name
                message = if (known != null) {
                    "${known.name} 파일로 확인됐고, 이 모델을 선택했어요."
                } else {
                    "가져왔어요. 처음 보는 파일이라 컨텍스트를 ${ModelCatalog.defaultContext(result.file.name)}으로 두었어요. 불러오기에 실패하면 아래에서 줄여 보세요."
                }
            } catch (e: Exception) {
                message = "가져오기 실패: ${e.message ?: e.javaClass.simpleName}"
            }
            importProgress = null
            filesVersion++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("모델") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
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
                Text(
                    "모델 파일은 한 번만 받으면 됩니다. 그 뒤로는 인터넷 없이 폰 안에서만 돌아갑니다. " +
                        "큰 파일이니 Wi-Fi를 권해요. 이 폰 램: ${ramGb}GB, 남은 공간: ${formatBytes(deps.files.freeBytes())}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message?.let { m ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(m, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { message = null }) { Text("확인") }
                        }
                    }
                }
            }
            item { StatusCard(engine, selected, onUnload = { Engine.unload() }) }
            if (download !is Downloader.State.Idle) {
                item {
                    DownloadCard(download, onCancel = { deps.downloader.cancel() }, onDismiss = {
                        deps.downloader.dismiss()
                        filesVersion++
                    })
                }
            }
            item { Text("추천 모델", style = MaterialTheme.typography.titleMedium) }
            items(ModelCatalog.presets, key = { it.id }) { spec ->
                PresetCard(
                    spec = spec,
                    installed = deps.files.isInstalled(spec),
                    selected = selected == spec.fileName,
                    busy = downloaderActive && download.target?.fileName == spec.fileName,
                    hasToken = token.isNotBlank(),
                    ramGb = ramGb,
                    downloaderActive = downloaderActive,
                    onDownload = { deps.downloader.start(DownloadTarget.of(spec, token.isNotBlank()), token) },
                    onSelect = { deps.settings.selectedModel = spec.fileName },
                    onDelete = {
                        deps.files.delete(spec.fileName)
                        if (selected == spec.fileName) deps.settings.selectedModel = null
                        filesVersion++
                    },
                )
            }
            val others = installed.filter { ModelCatalog.byFile(it.name) == null }
            if (others.isNotEmpty()) {
                item { Text("가져온 파일", style = MaterialTheme.typography.titleMedium) }
                items(others, key = { it.name }) { file ->
                    CustomFileCard(
                        file = file,
                        selected = selected == file.name,
                        contextTokens = deps.settings.contextFor(file.name),
                        onSelect = { deps.settings.selectedModel = file.name },
                        onContext = { deps.settings.setContextFor(file.name, it) },
                        onDelete = {
                            deps.files.delete(file.name)
                            if (selected == file.name) deps.settings.selectedModel = null
                            filesVersion++
                        },
                    )
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hugging Face 토큰", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Google의 공식 파일은 Gemma 약관에 동의한 계정만 받을 수 있어요. huggingface.co에 로그인 → 모델 페이지에서 약관 동의 → " +
                                "Settings › Access Tokens에서 Read 토큰을 만들어 붙이세요. 토큰이 있으면 모든 모델을 공식 저장소에서 받습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                deps.settings.hfToken = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("hf_… 토큰 (선택)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("다른 파일 쓰기", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "MediaPipe LLM 형식(.task)의 Gemma 파일이면 주소로 받거나, 폰에 있는 파일을 가져올 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("https://… .task") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { deps.downloader.start(DownloadTarget.fromUrl(customUrl, token.isNotBlank()), token) },
                                enabled = customUrl.trim().startsWith("https://") && !downloaderActive,
                                modifier = Modifier.weight(1f),
                            ) { Text("주소로 받기") }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                enabled = importProgress == null,
                                modifier = Modifier.weight(1f),
                            ) { Text("파일 가져오기") }
                        }
                        importProgress?.let { (done, total) ->
                            if (total > 0) {
                                LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text("복사 중 ${formatBytes(done)}${if (total > 0) " / ${formatBytes(total)}" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("실행 옵션", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("가속", style = MaterialTheme.typography.bodyMedium)
                            FilterChip(selected = backend == "CPU", onClick = { deps.settings.backend = "CPU" }, label = { Text("CPU") })
                            FilterChip(selected = backend == "GPU", onClick = { deps.settings.backend = "GPU" }, label = { Text("GPU") })
                        }
                        Text(
                            "CPU가 어디서나 돌아갑니다. GPU는 폰에 따라 더 빠르거나, 메모리가 모자라 실패할 수 있어요. 바꾸면 다음 생성 때 모델을 다시 불러옵니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("창의성 ${String.format(Locale.US, "%.1f", temperature)}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = temperature,
                            onValueChange = { deps.settings.temperature = (it * 10).roundToInt() / 10f },
                            valueRange = 0.2f..1.0f,
                            steps = 7,
                        )
                        Text(
                            "낮을수록 코드가 안정적이고, 높을수록 엉뚱한 게임이 나옵니다. 기본 0.6.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Gemma 대화 템플릿 직접 적용", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "모델이 설명만 늘어놓거나 이상한 문자를 내보내면 켜 보세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = manualTemplate, onCheckedChange = { deps.settings.manualTemplate = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(engine: Engine.State, selected: String?, onUnload: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("선택된 모델: ${selected?.let { ModelCatalog.byFile(it)?.name ?: it } ?: "없음"}", style = MaterialTheme.typography.titleMedium)
            val status = when (engine) {
                Engine.State.Idle -> "메모리에 올라와 있지 않음 (첫 생성 때 불러옵니다)"
                is Engine.State.Loading -> "불러오는 중: ${engine.fileName}"
                is Engine.State.Ready -> "준비됨 · ${engine.backend} · 컨텍스트 ${engine.maxTokens} 토큰"
                is Engine.State.Failed -> "불러오기 실패: ${engine.message}"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (engine is Engine.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (engine is Engine.State.Ready) {
                TextButton(onClick = onUnload) { Text("메모리에서 내리기") }
            }
        }
    }
}

@Composable
private fun DownloadCard(state: Downloader.State, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val target = state.target ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(target.name, style = MaterialTheme.typography.titleMedium)
            when (state) {
                is Downloader.State.Resolving -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("내려받을 주소를 확인하는 중…", style = MaterialTheme.typography.bodySmall)
                }

                is Downloader.State.Running -> {
                    if (state.total > 0) {
                        LinearProgressIndicator(progress = { (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "${state.status} · ${formatBytes(state.bytes)}${if (state.total > 0) " / ${formatBytes(state.total)}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onCancel) { Text("취소") }
                }

                is Downloader.State.Verifying -> {
                    LinearProgressIndicator(progress = { state.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("원본과 같은 파일인지 확인하는 중 ${(state.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }

                is Downloader.State.Done -> {
                    Text("완료! 이 모델이 선택됐어요. 이제 게임을 만들 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                is Downloader.State.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                Downloader.State.Idle -> Unit
            }
        }
    }
}

@Composable
private fun PresetCard(
    spec: ModelSpec,
    installed: Boolean,
    selected: Boolean,
    busy: Boolean,
    hasToken: Boolean,
    ramGb: Int,
    downloaderActive: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.name, style = MaterialTheme.typography.titleMedium)
                    Text(spec.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (installed) Icon(Icons.Default.Check, contentDescription = "설치됨", tint = MaterialTheme.colorScheme.secondary)
            }
            Text(
                "똑똑함 ${stars(spec.quality)}   속도 ${stars(spec.speed)}   컨텍스트 ${spec.contextTokens} 토큰",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(spec.note, style = MaterialTheme.typography.bodySmall)
            if (ramGb in 1 until spec.minRamGb) {
                Text("이 폰(램 ${ramGb}GB)에서는 버거울 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    installed -> {
                        if (selected) {
                            AssistChip(onClick = {}, label = { Text("사용 중") }, leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) })
                        } else {
                            Button(onClick = onSelect) { Text("이 모델 사용") }
                        }
                        TextButton(onClick = onDelete) { Text("삭제") }
                    }

                    busy -> Text("받는 중…", style = MaterialTheme.typography.bodyMedium)

                    else -> {
                        val canDownload = (!spec.needsToken || hasToken) && !downloaderActive
                        Button(onClick = onDownload, enabled = canDownload) { Text("다운로드 ${spec.sizeLabel}") }
                        if (spec.needsToken && !hasToken) {
                            Text("토큰 필요 (아래)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (!spec.needsToken && !hasToken) {
                            Text("계정 없이 받기 · 원본과 대조 검증", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFileCard(
    file: File,
    selected: Boolean,
    contextTokens: Int,
    onSelect: () -> Unit,
    onContext: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.name, style = MaterialTheme.typography.titleMedium)
            Text(formatBytes(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("컨텍스트", style = MaterialTheme.typography.bodySmall)
                for (size in listOf(1024, 2048, 4096, 8192)) {
                    FilterChip(selected = contextTokens == size, onClick = { onContext(size) }, label = { Text("$size") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    AssistChip(onClick = {}, label = { Text("사용 중") }, leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) })
                } else {
                    Button(onClick = onSelect) { Text("이 모델 사용") }
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDelete) { Text("삭제") }
            }
        }
    }
}

private fun stars(n: Int): String = "★".repeat(n.coerceIn(0, 3)) + "☆".repeat(3 - n.coerceIn(0, 3))

private fun totalRamGb(context: Context): Int {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return ceil(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
}
