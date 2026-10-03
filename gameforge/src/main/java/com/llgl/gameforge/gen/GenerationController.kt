package com.llgl.gameforge.gen

import android.content.Context
import com.llgl.gameforge.Settings
import com.llgl.gameforge.games.GameStore
import com.llgl.gameforge.harness.CodeMerge
import com.llgl.gameforge.harness.ContextBuilder
import com.llgl.gameforge.harness.JsDecls
import com.llgl.gameforge.harness.ReplyParser
import com.llgl.gameforge.harness.Shell
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
 * The harness around the model. A new game is written as declarations for the engine template;
 * a change or a fix is asked for as the declarations that change, against the part of the script
 * that matters (with up to two `READ` rounds for more), and merged by name. The UI only watches
 * the flows.
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
        /** 1 for the first request; a `READ` round-trip adds one. */
        val round: Int = 1,
    )

    sealed interface Outcome {
        data class Saved(val gameId: String, val created: Boolean, val summary: String) : Outcome
        data class NoCode(val raw: String) : Outcome
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

    /** Everything one run needs to remember between steps. */
    private inner class Run(val job: Job, val maxTokens: Int, val modelName: String) {
        val startedAt = System.currentTimeMillis()
        var totalTokens = 0
        var progress = Progress(job, Phase.LOADING_MODEL, "", 0, startedAt, 0, maxTokens, modelName)
            set(value) {
                field = value
                _progress.value = value
            }

        /** Streams one reply, keeping [progress] current, and returns the full text. */
        suspend fun generate(prompt: String, promptTokens: Int, round: Int): String {
            progress = progress.copy(phase = Phase.GENERATING, text = "", tokens = 0, promptTokens = promptTokens.coerceAtLeast(0), round = round)
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
            totalTokens += tokens
            progress = progress.copy(phase = Phase.SAVING, text = raw, tokens = tokens)
            return raw
        }

        val duration: Long get() = System.currentTimeMillis() - startedAt
    }

    private suspend fun run(job: Job) {
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
        val run = Run(job, maxTokens, ModelCatalog.byFile(fileName)?.name ?: fileName)
        run.progress = run.progress
        try {
            Engine.load(context, file, Engine.backendOf(settings.backend), maxTokens)
            when (job) {
                is Job.Create -> create(run, job)
                is Job.Revise, is Job.Fix -> edit(run, job)
            }
        } catch (e: CancellationException) {
            _outcome.value = Outcome.Cancelled
            throw e
        } catch (t: Throwable) {
            _outcome.value = Outcome.Failed(t.message ?: t.javaClass.simpleName)
        } finally {
            _progress.value = null
        }
    }

    private suspend fun create(run: Run, job: Job.Create) {
        val prompt = Prompts.create(job.idea, Prompts.linesFor(run.maxTokens))
        val promptTokens = countTokens(prompt)
        if (promptTokens > 0 && run.maxTokens - promptTokens < MIN_REPLY_TOKENS) {
            _outcome.value = Outcome.Failed("요청이 이 모델의 컨텍스트($run.maxTokens 토큰)에 비해 너무 길어요. 아이디어를 짧게 적어 보세요.")
            return
        }
        val raw = run.generate(prompt, promptTokens, round = 1)
        val reply = ReplyParser.parse(raw)
        if (reply.decls.isEmpty()) {
            _outcome.value = Outcome.NoCode(raw)
            return
        }
        val merged = CodeMerge.apply("", reply, Shell.engineNames)
        val title = Shell.titleFrom(merged.code) ?: clipTitle(job.idea)
        val game = store.create(title, job.idea, Shell.template, merged.code, run.modelName, run.totalTokens, run.duration)
        _outcome.value = Outcome.Saved(game.id, created = true, summary = merged.summary())
    }

    private suspend fun edit(run: Run, job: Job) {
        val id = job.gameId ?: return
        val base = store.gameJs(id) ?: throw IllegalStateException("게임 코드를 찾을 수 없어요")
        val shell = store.shell(id)
        val decls = JsDecls.parse(base)
        val errors = (job as? Job.Fix)?.errors ?: emptyList()
        val request = (job as? Job.Revise)?.request ?: ""
        val errorLines = ContextBuilder.errorLines(errors) { Shell.htmlLineToGameLine(shell, it) }
        val errorNames = ContextBuilder.errorNames(errors, decls)
        val reads = mutableListOf<String>()
        var budgetChars = contextBudgetChars(run.maxTokens)
        var round = 1
        while (true) {
            val selection = ContextBuilder.select(decls, budgetChars, request, errorLines, errorNames, reads)
            val allowRead = round < MAX_ROUNDS && !selection.whole
            val prompt = when (job) {
                is Job.Fix -> Prompts.fix(errors, selection.code(), selection.map(), allowRead)
                else -> Prompts.edit(request, selection.code(), selection.map(), allowRead)
            }
            val promptTokens = countTokens(prompt)
            if (promptTokens > 0 && run.maxTokens - promptTokens < MIN_REPLY_TOKENS) {
                // Too much context for this model: show less of the script and try again.
                budgetChars /= 2
                if (budgetChars < MIN_BUDGET_CHARS) {
                    _outcome.value = Outcome.Failed(
                        "이 모델의 컨텍스트(${run.maxTokens} 토큰)로는 이 게임을 고칠 만큼 코드를 보여 줄 수 없어요. 컨텍스트가 큰 모델을 쓰거나 코드를 직접 편집해 보세요.",
                    )
                    return
                }
                continue
            }
            val raw = run.generate(prompt, promptTokens, round)
            val reply = ReplyParser.parse(raw)
            if (reply.isEmpty && reply.reads.isNotEmpty() && round < MAX_ROUNDS) {
                reads += reply.reads
                round++
                continue
            }
            if (reply.isEmpty) {
                _outcome.value = Outcome.NoCode(raw)
                return
            }
            val merged = CodeMerge.apply(base, reply, Shell.engineNames)
            if (!merged.changed) {
                _outcome.value = Outcome.NoCode(raw)
                return
            }
            val change = when (job) {
                is Job.Revise -> job.request.trim().take(50)
                is Job.Fix -> "오류 수정"
                else -> ""
            } + " · " + merged.summary()
            store.update(id, merged.code, run.modelName, run.totalTokens, run.duration, change, Shell.titleFrom(merged.code))
            _outcome.value = Outcome.Saved(id, created = false, summary = merged.summary())
            return
        }
    }

    private suspend fun countTokens(prompt: String): Int = withContext(Engine.dispatcher) { Engine.countTokens(prompt) }

    /** Characters of game script we can show: context minus the reply we expect minus the fixed parts of the prompt. */
    private fun contextBudgetChars(maxTokens: Int): Int {
        val replyReserve = if (maxTokens <= 2048) 700 else 1000
        return ((maxTokens - replyReserve - PROMPT_OVERHEAD_TOKENS) * CHARS_PER_TOKEN).coerceAtLeast(MIN_BUDGET_CHARS)
    }

    private fun clipTitle(idea: String): String {
        val one = idea.replace(Regex("\\s+"), " ").trim()
        return when {
            one.isEmpty() -> "이름 없는 게임"
            one.length > 40 -> one.substring(0, 40).trimEnd() + "…"
            else -> one
        }
    }

    private companion object {
        const val MIN_REPLY_TOKENS = 400
        const val EMIT_EVERY_MS = 80L
        const val MAX_ROUNDS = 3
        const val PROMPT_OVERHEAD_TOKENS = 600
        const val CHARS_PER_TOKEN = 3
        const val MIN_BUDGET_CHARS = 500
    }
}
