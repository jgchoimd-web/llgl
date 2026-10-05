package com.llgl.tube.yt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelTest {
    private val id = "UC7YOGHUfC1Tb6E4pudI9STA"

    @Test
    fun `handles, ids and the usual channel urls are understood`() {
        assertEquals(Channel.Ref.Handle("@MentalOutlaw"), Channel.parse("@MentalOutlaw"))
        assertEquals(Channel.Ref.Handle("@MentalOutlaw"), Channel.parse("  https://www.youtube.com/@MentalOutlaw/videos?view=0 "))
        assertEquals(Channel.Ref.Handle("@MentalOutlaw"), Channel.parse("m.youtube.com/@MentalOutlaw"))
        assertEquals(Channel.Ref.Handle("@MentalOutlaw"), Channel.parse("MentalOutlaw"))
        assertEquals(Channel.Ref.Id(id), Channel.parse(id))
        assertEquals(Channel.Ref.Id(id), Channel.parse("https://youtube.com/channel/$id"))
        assertEquals(Channel.Ref.User("MentalOutlaw"), Channel.parse("https://www.youtube.com/c/MentalOutlaw"))
        assertEquals(Channel.Ref.User("mentaloutlaw"), Channel.parse("youtube.com/user/mentaloutlaw/featured"))
        assertNull(Channel.parse(""))
        assertNull(Channel.parse("Mental Outlaw"))
        assertNull(Channel.parse("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun `the uploads playlist swaps UC for UU and the page url follows the reference`() {
        assertEquals("UU7YOGHUfC1Tb6E4pudI9STA", Channel.uploadsPlaylist(id))
        assertNull(Channel.uploadsPlaylist("not-an-id"))
        assertEquals("https://www.youtube.com/@MentalOutlaw", Channel.pageUrl(Channel.Ref.Handle("@MentalOutlaw")))
        assertEquals("https://www.youtube.com/channel/$id", Channel.pageUrl(Channel.Ref.Id(id)))
        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=$id", Channel.feedUrl(id))
    }

    @Test
    fun `the channel id is read from the page and the title from the feed`() {
        val page = "<html>..ytInitialData = {\"metadata\":{\"channelMetadataRenderer\":{\"title\":\"Mental Outlaw\",\"externalId\":\"$id\"}}}..</html>"
        assertEquals(id, Channel.idFromPage(page))
        assertEquals(id, Channel.idFromPage("{\"channelId\": \"$id\"}"))
        assertNull(Channel.idFromPage("<html>nothing here</html>"))

        val feed = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
 <link rel="self" href="http://www.youtube.com/feeds/videos.xml?channel_id=$id"/>
 <id>yt:channel:7YOGHUfC1Tb6E4pudI9STA</id>
 <yt:channelId>7YOGHUfC1Tb6E4pudI9STA</yt:channelId>
 <title>Mental &amp; Outlaw</title>
 <author><name>Mental Outlaw</name></author>
 <entry><id>yt:video:x</id><title>Some video title</title></entry>
</feed>"""
        assertEquals("Mental & Outlaw", Channel.titleFromFeed(feed))
        assertNull(Channel.titleFromFeed("<html>not a feed</html>"))
    }
}
