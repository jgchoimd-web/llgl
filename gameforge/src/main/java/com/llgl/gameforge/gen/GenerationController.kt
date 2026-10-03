package com.llgl.gameforge.gen

import android.content.Context
import com.llgl.gameforge.Settings
import com.llgl.gameforge.games.GameStore
import com.llgl.gameforge.llm.Engine
import com.llgl.gameforge.model.ModelCatalog
import com.llgl.gameforge.model.ModelFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one generation at a time: loads the chosen model, builds the prompt, streams the reply
 * into [progress], then extracts the HTML and saves the game. The UI only watches the flows.
 */
class GenerationController(
    private val context: Context,
    private val settings: Settings,
    private val files: ModelFiles,
    private val store: GameStore,
) {
    sealed interface Job {
        val gameId: String?

        data class Create(val idea: String) : Job {
            override val gameId: String? get() = null
        }

        data class Revise(override val gameId: String, val request: String) : Job
        data class Fix(override val gameId: String, val errors: List<String>) : Job
    }

    enum class Phase { LOADING_MODEL, GENERATING, SAVING }

    data class Progress(
        val job: Job,
        val phase: Phase,
        val text: String,
        val tokens: Int,
        val startedAt: Long,
        val promptTokens: Int,
        val maxTokens: Int,
        val modelName: String,
    )

    sealed interface Outcome {
        data class Saved(val gameId: String, val created: Boolean) : Outcome
        data class NoHtml(val raw: String) : Outcome
        data class Failed(val message: String) : Outcome
        data object Cancelled : Outcome
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current: kotlinx.coroutines.Job? = null

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    private val _outcome = MutableStateFlow<Outcome?>(null)
    val outcome: StateFlow<Outcome?> = _outcome

    /** The last job started, so a failed one can be retried. */
    var lastJob: Job? = null
        private set

    val running: Boolean get() = current?.isActive == true

    fun start(job: Job) {
        if (running) return
        lastJob = job
        _outcome.value = null
        current = scope.launch { run(job) }
    }

    fun retry() {
        lastJob?.let { start(it) }
    }

    fun cancel() {
        Engine.cancel()
        current?.cancel()
    }

    fun clearOutcome() {
        _outcome.value = null
    }

    private suspend fun run(job: Job) {
        val startedAt = System.currentTimeMillis()
        val fileName = settings.selectedModel
        if (fileName == null) {
            _outcome.value = Outcome.Failed("먼저 모델 화면에서 모델을 받아 선택하세요.")
            return
        }
        val file = files.file(fileName)
        if (!file.exists()) {
            _outcome.value = Outcome.Failed("모델 파일이 없어요: $fileName")
            return
        }
        val maxTokens = settings.contextFor(fileName)
        val modelName = ModelCatalog.byFile(fileName)?.name ?: fileName
        var progress = Progress(job, Phase.LOADING_MODEL, "", 0, startedAt, 0, maxTokens, modelName)
        _progress.value = progress
        try {
            Engine.load(context, file, Engine.backendOf(settings.backend), maxTokens)

            val baseHtml = job.gameId?.let { store.html(it) }
            val prompt = when (job) {
                is Job.Create -> Prompts.create(job.idea, Prompts.linesFor(maxTokens))
                is Job.Revise -> Prompts.revise(baseHtml ?: throw IllegalStateException("게임 파일이 없어요"), job.request)
                is Job.Fix -> Prompts.fix(baseHtml ?: throw IllegalStateException("게임 파일이 없어요"), job.errors)
            }
            val promptTokens = withContext(Engine.dispatcher) { Engine.countTokens(prompt) }
            if (promptTokens > 0 && maxTokens - promptTokens < MIN_REPLY_TOKENS) {
                _outcome.value = Outcome.Failed(
                    "요청이 이 모델의 컨텍스트($maxTokens 토큰)에 비해 너무 길어요(프롬프트 $promptTokens 토큰). " +
                        "컨텍스트가 큰 모델을 쓰거나 더 짧은 게임으로 시도해 보세요.",
                )
                return
            }
            progress = progress.copy(phase = Phase.GENERATING, promptTokens = promptTokens.coerceAtLeast(0))
            _progress.value = progress

            val text = StringBuilder()
            var tokens = 0
            var lastEmit = 0L
            val raw = Engine.generate(prompt, settings.temperature, settings.manualTemplate) { delta ->
                val snapshot: String
                val count: Int
                synchronized(text) {
                    text.append(delta)
                    tokens++
                    count = tokens
                    val now = System.currentTimeMillis()
                    snapshot = if (now - lastEmit >= EMIT_EVERY_MS) {
                        lastEmit = now
                        text.toString()
                    } else {
                        ""
                    }
                }
                if (snapshot.isNotEmpty()) _progress.value = progress.copy(text = snapshot, tokens = count)
            }
            progress = progress.copy(phase = Phase.SAVING, text = raw, tokens = tokens)
            _progress.value = progress

            val fallbackTitle = when (job) {
                is Job.Create -> job.idea
                else -> store.meta(job.gameId!!)?.title ?: "게임"
            }
            val extracted = HtmlExtractor.extract(raw, fallbackTitle)
            if (extracted == null) {
                _outcome.value = Outcome.NoHtml(raw)
                return
            }
            val html = HtmlExtractor.prepare(extracted.html)
            val duration = System.currentTimeMillis() - startedAt
            val game = when (job) {
                is Job.Create -> store.create(extracted.title, job.idea, html, modelName, tokens, duration)
                is Job.Revise -> store.update(job.gameId, html, modelName, tokens, duration, job.request.trim().take(60))
                is Job.Fix -> store.update(job.gameId, html, modelName, tokens, duration, "오류 수정")
            }
            _outcome.value = Outcome.Saved(game.id, job is Job.Create)
        } catch (e: CancellationException) {
            _outcome.value = Outcome.Cancelled
            throw e
        } catch (t: Throwable) {
            _outcome.value = Outcome.Failed(t.message ?: t.javaClass.simpleName)
        } finally {
            _progress.value = null
        }
    }

    private companion object {
        const val MIN_REPLY_TOKENS = 400
        const val EMIT_EVERY_MS = 80L
    }
}
