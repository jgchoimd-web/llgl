package com.llgl.app.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangulComposerTest {

    private fun type(block: HangulComposer.(StringBuilder) -> Unit): String {
        val out = StringBuilder()
        val composer = HangulComposer()
        composer.block(out)
        out.append(composer.flush())
        return out.toString()
    }

    @Test
    fun `consonant plus vowel gesture then a tap for the final`() {
        val text = type { out ->
            out.append(syllable('ㄱ', 'ㅏ'))
            assertEquals("가", composing)
            out.append(consonant('ㅇ'))
            assertEquals("강", composing)
        }
        assertEquals("강", text)
    }

    @Test
    fun `a vowel gesture always starts a new syllable`() {
        val text = type { out ->
            out.append(syllable('ㅇ', 'ㅣ'))
            out.append(consonant('ㅆ'))
            out.append(syllable('ㅇ', 'ㅓ'))
            assertEquals("어", composing)
            out.append(consonant('ㅆ'))
            out.append(syllable('ㄷ', 'ㅏ'))
        }
        assertEquals("있었다", text)
    }

    @Test
    fun `compound finals and the syllable boundary after them`() {
        assertEquals("닭", type { out -> out.append(syllable('ㄷ', 'ㅏ')); out.append(consonant('ㄹ')); out.append(consonant('ㄱ')) })
        assertEquals("값", type { out -> out.append(syllable('ㄱ', 'ㅏ')); out.append(consonant('ㅂ')); out.append(consonant('ㅅ')) })
        assertEquals("닭ㅅ", type { out ->
            out.append(syllable('ㄷ', 'ㅏ'))
            out.append(consonant('ㄹ'))
            out.append(consonant('ㄱ'))
            out.append(consonant('ㅅ'))
            assertEquals("ㅅ", composing)
        })
    }

    @Test
    fun `compound vowels arrive whole from a turn gesture`() {
        assertEquals("왜", type { out -> out.append(syllable('ㅇ', 'ㅙ')) })
        assertEquals("의사", type { out -> out.append(syllable('ㅇ', 'ㅢ')); out.append(syllable('ㅅ', 'ㅏ')) })
    }

    @Test
    fun `lone consonants are committed one by one`() {
        assertEquals("ㅋㅋㅋ", type { out -> repeat(3) { out.append(consonant('ㅋ')) } })
    }

    @Test
    fun `consonants that cannot be finals start a new jamo`() {
        assertEquals("가ㄸ", type { out ->
            out.append(syllable('ㄱ', 'ㅏ'))
            out.append(consonant('ㄸ'))
            assertEquals("ㄸ", composing)
        })
        assertEquals("각ㄸ", type { out ->
            out.append(syllable('ㄱ', 'ㅏ'))
            out.append(consonant('ㄱ'))
            out.append(consonant('ㄸ'))
        })
    }

    @Test
    fun `backspace peels one jamo at a time`() {
        val composer = HangulComposer()
        composer.syllable('ㄷ', 'ㅏ')
        composer.consonant('ㄹ')
        composer.consonant('ㄱ')
        assertEquals("닭", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("달", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("다", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("ㄷ", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("", composer.composing)
        assertFalse(composer.backspace())
    }

    @Test
    fun `plain vowels follow 2-beolsik rules`() {
        val composer = HangulComposer()
        composer.consonant('ㄱ')
        assertEquals("", composer.vowel('ㅗ'))
        assertEquals("고", composer.composing)
        assertEquals("", composer.vowel('ㅏ'))
        assertEquals("과", composer.composing)
        assertEquals("과", composer.vowel('ㅣ'))
        assertEquals("ㅣ", composer.composing)

        val carried = HangulComposer()
        carried.syllable('ㄷ', 'ㅏ')
        carried.consonant('ㄹ')
        carried.consonant('ㄱ')
        assertEquals("달", carried.vowel('ㅏ'))
        assertEquals("가", carried.composing)
    }

    @Test
    fun `a lone consonant is reported until a vowel joins it`() {
        val composer = HangulComposer()
        assertFalse(composer.isLoneConsonant)
        composer.consonant('ㄱ')
        assertTrue(composer.isLoneConsonant)
        composer.vowel('ㅏ')
        assertFalse(composer.isLoneConsonant)
        composer.consonant('ㅇ')
        assertFalse(composer.isLoneConsonant)
        assertEquals("강", composer.composing)
    }

    @Test
    fun `every syllable round-trips through compose and decompose`() {
        for (code in 0xAC00..0xD7A3) {
            val c = code.toChar()
            val (cho, jung, jong) = Jamo.decompose(c)!!
            assertEquals(c, Jamo.compose(cho, jung, jong))
        }
    }
}
