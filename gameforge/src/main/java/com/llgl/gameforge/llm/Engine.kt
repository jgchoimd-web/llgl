package com.llgl.gameforge.llm

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * The one place that talks to MediaPipe's LLM Inference API. A single thread owns the model, so
 * loading, generating and closing never overlap; callers suspend on [dispatcher].
 */
object Engine {
    sealed interface State {
        data object Idle : State
        data class Loading(val fileName: String) : State
        data class Ready(val fileName: String, val backend: String, val maxTokens: Int) : State
        data class Failed(val fileName: String, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "gameforge-llm") }
    val dispatcher = executor.asCoroutineDispatcher()

    private var llm: LlmInference? = null
    private var loadedKey: String? = null

    @Volatile
    private var session: LlmInferenceSession? = null

    /** True while a reply is being generated. */
    @Volatile
    var busy: Boolean = false
        private set

    fun backendOf(name: String): LlmInference.Backend =
        if (name.equals("GPU", ignoreCase = true)) LlmInference.Backend.GPU else LlmInference.Backend.CPU

    /** Loads [file] unless the same file with the same options is already in memory. Slow: seconds to a minute. */
    suspend fun load(context: Context, file: File, backend: LlmInference.Backend, maxTokens: Int) = withContext(dispatcher) {
        val key = "${file.absolutePath}|$backend|$maxTokens"
        if (llm != null && loadedKey == key) return@withContext
        closeAll()
        _state.value = State.Loading(file.name)
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(maxTokens)
                .setMaxTopK(64)
                .setPreferredBackend(backend)
                .build()
            llm = LlmInference.createFromOptions(context.applicationContext, options)
            loadedKey = key
            _state.value = State.Ready(file.name, backend.name, maxTokens)
        } catch (t: Throwable) {
            llm = null
            loadedKey = null
            _state.value = State.Failed(file.name, describe(t))
            throw IllegalStateException("모델을 불러오지 못했어요: ${describe(t)}", t)
        }
    }

    /** Frees the model's memory; the next generation loads it again. */
    fun unload() {
        executor.execute {
            closeAll()
            _state.value = State.Idle
        }
    }

    /** Token count of [text] for the loaded model, or -1 when nothing is loaded. Call on [dispatcher]. */
    fun countTokens(text: String): Int = try {
        llm?.sizeInTokens(text) ?: -1
    } catch (_: Throwable) {
        -1
    }

    /**
     * Streams a reply to [prompt]; [onDelta] receives each new piece on a background thread.
     * Returns the full reply. Cancelling the coroutine stops generation.
     */
    suspend fun generate(prompt: String, temperature: Float, manualTemplate: Boolean, onDelta: (String) -> Unit): String =
        withContext(dispatcher) {
            val engine = llm ?: throw IllegalStateException("모델이 메모리에 없어요")
            val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.95f)
                .setTemperature(temperature)
                .setRandomSeed(Random.nextInt(1, Int.MAX_VALUE))
            if (manualTemplate) {
                options.setPromptTemplates(
                    PromptTemplates.builder()
                        .setUserPrefix("<start_of_turn>user\n")
                        .setUserSuffix("<end_of_turn>\n")
                        .setModelPrefix("<start_of_turn>model\n")
                        .setModelSuffix("<end_of_turn>\n")
                        .build(),
                )
            }
            val s = LlmInferenceSession.createFromOptions(engine, options.build())
            session = s
            busy = true
            var future: ListenableFuture<String>? = null
            try {
                s.addQueryChunk(prompt)
                val text = StringBuilder()
                suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    val f = s.generateResponseAsync { partial, done ->
                        if (!partial.isNullOrEmpty()) {
                            synchronized(text) { text.append(partial) }
                            onDelta(partial)
                        }
                        if (done && finished.compareAndSet(false, true)) {
                            cont.resume(synchronized(text) { text.toString() })
                        }
                    }
                    future = f
                    f.addListener({
                        if (finished.compareAndSet(false, true)) {
                            try {
                                f.get()
                                cont.resume(synchronized(text) { text.toString() })
                            } catch (t: Throwable) {
                                cont.resumeWithException(t.cause ?: t)
                            }
                        }
                    }, MoreExecutors.directExecutor())
                    cont.invokeOnCancellation {
                        runCatching { s.cancelGenerateResponseAsync() }
                    }
                }
            } finally {
                // Give the native side a moment to wind down before the session goes away.
                runCatching { future?.get(3, TimeUnit.SECONDS) }
                busy = false
                session = null
                runCatching { s.close() }
            }
        }

    /** Asks the running generation to stop; the suspended [generate] call then returns. */
    fun cancel() {
        runCatching { session?.cancelGenerateResponseAsync() }
    }

    private fun closeAll() {
        runCatching { session?.close() }
        session = null
        runCatching { llm?.close() }
        llm = null
        loadedKey = null
    }

    private fun describe(t: Throwable): String {
        val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        return if (t is OutOfMemoryError || message.contains("memory", ignoreCase = true) || message.contains("alloc", ignoreCase = true)) {
            "메모리가 부족해요. 더 작은 모델을 고르거나 다른 앱을 닫아 보세요. ($message)"
        } else {
            message
        }
    }
}
