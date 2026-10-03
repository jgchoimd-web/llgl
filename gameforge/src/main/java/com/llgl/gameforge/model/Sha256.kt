package com.llgl.gameforge.model

import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    /** Streams [input] to the end and returns the lowercase hex digest; [onProgress] gets the bytes read so far. */
    fun of(input: InputStream, onProgress: ((Long) -> Unit)? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 20)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            total += n
            onProgress?.invoke(total)
        }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(DIGITS[v ushr 4]).append(DIGITS[v and 0xF])
        }
        return out.toString()
    }

    private const val DIGITS = "0123456789abcdef"
}
