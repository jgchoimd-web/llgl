package com.llgl.app.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamoTest {

    @Test
    fun `keys flatten syllables and split compound parts`() {
        assertEquals("ㄱㅏㅇㅇㅏㅈㅣ", Jamo.key("강아지"))
        assertEquals("ㅇㅗㅐ", Jamo.key("왜"))
        assertEquals("ㄷㅏㄹㄱ", Jamo.key("닭"))
        assertEquals("hello", Jamo.key("Hello"))
        assertEquals("ㄱㅏ", Jamo.key("ㄱㅏ"))
    }

    @Test
    fun `a half-typed word is a prefix of its completions`() {
        assertTrue(Jamo.key("강아지").startsWith(Jamo.key("가")))
        assertTrue(Jamo.key("강아지").startsWith(Jamo.key("강")))
        assertTrue(Jamo.key("왜").startsWith(Jamo.key("오")))
        assertFalse(Jamo.key("강아지").startsWith(Jamo.key("거")))
    }

    @Test
    fun `combination tables`() {
        assertEquals('ㅘ', Jamo.combineVowels('ㅗ', 'ㅏ'))
        assertEquals('ㅢ', Jamo.combineVowels('ㅡ', 'ㅣ'))
        assertNull(Jamo.combineVowels('ㅏ', 'ㅗ'))
        assertEquals('ㄺ', Jamo.combineFinals('ㄹ', 'ㄱ'))
        assertNull(Jamo.combineFinals('ㄱ', 'ㄹ'))
        assertTrue(Jamo.canBeFinal('ㅆ'))
        assertFalse(Jamo.canBeFinal('ㄸ'))
        assertFalse(Jamo.canBeFinal('ㅏ'))
    }
}
