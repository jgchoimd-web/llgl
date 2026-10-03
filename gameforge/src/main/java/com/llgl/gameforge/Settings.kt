package com.llgl.gameforge

import android.content.Context
import androidx.core.content.edit
import com.llgl.gameforge.model.DownloadTarget
import com.llgl.gameforge.model.ModelCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User choices and the one download that may be in flight. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gameforge", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)

    /** Bumps on every change, so screens that read settings directly know to re-read them. */
    val version: StateFlow<Int> = _version

    private fun touch() {
        _version.value = _version.value + 1
    }

    /** File name (inside the models folder) of the model to generate with. */
    var selectedModel: String?
        get() = prefs.getString(KEY_MODEL, null)
        set(value) {
            prefs.edit { if (value == null) remove(KEY_MODEL) else putString(KEY_MODEL, value) }
            touch()
        }

    /** "CPU" or "GPU". */
    var backend: String
        get() = prefs.getString(KEY_BACKEND, "CPU") ?: "CPU"
        set(value) {
            prefs.edit { putString(KEY_BACKEND, value) }
            touch()
        }

    var hfToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) {
            prefs.edit { putString(KEY_TOKEN, value.trim()) }
            touch()
        }

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.6f)
        set(value) {
            prefs.edit { putFloat(KEY_TEMPERATURE, value) }
            touch()
        }

    /** Wrap prompts in Gemma's turn markers ourselves instead of trusting the bundle's template. */
    var manualTemplate: Boolean
        get() = prefs.getBoolean(KEY_TEMPLATE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_TEMPLATE, value) }
            touch()
        }

    fun contextFor(fileName: String): Int = prefs.getInt("ctx_$fileName", ModelCatalog.defaultContext(fileName))

    fun setContextFor(fileName: String, tokens: Int) {
        prefs.edit { putInt("ctx_$fileName", tokens) }
        touch()
    }

    // --- the download in flight; kept so tracking resumes after the process is killed ---

    val downloadId: Long get() = prefs.getLong(KEY_DL_ID, -1L)

    fun saveDownload(id: Long, target: DownloadTarget) {
        prefs.edit {
            putLong(KEY_DL_ID, id)
            putString(KEY_DL_NAME, target.name)
            putString(KEY_DL_FILE, target.fileName)
            putString(KEY_DL_URL, target.url)
            putString(KEY_DL_SHA, target.sha256)
            putInt(KEY_DL_CTX, target.contextTokens)
            putString(KEY_DL_SPEC, target.specId)
            putBoolean(KEY_DL_TOKEN, target.usesToken)
        }
    }

    fun downloadTarget(): DownloadTarget? {
        val file = prefs.getString(KEY_DL_FILE, null) ?: return null
        return DownloadTarget(
            name = prefs.getString(KEY_DL_NAME, file) ?: file,
            fileName = file,
            url = prefs.getString(KEY_DL_URL, "") ?: "",
            sha256 = prefs.getString(KEY_DL_SHA, null),
            contextTokens = prefs.getInt(KEY_DL_CTX, 4096),
            specId = prefs.getString(KEY_DL_SPEC, null),
            usesToken = prefs.getBoolean(KEY_DL_TOKEN, false),
        )
    }

    fun clearDownload() {
        prefs.edit {
            remove(KEY_DL_ID)
            remove(KEY_DL_NAME)
            remove(KEY_DL_FILE)
            remove(KEY_DL_URL)
            remove(KEY_DL_SHA)
            remove(KEY_DL_CTX)
            remove(KEY_DL_SPEC)
            remove(KEY_DL_TOKEN)
        }
    }

    private companion object {
        const val KEY_MODEL = "selected_model"
        const val KEY_BACKEND = "backend"
        const val KEY_TOKEN = "hf_token"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TEMPLATE = "manual_template"
        const val KEY_DL_ID = "dl_id"
        const val KEY_DL_NAME = "dl_name"
        const val KEY_DL_FILE = "dl_file"
        const val KEY_DL_URL = "dl_url"
        const val KEY_DL_SHA = "dl_sha"
        const val KEY_DL_CTX = "dl_ctx"
        const val KEY_DL_SPEC = "dl_spec"
        const val KEY_DL_TOKEN = "dl_token"
    }
}
