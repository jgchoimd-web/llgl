package com.llgl.tube

import com.llgl.tube.yt.Channel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns what the user typed into a channel id and title, with two public pages and no API key:
 * the channel page carries the id, YouTube's RSS feed for that id carries the title (and proves the id).
 * Blocking; call off the main thread.
 */
class ChannelResolver {
    class Resolved(val id: String, val title: String)

    fun resolve(input: String): Result<Resolved> {
        val ref = Channel.parse(input) ?: return Result.failure(IOException("채널 주소를 이해하지 못했어요: $input"))
        return try {
            val id = when (ref) {
                is Channel.Ref.Id -> ref.id
                else -> Channel.idFromPage(fetch(Channel.pageUrl(ref))) ?: return Result.failure(IOException("채널 페이지에서 ID를 찾지 못했어요"))
            }
            val title = Channel.titleFromFeed(fetch(Channel.feedUrl(id))) ?: return Result.failure(IOException("채널 피드를 읽지 못했어요 ($id)"))
            Result.success(Resolved(id, title))
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $url")
            c.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                val sb = StringBuilder()
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    sb.append(String(buf, 0, n, Charsets.UTF_8))
                    total += n
                    if (total > MAX_BYTES) break
                }
                return sb.toString()
            }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val MAX_BYTES = 3 * 1024 * 1024
        const val UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
