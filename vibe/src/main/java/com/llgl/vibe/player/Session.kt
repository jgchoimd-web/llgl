package com.llgl.vibe.player

import android.content.Context
import com.llgl.vibe.AnalysisCache
import com.llgl.vibe.Prefs
import com.llgl.vibe.analysis.Analyzer
import com.llgl.vibe.analysis.HapticTrack
import com.llgl.vibe.audio.Decoder
import com.llgl.vibe.haptics.Mode
import com.llgl.vibe.haptics.VibeEngine
import com.llgl.vibe.library.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One song at a time: analysis (cached), audio playback and the vibration engine kept in step.
 * Lives in the Application so a screen can come and go while a song is loaded.
 */
class Session(private val context: Context, val prefs: Prefs, private val cache: AnalysisCache) {
    sealed interface State {
        data object Empty : State
        data class Analyzing(val song: Song, val progress: Float, val step: String) : State
        data class Ready(val song: Song, val track: HapticTrack) : State
        data class Failed(val song: Song, val message: String) : State
    }

    val engine = VibeEngine(context)
    val playback = Playback(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Empty)
    val state: StateFlow<State> = _state

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    init {
        engine.mode = prefs.mode
        engine.intensity = prefs.intensity
        playback.muted = prefs.muted
        playback.onCompletion = {
            engine.stop()
            _playing.value = false
        }
    }

    val song: Song? get() = (_state.value as? State.Ready)?.song ?: (_state.value as? State.Analyzing)?.song ?: (_state.value as? State.Failed)?.song

    /** Loads [song]: cached analysis if any, else decode + analyse in the background. */
    fun open(song: Song, reanalyze: Boolean = false) {
        if (song.key == this.song?.key && !reanalyze && _state.value is State.Ready) return
        stop()
        job?.cancel()
        _state.value = State.Analyzing(song, 0f, "준비 중")
        job = scope.launch {
            try {
                val cached = if (reanalyze) null else withContext(Dispatchers.IO) { cache.get(song.key) }
                val track = cached ?: withContext(Dispatchers.Default) {
                    val pcm = Decoder.decode(context, song.uri, { p -> _state.value = State.Analyzing(song, p * 0.5f, "디코딩") })
                    val t = Analyzer.analyze(pcm.samples, pcm.sampleRate) { p -> _state.value = State.Analyzing(song, 0.5f + p * 0.5f, "분석") }
                    cache.put(song.key, t)
                    t
                }
                val ok = withContext(Dispatchers.IO) { playback.load(song.uri) }
                if (!ok) {
                    _state.value = State.Failed(song, "이 파일은 재생할 수 없어요")
                    return@launch
                }
                prefs.remember(song)
                _state.value = State.Ready(song, track)
            } catch (e: Exception) {
                _state.value = State.Failed(song, e.message ?: "분석에 실패했어요")
            }
        }
    }

    val ready: State.Ready? get() = _state.value as? State.Ready

    fun togglePlay() {
        if (_playing.value) pause() else play()
    }

    fun play() {
        val r = ready ?: return
        playback.play()
        engine.play(r.track) { playback.position }
        _playing.value = true
    }

    fun pause() {
        playback.pause()
        engine.stop()
        _playing.value = false
    }

    fun seekTo(ms: Int) {
        playback.seekTo(ms)
        if (_playing.value) engine.resync()
    }

    fun setMode(mode: Mode) {
        engine.mode = mode
        prefs.mode = mode
        if (_playing.value) engine.resync()
    }

    fun setIntensity(v: Float) {
        engine.intensity = v
        prefs.intensity = v
        if (_playing.value) engine.resync()
    }

    fun setMuted(m: Boolean) {
        playback.muted = m
        prefs.muted = m
    }

    /** Stops sound and motor and forgets the song. */
    fun stop() {
        engine.stop()
        playback.pause()
        _playing.value = false
    }

    fun close() {
        job?.cancel()
        stop()
        playback.release()
        _state.value = State.Empty
    }
}
