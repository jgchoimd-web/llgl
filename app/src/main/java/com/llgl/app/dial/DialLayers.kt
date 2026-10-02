package com.llgl.app.dial

import com.llgl.app.keyboard.KeyAction
import com.llgl.app.keyboard.Layer

/** One labelled spot on a ring. [pushed] is what a radial push (in or out) turns it into. */
class DialItem(val label: String, val action: KeyAction, val pushed: KeyAction? = null) {
    val isSpecial: Boolean get() = action !is KeyAction.Text && action !is KeyAction.Consonant
}

/**
 * Ring contents per layer. Items are listed from the top end of the arc (nearest the right edge)
 * to the bottom end. The middle of the outer ring is where a right thumb rests, so the most
 * frequent consonants and letters live there.
 *
 * In the Hangul layer the two inner rings have no fixed items: they are read as pulses relative
 * to where the thumb pushed in ([DialTables]).
 */
object DialLayers {
    private fun text(s: String, pushed: String? = null) = DialItem(s, KeyAction.Text(s), pushed?.let { KeyAction.Text(it) })
    private fun consonant(c: Char) = DialItem(c.toString(), KeyAction.Consonant(c))
    private fun letter(c: Char) = DialItem(c.toString(), KeyAction.Text(c.toString()), KeyAction.Text(c.uppercaseChar().toString()))

    /** Consonants that pushing past the outer edge hardens (ㄱ→ㄲ …). */
    val STRENGTHENABLE: List<Char> = DialTables.STRENGTHEN.keys.toList()

    val SYMBOLS_KEY = DialItem("123", KeyAction.ToggleSymbols)
    val LETTERS_KEY = DialItem("가나", KeyAction.ToggleSymbols)
    val LANG_KEY = DialItem("한/영", KeyAction.ToggleLang)

    val HANGUL_OUTER: List<DialItem> = listOf(
        SYMBOLS_KEY,
        consonant('ㅍ'), consonant('ㅊ'), consonant('ㅁ'), consonant('ㅈ'), consonant('ㅅ'), consonant('ㄴ'),
        consonant('ㅇ'), consonant('ㄱ'),
        consonant('ㄷ'), consonant('ㄹ'), consonant('ㅎ'), consonant('ㅂ'), consonant('ㅌ'), consonant('ㅋ'),
        LANG_KEY,
    )

    val ENGLISH_OUTER: List<DialItem> = listOf(
        SYMBOLS_KEY,
        letter('g'), letter('w'), letter('m'), letter('c'), letter('d'), letter('h'),
        letter('t'), letter('n'),
        letter('s'), letter('r'), letter('l'), letter('u'), letter('f'), letter('y'),
        LANG_KEY,
    )
    val ENGLISH_VOWEL: List<DialItem> = listOf(
        letter('q'), letter('k'), letter('v'), letter('p'), letter('i'), letter('e'),
        letter('a'), letter('o'), letter('b'), letter('j'), letter('x'), letter('z'),
    )
    val ENGLISH_DEEP: List<DialItem> = listOf(text("'"), text("-"), text("."), text(","), text("?"), text("!"))

    val SYMBOLS_OUTER: List<DialItem> = listOf(
        LETTERS_KEY,
        text("1", "!"), text("2", "@"), text("3", "#"), text("4", "$"), text("5", "%"), text("6", "^"), text("7", "&"),
        text("8", "*"), text("9", "("), text("0", ")"), text("@"), text("#"), text("/", "\\"), text(":", ";"),
        LANG_KEY,
    )
    val SYMBOLS_VOWEL: List<DialItem> = listOf(
        text("-", "_"), text("+", "="), text("*", "^"), text("(", "["), text(")", "]"), text("\"", "'"),
        text(".", "…"), text(",", ";"), text("?", "¿"), text("!", "¡"), text("~", "`"), text("&", "|"),
    )
    val SYMBOLS_DEEP: List<DialItem> = listOf(text("%"), text("₩", "$"), text("€", "£"), text("<", "{"), text(">", "}"), text("_"))

    fun outer(layer: Layer): List<DialItem> = when (layer) {
        Layer.HANGUL -> HANGUL_OUTER
        Layer.ENGLISH -> ENGLISH_OUTER
        Layer.SYMBOLS -> SYMBOLS_OUTER
    }

    /** Fixed items on an inner ring, or null when the ring is read as pulses (Hangul). */
    fun inner(layer: Layer, ring: Ring): List<DialItem>? = when (layer) {
        Layer.HANGUL -> null
        Layer.ENGLISH -> if (ring == Ring.VOWEL) ENGLISH_VOWEL else ENGLISH_DEEP
        Layer.SYMBOLS -> if (ring == Ring.VOWEL) SYMBOLS_VOWEL else SYMBOLS_DEEP
    }

    fun items(layer: Layer, ring: Ring): List<DialItem>? = if (ring == Ring.OUTER) outer(layer) else inner(layer, ring)
}
