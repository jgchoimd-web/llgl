package com.llgl.gameforge.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The folder that holds model bundles: the app-private external files dir (same place
 * DownloadManager writes to), falling back to internal storage when there is none.
 */
class ModelFiles(private val context: Context) {
    val dir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR).apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Finished model files, excluding downloads and imports still in progress. */
    fun installed(): List<File> = (dir.listFiles() ?: emptyArray())
        .filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".tmp") }
        .sortedBy { it.name }

    fun isInstalled(spec: ModelSpec): Boolean = file(spec.fileName).let { it.exists() && it.length() > 0 }

    fun delete(name: String): Boolean = file(name).delete()

    fun freeBytes(): Long = dir.usableSpace

    data class Imported(val file: File, val sha256: String)

    /**
     * Copies a document the user picked into the models folder, hashing it on the way.
     * Blocking: call it off the main thread. [onProgress] gets (bytes copied, total or -1).
     */
    fun import(uri: Uri, onProgress: (Long, Long) -> Unit): Imported {
        val resolver = context.contentResolver
        var name = "model.task"
        var total = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) c.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) total = c.getLong(sizeIndex)
            }
        }
        name = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val tmp = file("$name.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("파일을 열 수 없어요")
            input.use { ins ->
                tmp.outputStream().buffered(1 shl 20).use { out ->
                    val buffer = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val n = ins.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        copied += n
                        onProgress(copied, total)
                    }
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        if (tmp.length() == 0L) {
            tmp.delete()
            throw IOException("빈 파일이에요")
        }
        val target = file(name)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("파일을 저장할 수 없어요")
        }
        return Imported(target, Sha256.hex(digest.digest()))
    }

    companion object {
        const val DIR = "models"
    }
}
