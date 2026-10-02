package com.llgl.app.dial

import kotlin.math.abs

/**
 * What the dial's pulses mean. Vowels and finals are chosen by how many ticks the thumb turned
 * from where it entered the ring: 0 is the most common choice, and the count grows with rarity,
 * alternating clockwise (+) and counter-clockwise (−).
 */
object DialTables {
    /** Reached by pushing into the vowel ring. */
    val VOWELS_A: Map<Int, Char> = mapOf(
        0 to 'ㅏ', 1 to 'ㅣ', -1 to 'ㅡ', 2 to 'ㅓ', -2 to 'ㅗ', 3 to 'ㅜ', -3 to 'ㅔ',
        4 to 'ㅐ', -4 to 'ㅢ', 5 to 'ㅚ', -5 to 'ㅟ',
    )

    /** Reached by pushing in deeper. */
    val VOWELS_B: Map<Int, Char> = mapOf(
        0 to 'ㅑ', 1 to 'ㅕ', -1 to 'ㅛ', 2 to 'ㅠ', -2 to 'ㅘ', 3 to 'ㅝ', -3 to 'ㅙ',
        4 to 'ㅞ', -4 to 'ㅒ', 5 to 'ㅖ',
    )

    /** Reached by pushing back out to the outer ring. */
    val FINALS: Map<Int, Char> = mapOf(
        0 to 'ㄴ', 1 to 'ㅇ', -1 to 'ㄹ', 2 to 'ㄱ', -2 to 'ㅁ', 3 to 'ㅂ', -3 to 'ㅅ',
        4 to 'ㄷ', -4 to 'ㅈ', 5 to 'ㅎ', -5 to 'ㅊ', 6 to 'ㅌ', -6 to 'ㅍ', 7 to 'ㅋ',
    )

    /** Pushing past the outer edge "hardens" a consonant. */
    val STRENGTHEN: Map<Char, Char> = mapOf('ㄱ' to 'ㄲ', 'ㄷ' to 'ㄸ', 'ㅂ' to 'ㅃ', 'ㅅ' to 'ㅆ', 'ㅈ' to 'ㅉ')

    fun vowel(set: Int, ticks: Int): Char = vowels(set).getValue(vowelKey(set, ticks))

    fun final(ticks: Int): Char = FINALS.getValue(finalKey(ticks))

    /** The table entry [ticks] lands on, for highlighting the dial. */
    fun vowelKey(set: Int, ticks: Int): Int = nearestKey(vowels(set), ticks)

    fun finalKey(ticks: Int): Int = nearestKey(FINALS, ticks)

    fun vowels(set: Int): Map<Int, Char> = if (set == 0) VOWELS_A else VOWELS_B

    fun strengthen(c: Char): Char = STRENGTHEN[c] ?: c

    /** Ticks past the end of a table snap to its nearest entry on that side. */
    private fun nearestKey(table: Map<Int, Char>, ticks: Int): Int {
        if (table.containsKey(ticks)) return ticks
        return table.keys.minByOrNull { abs(it - ticks) * 2 + (if ((it < 0) == (ticks < 0)) 0 else 1) }!!
    }
}
