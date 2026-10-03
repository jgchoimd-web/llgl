package com.llgl.gameforge.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.llgl.gameforge.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** One file to fetch. [usesToken] means the URL is gated and must be resolved with the Hugging Face token first. */
data class DownloadTarget(
    val name: String,
    val fileName: String,
    val url: String,
    val sha256: String?,
    val contextTokens: Int,
    val specId: String?,
    val usesToken: Boolean,
) {
    companion object {
        fun of(spec: ModelSpec, hasToken: Boolean): DownloadTarget {
            val official = hasToken || spec.mirrorUrl == null
            return DownloadTarget(
                name = spec.name,
                fileName = spec.fileName,
                url = if (official) spec.officialUrl else spec.mirrorUrl!!,
                sha256 = spec.sha256,
                contextTokens = spec.contextTokens,
                specId = spec.id,
                usesToken = official,
            )
        }

        fun fromUrl(url: String, hasToken: Boolean): DownloadTarget {
            val trimmed = url.trim()
            val lastSegment = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
            val decoded = try {
                URLDecoder.decode(lastSegment, "UTF-8")
            } catch (_: Exception) {
                lastSegment
            }
            val fileName = decoded.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "model.task" }
            return DownloadTarget(
                name = fileName,
                fileName = fileName,
                url = trimmed,
                sha256 = null,
                contextTokens = ModelCatalog.defaultContext(fileName),
                specId = null,
                usesToken = hasToken && trimmed.startsWith("https://huggingface.co/"),
            )
        }
    }
}

/**
 * Fetches model bundles with the system DownloadManager (resumable, survives the app being
 * killed, shows a notification), then checks the SHA-256 before the file counts as installed.
 *
 * Gated Hugging Face files are resolved first: one request with the token, without following
 * redirects, gives the signed CDN URL that DownloadManager can fetch with no header at all.
 * (DownloadManager would otherwise forward the Authorization header to the CDN, which rejects it.)
 */
class Downloader(private val context: Context, private val settings: Settings, private val files: ModelFiles) {

    sealed interface State {
        val target: DownloadTarget?

        data object Idle : State {
            override val target: DownloadTarget? get() = null
        }

