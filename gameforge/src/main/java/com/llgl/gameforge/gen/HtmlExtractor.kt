package com.llgl.gameforge.gen

/**
 * Turns whatever the model wrote into something a WebView can run: strips chatter and markdown
 * fences around the document, closes a document that was cut off by the token limit, and injects
 * the few things every game needs on a phone (viewport, no-scroll CSS, an error reporter).
 */
object HtmlExtractor {
    data class Extracted(val html: String, val title: String, val truncated: Boolean)

    /** Marker attribute on everything we inject, so running [prepare] twice changes nothing. */
    const val MARK = "data-gameforge"

    /** Returns null when there is no HTML in [raw] at all (the model answered in prose). */
    fun extract(raw: String, fallbackTitle: String): Extracted? {
        var text = unfence(raw)
        var lower = text.lowercase()
        var truncated = false
        var start = lower.indexOf("<!doctype")
        if (start < 0) start = lower.indexOf("<html")
        if (start >= 0) {
            text = text.substring(start)
        } else {
            // No document shell; accept a fragment with real markup and wrap it ourselves.
            val fragment = listOf("<head", "<body", "<canvas", "<script", "<style")
                .map { lower.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: return null
            text = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n</head>\n<body>\n" +
                text.substring(fragment) + "\n</body>\n</html>"
            truncated = true
        }
        lower = text.lowercase()
        val end = lower.lastIndexOf("</html>")
        if (end >= 0) {
            text = text.substring(0, end + "</html>".length)
        } else {
            truncated = true
            text = closeOpenTags(text)
        }
        return Extracted(text.trim(), titleOf(text, fallbackTitle), truncated)
    }

    /** The visible name of a game: its <title>, else [fallback], clipped. */
    fun titleOf(html: String, fallback: String): String {
        val match = TITLE.find(html)
        val fromTitle = match?.groupValues?.get(1)?.replace(WHITESPACE, " ")?.trim().orEmpty()
        val chosen = if (fromTitle.isNotEmpty()) fromTitle else fallback.replace(WHITESPACE, " ").trim()
        val name = if (chosen.isEmpty()) "이름 없는 게임" else chosen
        return if (name.length > 40) name.substring(0, 40).trimEnd() + "…" else name
    }

    /** Adds viewport, charset, no-scroll CSS and the error hook once; returns [html] unchanged if already prepared. */
    fun prepare(html: String): String {
        if (html.contains(MARK)) return html
        val lower = html.lowercase()
        val inject = StringBuilder()
        if (!lower.contains("charset")) inject.append("<meta charset=\"utf-8\" $MARK>\n")
        if (!lower.contains("name=\"viewport\"") && !lower.contains("name='viewport'")) {
            inject.append(
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, " +
                    "user-scalable=no, viewport-fit=cover\" $MARK>\n",
            )
        }
        inject.append("<style $MARK>$BASE_CSS</style>\n")
        inject.append("<script $MARK>$HOOK</script>\n")

        val head = HEAD_OPEN.find(html)
        if (head != null) {
            val at = head.range.last + 1
            return html.substring(0, at) + "\n" + inject + html.substring(at)
        }
        val root = HTML_OPEN.find(html)
        if (root != null) {
            val at = root.range.last + 1
            return html.substring(0, at) + "\n<head>\n" + inject + "</head>" + html.substring(at)
        }
        return "<!DOCTYPE html>\n<html>\n<head>\n" + inject + "</head>\n<body>\n" + html + "\n</body>\n</html>"
    }

    /** If the reply is wrapped in ``` fences, keeps the fenced block that holds the markup. */
    private fun unfence(raw: String): String {
        val fence = raw.indexOf("```")
        if (fence < 0) return raw
        val lineEnd = raw.indexOf('\n', fence)
        if (lineEnd < 0) return raw.replace("```", "")
        val close = raw.indexOf("```", lineEnd + 1)
        val body = if (close < 0) raw.substring(lineEnd + 1) else raw.substring(lineEnd + 1, close)
        val lower = body.lowercase()
        val looksLikeMarkup = listOf("<html", "<!doctype", "<canvas", "<script", "<body").any { lower.contains(it) }
        return if (looksLikeMarkup) body else raw.replace("```", "")
    }

    /** A reply cut off mid-file: close the script/style/body that are still open so the browser gets a whole document. */
    private fun closeOpenTags(text: String): String {
        val out = StringBuilder(text.trimEnd())
        val lower = out.toString().lowercase()
        val scriptOpen = lower.lastIndexOf("<script")
        val scriptClose = lower.lastIndexOf("</script>")
        val styleOpen = lower.lastIndexOf("<style")
        val styleClose = lower.lastIndexOf("</style>")
        if (scriptOpen >= 0 && scriptClose < scriptOpen) {
            out.append("\n</script>")
        } else if (styleOpen >= 0 && styleClose < styleOpen) {
            out.append("\n</style>")
        }
        if (!lower.contains("</body>")) out.append("\n</body>")
        out.append("\n</html>")
        return out.toString()
    }

    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HEAD_OPEN = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val HTML_OPEN = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")

    private const val BASE_CSS = "html,body{margin:0;padding:0;overflow:hidden;background:#000;touch-action:none;" +
        "-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;overscroll-behavior:none;" +
        "-webkit-tap-highlight-color:transparent}"

    // Uncaught exceptions already reach the WebView console; this adds the promise rejections that do not.
    private const val HOOK = "(function(){window.addEventListener('unhandledrejection',function(e){var r=e.reason;" +
        "try{console.error('Unhandled promise rejection: '+(r&&r.message||r))}catch(_){}});})();"
}
