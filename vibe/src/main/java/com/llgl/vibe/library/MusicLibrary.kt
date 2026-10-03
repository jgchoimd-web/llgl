package com.llgl.vibe.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat

data class Song(val uri: Uri, val title: String, val artist: String, val durationMs: Long) {
    /** Stable id for caches and the recent list. */
    val key: String get() = Integer.toHexString(uri.toString().hashCode()) + "-" + durationMs

    fun save(): String = "${uri}|${title.replace('|', ' ')}|${artist.replace('|', ' ')}|$durationMs"

    companion object {
        fun load(s: String): Song? {
            val p = s.split('|')
            if (p.size != 4) return null
            val d = p[3].toLongOrNull() ?: return null
            return Song(Uri.parse(p[0]), p[1], p[2], d)
        }
    }
}

/** The phone's music through MediaStore, plus single files through the document picker. */
object MusicLibrary {
    val permission: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun query(context: Context): List<Song> {
        val out = ArrayList<Song>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000"
        try {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
                    out += Song(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), c.getString(titleCol) ?: "제목 없음", artist, c.getLong(durCol))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** A song picked with the document picker; metadata from the file itself when it has any. */
    fun fromDocument(context: Context, uri: Uri): Song {
        var name = "선택한 파일"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
            }
        } catch (_: Exception) {
        }
        var title = name.substringBeforeLast('.')
        var artist = ""
        var duration = 0L
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
        return Song(uri, title, artist, duration)
    }
}