        data class Resolving(override val target: DownloadTarget) : State
        data class Running(override val target: DownloadTarget, val bytes: Long, val total: Long, val status: String) : State
        data class Verifying(override val target: DownloadTarget, val fraction: Float) : State
        data class Done(override val target: DownloadTarget) : State
        data class Failed(override val target: DownloadTarget, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tracking: Job? = null

    private val manager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val active: Boolean
        get() = _state.value.let { it is State.Resolving || it is State.Running || it is State.Verifying }

    fun start(target: DownloadTarget, token: String) {
        if (active) return
        _state.value = State.Resolving(target)
        tracking = scope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    if (target.usesToken) resolveWithToken(target.url, token) else target.url
                }
                val part = files.file(target.fileName + PART)
                if (part.exists()) part.delete()
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(target.name)
                    .setDescription("AI 게임 공방 모델 파일")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setMimeType("application/octet-stream")
                    .setDestinationInExternalFilesDir(context, ModelFiles.DIR, target.fileName + PART)
                val id = manager.enqueue(request)
                settings.saveDownload(id, target)
                track(id, target)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = State.Failed(target, t.message ?: "다운로드를 시작할 수 없어요")
            }
        }
    }

    /** Called at app start: picks up a download that was running when the process died. */
    fun resumeTracking() {
        val id = settings.downloadId
        val target = settings.downloadTarget() ?: return
        if (id < 0) return
        _state.value = State.Running(target, 0, -1, "확인 중")
        tracking = scope.launch { track(id, target) }
    }

    fun cancel() {
        val id = settings.downloadId
        tracking?.cancel()
        if (id >= 0) runCatching { manager.remove(id) }
        settings.clearDownload()
        _state.value = State.Idle
    }

    /** Clears a finished or failed card. */
    fun dismiss() {
        if (!active) _state.value = State.Idle
    }

    private suspend fun track(id: Long, target: DownloadTarget) {
        while (true) {
            val snap = withContext(Dispatchers.IO) { query(id) }
            if (snap == null) {
                settings.clearDownload()
                _state.value = State.Failed(target, "다운로드가 사라졌어요(알림에서 취소됐을 수 있어요)")
                return
            }
            when (snap.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    finish(id, target, snap.localPath)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    settings.clearDownload()
                    runCatching { manager.remove(id) }
                    _state.value = State.Failed(target, failReason(snap.reason))
                    return
                }
                else -> _state.value = State.Running(target, snap.bytes, snap.total, statusLabel(snap.status, snap.reason))
            }
            delay(700)
        }
    }

    private suspend fun finish(id: Long, target: DownloadTarget, localPath: String?) {
        val part = localPath?.let { File(it) }?.takeIf { it.exists() } ?: files.file(target.fileName + PART)
        _state.value = State.Verifying(target, 0f)
        val ok = withContext(Dispatchers.IO) {
            val expected = target.sha256 ?: return@withContext part.exists()
            val total = part.length().coerceAtLeast(1L)
            val sha = part.inputStream().buffered(1 shl 20).use { input ->
                Sha256.of(input) { done -> _state.value = State.Verifying(target, done.toFloat() / total) }
            }
            sha.equals(expected, ignoreCase = true)
        }
        if (!ok) {
            part.delete()
            settings.clearDownload()
            runCatching { manager.remove(id) }
            _state.value = State.Failed(target, "받은 파일이 원본과 달라요(체크섬 불일치). 다시 받아 주세요.")
            return
        }
        val finalFile = files.file(target.fileName)
        if (finalFile.exists()) finalFile.delete()
        if (!part.renameTo(finalFile)) {
            settings.clearDownload()
            _state.value = State.Failed(target, "파일 이름을 바꿀 수 없어요")
            return
        }
        settings.setContextFor(target.fileName, target.contextTokens)
        settings.selectedModel = target.fileName
        settings.clearDownload()
        runCatching { manager.remove(id) }
        _state.value = State.Done(target)
    }

    private data class Snapshot(val status: Int, val reason: Int, val bytes: Long, val total: Long, val localPath: String?)

    private fun query(id: Long): Snapshot? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            return Snapshot(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                localPath = localUri?.let { Uri.parse(it).path },
            )
        }
    }

    /** Follows Hugging Face's redirect by hand, carrying the token, and returns the first URL off huggingface.co. */
    private fun resolveWithToken(url: String, token: String): String {
        if (token.isBlank()) throw IOException("이 모델은 Hugging Face 토큰이 필요해요. 모델 화면 아래에 토큰을 넣어 주세요.")
        var current = url
        repeat(6) {
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
                connection.setRequestProperty("User-Agent", "gameforge/0.1")
                when (val code = connection.responseCode) {
                    in 300..399 -> {
                        val location = connection.getHeaderField("Location") ?: throw IOException("서버가 리디렉션 주소를 주지 않았어요")
                        val next = URL(URL(current), location).toString()
                        if (!next.startsWith("https://huggingface.co/")) return next
                        current = next
                    }
                    200 -> return current
                    401 -> throw IOException("토큰이 틀렸거나 만료됐어요 (401)")
                    403 -> throw IOException("접근이 거부됐어요 (403). huggingface.co에서 이 모델 페이지의 약관에 동의했는지 확인하세요")
                    404 -> throw IOException("파일을 찾을 수 없어요 (404)")
                    else -> throw IOException("서버 응답 $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("리디렉션이 너무 많아요")
    }

    private fun statusLabel(status: Int, reason: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "대기 중"
        DownloadManager.STATUS_RUNNING -> "받는 중"
        DownloadManager.STATUS_PAUSED -> when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "네트워크 기다리는 중"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Wi-Fi 기다리는 중"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "잠시 후 다시 시도"
            else -> "일시 중지"
        }
        else -> "진행 중"
    }

    private fun failReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "저장 공간이 부족해요"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "전송 중 오류가 났어요. 다시 시도해 주세요"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "서버가 거부했어요. 토큰이 필요한 모델이면 토큰과 약관 동의를 확인하세요"
        DownloadManager.ERROR_FILE_ERROR, DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "파일을 쓸 수 없어요"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "저장 장치를 찾을 수 없어요"
        DownloadManager.ERROR_CANNOT_RESUME -> "이어받기가 안 돼요. 다시 시도해 주세요"
        401, 403 -> "접근이 거부됐어요 (HTTP $reason). 토큰과 약관 동의를 확인하세요. 서명된 주소가 만료됐을 수도 있으니 다시 시도해 보세요"
        404 -> "파일이 없어요 (HTTP 404)"
        else -> "실패 (코드 $reason)"
    }

    private companion object {
        const val PART = ".part"
    }
}
