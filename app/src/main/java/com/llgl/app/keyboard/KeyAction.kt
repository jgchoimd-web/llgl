package com.llgl.app.keyboard

/** What a dial item or hub gesture does. */
sealed interface KeyAction {
    data class Text(val text: String) : KeyAction

    /** A Hangul consonant on the outer ring. */
    data class Consonant(val c: Char) : KeyAction
    data object Space : KeyAction
    data object Backspace : KeyAction
    data object DeleteWord : KeyAction
    data object Enter : KeyAction
    data object Newline : KeyAction
    data object ToggleLang : KeyAction
    data object ToggleSymbols : KeyAction
    data class MoveCursor(val delta: Int) : KeyAction
    data object None : KeyAction
}

enum class Layer { HANGUL, ENGLISH, SYMBOLS }
