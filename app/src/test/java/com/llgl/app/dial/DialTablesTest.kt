package com.llgl.app.dial

import com.llgl.app.hangul.Jamo
import com.llgl.app.keyboard.KeyAction
import com.llgl.app.keyboard.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialTablesTest {

    @Test
    fun `the two vowel sets cover all 21 vowels exactly once`() {
        val all = DialTables.VOWELS_A.values + DialTables.VOWELS_B.values
        assertEquals(21, all.size)
        assertEquals(Jamo.JUNGSEONG.toSet(), all.toSet())
    }

    @Test
    fun `finals plus hardening cover every single final consonant`() {
        val finals = DialTables.FINALS.values.toMutableSet()
        finals += DialTables.FINALS.values.map { DialTables.strengthen(it) }
        val singles = Jamo.JONGSEONG.filter { it != ' ' && Jamo.splitFinal(it) == null }.toSet()
        assertEquals(singles, finals.filter { Jamo.canBeFinal(it) }.toSet())
    }

    @Test
    fun `ticks beyond a table snap to the nearest entry on that side`() {
        assertEquals('ㅚ', DialTables.vowel(0, 9))
        assertEquals('ㅟ', DialTables.vowel(0, -9))
        assertEquals('ㅖ', DialTables.vowel(1, 7))
        assertEquals('ㅒ', DialTables.vowel(1, -7))
        assertEquals('ㅋ', DialTables.final(12))
        assertEquals('ㅍ', DialTables.final(-12))
        assertEquals('ㄴ', DialTables.final(0))
    }

    @Test
    fun `the Hangul outer ring holds all 14 basic consonants plus the layer keys`() {
        val consonants = DialLayers.HANGUL_OUTER.mapNotNull { (it.action as? KeyAction.Consonant)?.c }
        assertEquals(14, consonants.size)
        assertEquals("ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ".toSet(), consonants.toSet())
        assertEquals(16, DialLayers.HANGUL_OUTER.size)
        assertEquals(KeyAction.ToggleSymbols, DialLayers.HANGUL_OUTER.first().action)
        assertEquals(KeyAction.ToggleLang, DialLayers.HANGUL_OUTER.last().action)
        assertTrue(DialLayers.STRENGTHENABLE.all { it in consonants })
    }

    @Test
    fun `the English rings reach all 26 letters and their capitals`() {
        val items = DialLayers.ENGLISH_OUTER + DialLayers.ENGLISH_VOWEL
        val lower = items.mapNotNull { (it.action as? KeyAction.Text)?.text?.singleOrNull() }.toSet()
        val upper = items.mapNotNull { (it.pushed as? KeyAction.Text)?.text?.singleOrNull() }.toSet()
        assertTrue(('a'..'z').all { it in lower })
        assertTrue(('A'..'Z').all { it in upper })
        assertEquals(16, DialLayers.outer(Layer.ENGLISH).size)
        assertEquals(12, DialLayers.inner(Layer.ENGLISH, Ring.VOWEL)!!.size)
        assertEquals(6, DialLayers.inner(Layer.ENGLISH, Ring.DEEP)!!.size)
    }

    @Test
    fun `the symbols layer has every digit on the outer ring`() {
        val taps = DialLayers.SYMBOLS_OUTER.mapNotNull { (it.action as? KeyAction.Text)?.text }.toSet()
        assertTrue(('0'..'9').all { it.toString() in taps })
        assertEquals(16, DialLayers.SYMBOLS_OUTER.size)
    }
}
