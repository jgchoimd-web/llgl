package com.llgl.tube.yt

/** Pure helpers around a YouTube channel: what the user typed, the ids, the page and the feed. */
object Channel {
    private val ID = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val PAGE_ID = Regex("\"(?:externalId|channelId)\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{22})\"")
    private val HANDLE = Regex("^@[A-Za-z0-9._-]{3,30}$")
    private val FEED_TITLE = Regex("<title>([^<]*)</title>")

    sealed interface Ref {
        data class Id(val id: String) : Ref
        data class Handle(val handle: String) : Ref
        data class User(val name: String) : Ref
    }

    fun isId(s: String): Boolean = ID.matches(s)

    /** What the user typed: a handle, a channel id, or a channel URL in any of its usual shapes. */
    fun parse(input: String): Ref? {
        val s = input.trim()
        if (s.isEmpty()) return null
        if (ID.matches(s)) return Ref.Id(s)
        if (HANDLE.matches(s)) return Ref.Handle(s)
        val urlish = s.contains('/') || s.contains('.') || s.contains('?')
        // A bare name with nothing url-like around it is taken as a handle.
        if (!urlish) return if (HANDLE.matches("@$s")) Ref.Handle("@$s") else null
        var path = s.removePrefix("https://").removePrefix("http://").removePrefix("www.").removePrefix("m.").removePrefix("youtube.com")
        path = path.substringBefore('?').substringBefore('#').trim('/')
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return when {
            HANDLE.matches(parts[0]) -> Ref.Handle(parts[0])
            parts[0] == "channel" && parts.size >= 2 && ID.matches(parts[1]) -> Ref.Id(parts[1])
            (parts[0] == "c" || parts[0] == "user") && parts.size >= 2 -> Ref.User(parts[1])
            else -> null
        }
    }

    /** The channel page to fetch for a reference that still needs its id. */
    fun pageUrl(ref: Ref): String = when (ref) {
        is Ref.Id -> "https://www.youtube.com/channel/${ref.id}"
        is Ref.Handle -> "https://www.youtube.com/${ref.handle}"
        is Ref.User -> "https://www.youtube.com/c/${ref.name}"
    }

    /** A channel's uploads playlist is the channel id with UU in place of UC. */
    fun uploadsPlaylist(channelId: String): String? = if (ID.matches(channelId)) "UU" + channelId.substring(2) else null

    /** YouTube's own RSS feed for a channel: no API key, carries the channel title. */
    fun feedUrl(channelId: String): String = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    /** The channel id inside a channel page's HTML, if present. */
    fun idFromPage(html: String): String? = PAGE_ID.find(html)?.groupValues?.get(1)

    /** The channel title from its feed: the feed-level title, before any entry. */
    fun titleFromFeed(xml: String): String? {
        val feedStart = xml.indexOf("<feed")
        if (feedStart < 0) return null
        val entry = xml.indexOf("<entry", feedStart).let { if (it < 0) xml.length else it }
        val head = xml.substring(feedStart, entry)
        val m = FEED_TITLE.find(head) ?: return null
        return unescape(m.groupValues[1]).trim().ifEmpty { null }
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}
