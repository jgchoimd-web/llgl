package com.llgl.tube.yt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPageTest {
    @Test
    fun `the page carries the playlist, the fit and the mute, and loads the official player`() {
        val html = PlayerPage.html("UU7YOGHUfC1Tb6E4pudI9STA", PlayerPage.FIT_COVER, muted = true)
        assertTrue(html.contains("list='UU7YOGHUfC1Tb6E4pudI9STA'"))
        assertTrue(html.contains("fit='cover'"))
        assertTrue(html.contains("muted=true"))
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        assertTrue(html.contains("listType:'playlist'"))
        assertFalse("placeholder left behind", html.contains("__"))

        val other = PlayerPage.html("UUabc", "contain", muted = false)
        assertTrue(other.contains("fit='contain'"))
        assertTrue(other.contains("muted=false"))
        assertTrue(PlayerPage.html("UUabc", "weird", muted = false).contains("fit='cover'"))
    }
}
