package com.llgl.app.hangul

/** Hangul jamo tables and syllable (de)composition, using the compatibility jamo block for letters. */
object Jamo {
    const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val JUNGSEONG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"

    /** Index 0 (a space) means "no final consonant". */
    const val JONGSEONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val VOWEL_PAIRS: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㅗ' to 'ㅏ') to 'ㅘ', ('ㅗ' to 'ㅐ') to 'ㅙ', ('ㅗ' to 'ㅣ') to 'ㅚ',
        ('ㅜ' to 'ㅓ') to 'ㅝ', ('ㅜ' to 'ㅔ') to 'ㅞ', ('ㅜ' to 'ㅣ') to 'ㅟ',
        ('ㅡ' to 'ㅣ') to 'ㅢ',
    )
    private val FINAL_PAIRS: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ',
        ('ㄹ' to 'ㄱ') to 'ㄺ', ('ㄹ' to 'ㅁ') to 'ㄻ', ('ㄹ' to 'ㅂ') to 'ㄼ', ('ㄹ' to 'ㅅ') to 'ㄽ',
        ('ㄹ' to 'ㅌ') to 'ㄾ', ('ㄹ' to 'ㅍ') to 'ㄿ', ('ㄹ' to 'ㅎ') to 'ㅀ', ('ㅂ' to 'ㅅ') to 'ㅄ',
    )
    private val VOWEL_SPLIT: Map<Char, Pair<Char, Char>> = VOWEL_PAIRS.entries.associate { it.value to it.key }
    private val FINAL_SPLIT: Map<Char, Pair<Char, Char>> = FINAL_PAIRS.entries.associate { it.value to it.key }

    fun isConsonant(c: Char): Boolean = CHOSEONG.indexOf(c) >= 0
    fun isVowel(c: Char): Boolean = JUNGSEONG.indexOf(c) >= 0
    fun isSyllable(c: Char): Boolean = c in '가'..'힣'
    fun canBeFinal(c: Char): Boolean = JONGSEONG.indexOf(c) > 0

    fun combineVowels(a: Char, b: Char): Char? = VOWEL_PAIRS[a to b]
    fun combineFinals(a: Char, b: Char): Char? = FINAL_PAIRS[a to b]
    fun splitVowel(v: Char): Pair<Char, Char>? = VOWEL_SPLIT[v]
    fun splitFinal(f: Char): Pair<Char, Char>? = FINAL_SPLIT[f]

    fun compose(cho: Char, jung: Char, jong: Char? = null): Char {
        val ci = CHOSEONG.indexOf(cho)
        val ji = JUNGSEONG.indexOf(jung)
        val oi = if (jong == null) 0 else JONGSEONG.indexOf(jong)
        require(ci >= 0 && ji >= 0 && oi >= 0) { "not composable: $cho $jung $jong" }
        return (0xAC00 + (ci * 21 + ji) * 28 + oi).toChar()
    }

    /** (initial, medial, final-or-null) of a precomposed syllable, or null for anything else. */
    fun decompose(c: Char): Triple<Char, Char, Char?>? {
        if (!isSyllable(c)) return null
        val code = c.code - 0xAC00
        val cho = CHOSEONG[code / (21 * 28)]
        val jung = JUNGSEONG[(code % (21 * 28)) / 28]
        val jongIndex = code % 28
        return Triple(cho, jung, if (jongIndex == 0) null else JONGSEONG[jongIndex])
    }

    /**
     * Flattens text to a jamo-level string for prefix matching: syllables become their jamo,
     * compound vowels and finals are split into their parts, Latin letters are lower-cased.
     * "가" is then a prefix of "강아지", and "오" a prefix of "왜".
     */
    fun key(text: String): String = buildString {
        for (ch in text) {
            val parts = decompose(ch)
            if (parts == null) {
                when {
                    isVowel(ch) -> appendVowel(ch)
                    FINAL_SPLIT.containsKey(ch) -> appendFinal(ch)
                    else -> append(ch.lowercaseChar())
                }
                continue
            }
            val (cho, jung, jong) = parts
            append(cho)
            appendVowel(jung)
            if (jong != null) appendFinal(jong)
        }
    }

    private fun StringBuilder.appendVowel(v: Char) {
        val split = VOWEL_SPLIT[v]
        if (split != null) {
            append(split.first)
            append(split.second)
        } else {
            append(v)
        }
    }

    private fun StringBuilder.appendFinal(f: Char) {
        val split = FINAL_SPLIT[f]
        if (split != null) {
            append(split.first)
            append(split.second)
        } else {
            append(f)
        }
    }
}
