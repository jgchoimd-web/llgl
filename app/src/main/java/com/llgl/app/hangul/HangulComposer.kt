package com.llgl.app.hangul

/**
 * Composes Hangul syllables one jamo at a time. At most one syllable (or one lone jamo) is ever
 * "composing"; whenever a new syllable starts, the previous one is returned so the caller can
 * commit it to the editor.
 *
 * The thumb keyboard enters consonant+vowel in a single gesture ([syllable]), which always starts
 * a new syllable, while a bare consonant tap ([consonant]) becomes the final of the current
 * syllable when that is legal. [vowel] exists for completeness and follows standard 2-beolsik
 * behaviour (a vowel after a final moves that final into a new syllable).
 */
class HangulComposer {
    private var cho: Char? = null
    private var jung: Char? = null
    private var jongA: Char? = null
    private var jongB: Char? = null

    val isEmpty: Boolean
        get() = cho == null && jung == null

    /** True while only an initial consonant is composing, waiting for a vowel. */
    val isLoneConsonant: Boolean
        get() = cho != null && jung == null

    /** The text currently being composed: "", a lone jamo, or one syllable. */
    val composing: String
        get() {
            val c = cho
            val v = jung
            return when {
                c == null && v == null -> ""
                v == null -> c.toString()
                c == null -> v.toString()
                else -> Jamo.compose(c, v, finalChar()).toString()
            }
        }

    private fun finalChar(): Char? {
        val a = jongA ?: return null
        val b = jongB ?: return a
        return Jamo.combineFinals(a, b) ?: a
    }

    /** A bare consonant tap. Returns text to commit before the new composition, if any. */
    fun consonant(c: Char): String {
        require(Jamo.isConsonant(c)) { "not a consonant: $c" }
        val currentCho = cho
        val currentJung = jung
        if (currentCho == null && currentJung == null) {
            cho = c
            return ""
        }
        if (currentJung == null || currentCho == null) return commitThenStart(c)
        if (jongA == null) {
            if (!Jamo.canBeFinal(c)) return commitThenStart(c)
            jongA = c
            return ""
        }
        if (jongB == null) {
            if (Jamo.combineFinals(jongA!!, c) == null) return commitThenStart(c)
            jongB = c
            return ""
        }
        return commitThenStart(c)
    }

    /** Consonant + vowel entered as one gesture: always begins a new syllable. */
    fun syllable(c: Char, v: Char): String {
        require(Jamo.isConsonant(c)) { "not a consonant: $c" }
        require(Jamo.isVowel(v)) { "not a vowel: $v" }
        val out = composing
        clear()
        cho = c
        jung = v
        return out
    }

    /** A vowel on its own (standard 2-beolsik rules). */
    fun vowel(v: Char): String {
        require(Jamo.isVowel(v)) { "not a vowel: $v" }
        val currentJung = jung
        if (currentJung == null) {
            jung = v
            return ""
        }
        if (jongA == null) {
            val combined = Jamo.combineVowels(currentJung, v)
            if (combined != null) {
                jung = combined
                return ""
            }
            val out = composing
            clear()
            jung = v
            return out
        }
        // A final followed by a vowel: the (last part of the) final starts the next syllable.
        val moved: Char
        if (jongB != null) {
            moved = jongB!!
            jongB = null
        } else {
            moved = jongA!!
            jongA = null
        }
        val out = composing
        clear()
        cho = moved
        jung = v
        return out
    }

    /** Removes the last jamo. Returns false when there was nothing composing. */
    fun backspace(): Boolean {
        if (isEmpty) return false
        when {
            jongB != null -> jongB = null
            jongA != null -> jongA = null
            jung != null -> jung = null
            else -> cho = null
        }
        return true
    }

    /** Ends the composition and returns the text to commit. */
    fun flush(): String {
        val out = composing
        clear()
        return out
    }

    private fun commitThenStart(c: Char): String {
        val out = composing
        clear()
        cho = c
        return out
    }

    private fun clear() {
        cho = null
        jung = null
        jongA = null
        jongB = null
    }
}
