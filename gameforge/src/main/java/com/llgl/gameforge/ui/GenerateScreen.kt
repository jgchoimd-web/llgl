package com.llgl.gameforge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llgl.gameforge.gen.GenerationController
import com.llgl.gameforge.gen.GenerationController.Job
import com.llgl.gameforge.gen.GenerationController.Outcome
import com.llgl.gameforge.gen.GenerationController.Phase
import com.llgl.gameforge.gen.GenerationController.Progress
import kotlinx.coroutines.delay
import java.util.Locale

/** Watches the model write the game, then reports how it went. A finished game is opened by the root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(generation: GenerationController, onLeave: () -> Unit) {
    val progress by generation.progress.collectAsStateWithLifecycle()
    val outcome by generation.outcome.collectAsStateWithLifecycle()
    val running = outcome == null
    var confirmCancel by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    if (running) KeepScreenOn()

    val leaveOrAsk = {
        if (generation.running) confirmCancel = true else onLeave()
    }
    BackHandler(onBack = leaveOrAsk)

    val job = progress?.job ?: generation.lastJob

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (running) "게임 만드는 중" else "결과") },
                navigationIcon = {
                    IconButton(onClick = leaveOrAsk) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(describe(job), style = MaterialTheme.typography.titleMedium)
            when (val o = outcome) {
                null -> RunningBody(progress, now, onCancel = { confirmCancel = true })

                is Outcome.Saved -> Text("완료! ${o.summary.ifBlank { "게임" }} · 여는 중…")

                is Outcome.NoCode -> {
                    Text(
                        "응답에서 바꿀 코드를 찾지 못했어요. 모델이 설명만 했거나 코드가 잘렸을 수 있어요. 아래가 모델이 쓴 전부입니다.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    CodeBox(o.raw, Modifier.weight(1f).fillMaxWidth())
                    RetryRow(onRetry = { generation.retry() }, onLeave = onLeave)
                }

                is Outcome.Failed -> {
                    Text("실패: ${o.message}", color = MaterialTheme.colorScheme.error)
                    Text(
                        "메모리가 부족하다면 더 작은 모델을, 너무 느리다면 모델 화면에서 CPU/GPU를 바꿔 보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    RetryRow(onRetry = { generation.retry() }, onLeave = onLeave)
                }

                Outcome.Cancelled -> {
                    Text("취소했어요.")
                    Spacer(Modifier.weight(1f))
                    RetryRow(onRetry = { generation.retry() }, onLeave = onLeave)
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("생성을 취소할까요?") },
            text = { Text("지금까지 쓴 코드는 버려집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    generation.cancel()
                    onLeave()
                }) { Text("취소하기") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("계속 만들기") } },
        )
    }
}

@Composable
private fun ColumnScope.RunningBody(progress: Progress?, now: Long, onCancel: () -> Unit) {
    val phase = when (progress?.phase) {
        Phase.LOADING_MODEL -> "모델을 메모리에 올리는 중… 처음엔 수십 초 걸릴 수 있어요."
        Phase.GENERATING -> "코드를 쓰는 중. 끝나면 바로 실행됩니다."
        Phase.SAVING -> "저장하는 중…"
        null -> "준비 중…"
    }
    Text(phase, style = MaterialTheme.typography.bodyMedium)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    if (progress != null) {
        val elapsed = (now - progress.startedAt).coerceAtLeast(0)
        val rate = if (elapsed > 1000 && progress.tokens > 0) progress.tokens * 1000f / elapsed else 0f
        val parts = mutableListOf(progress.modelName, formatDuration(elapsed), "${progress.tokens} 토큰")
        if (rate > 0f) parts += String.format(Locale.US, "%.1f 토큰/초", rate)
        if (progress.promptTokens > 0) parts += "컨텍스트 ${progress.promptTokens + progress.tokens}/${progress.maxTokens}"
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    CodeBox(progress?.text ?: "", Modifier.weight(1f).fillMaxWidth(), follow = true)
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("취소") }
}

@Composable
private fun RetryRow(onRetry: () -> Unit, onLeave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("다시 시도") }
        OutlinedButton(onClick = onLeave, modifier = Modifier.weight(1f)) { Text("돌아가기") }
    }
}

private fun describe(job: Job?): String = when (job) {
    is Job.Create -> "“${job.idea}”"
    is Job.Revise -> "수정 요청: ${job.request}"
    is Job.Fix -> "오류 ${job.errors.size}개 고치기"
    null -> ""
}
