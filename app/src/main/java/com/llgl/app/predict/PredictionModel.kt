package com.llgl.app.predict

import com.llgl.app.hangul.Jamo

/**
 * Word prediction learned only from what the user types: decayed unigram counts for ranking,
 * bigram counts for "what usually follows", and jamo-level prefix matching so a half-typed
 * syllable still finds the word. Pure Kotlin; the IME persists it with [save]/[load].
 */
class PredictionModel(private val maxWords: Int = 20_000) {
    private val unigram = HashMap<String, Float>()
    private val keys = HashMap<String, String>()
    private val byFirst = HashMap<Char, MutableSet<String>>()
    private val bigram = HashMap<String, HashMap<String, Float>>()
    private var learnsSinceDecay = 0

    val size: Int get() = unigram.size

    fun learn(word: String, previous: String? = null) {
        if (!isLearnable(word)) return
        add(word, 1f)
        if (previous != null && isLearnable(previous)) {
            val followers = bigram.getOrPut(previous) { HashMap() }
            followers[word] = (followers[word] ?: 0f) + 1f
        }
        if (++learnsSinceDecay >= DECAY_EVERY) {
            learnsSinceDecay = 0
            decay()
        }
        if (unigram.size > maxWords) prune()
    }

    /** Words that start with [prefix] (at jamo level), best first. An empty prefix gives [next]. */
    fun suggest(prefix: String, previous: String? = null, limit: Int = 4): List<String> {
        if (prefix.isEmpty()) return next(previous, limit)
        val prefixKey = Jamo.key(prefix)
        if (prefixKey.isEmpty()) return emptyList()
        val pool = byFirst[prefixKey.first()] ?: return emptyList()
        val followers = previous?.let { bigram[it] }
        return pool.asSequence()
            .filter { word ->
                val key = keys[word] ?: return@filter false
                key.length > prefixKey.length && key.startsWith(prefixKey)
            }
            .map { word -> word to ((unigram[word] ?: 0f) + BIGRAM_WEIGHT * (followers?.get(word) ?: 0f)) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Words that usually follow [previous]. */
    fun next(previous: String?, limit: Int = 4): List<String> {
        val followers = previous?.let { bigram[it] } ?: return emptyList()
        return followers.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun forget(word: String) {
        unigram.remove(word)
        keys.remove(word)?.let { key -> byFirst[key.first()]?.remove(word) }
        bigram.remove(word)
        for (followers in bigram.values) followers.remove(word)
    }

    /** Halves the weight of everything so recent habits win; drops words that faded away. */
    fun decay(factor: Float = 0.5f) {
        val dead = ArrayList<String>()
        for (entry in unigram.entries) {
            entry.setValue(entry.value * factor)
            if (entry.value < MIN_COUNT) dead += entry.key
        }
        dead.forEach(::forget)
        for (followers in bigram.values) {
            val it = followers.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                e.setValue(e.value * factor)
                if (e.value < MIN_COUNT) it.remove()
            }
        }
    }

    fun save(): String = buildString {
        for ((word, count) in unigram) append("U\t").append(word).append('\t').append(count).append('\n')
        for ((prev, followers) in bigram) {
            for ((word, count) in followers) append("B\t").append(prev).append('\t').append(word).append('\t').append(count).append('\n')
        }
    }

    fun load(text: String) {
        for (line in text.lineSequence()) {
            val parts = line.split('\t')
            when {
                parts.size == 3 && parts[0] == "U" -> parts[2].toFloatOrNull()?.let { if (isLearnable(parts[1])) add(parts[1], it) }
                parts.size == 4 && parts[0] == "B" -> parts[3].toFloatOrNull()?.let { bigram.getOrPut(parts[1]) { HashMap() }[parts[2]] = it }
            }
        }
    }

    private fun add(word: String, count: Float) {
        unigram[word] = (unigram[word] ?: 0f) + count
        val key = keys.getOrPut(word) { Jamo.key(word) }
        byFirst.getOrPut(key.first()) { HashSet() }.add(word)
    }

    private fun prune() {
        val keep = unigram.entries.sortedByDescending { it.value }.take(maxWords).map { it.key }.toHashSet()
        for (word in unigram.keys.toList()) if (word !in keep) forget(word)
    }

    companion object {
        const val DECAY_EVERY = 500
        const val BIGRAM_WEIGHT = 3f
        const val MIN_COUNT = 0.1f

        /** Words worth remembering: Hangul of any length, or Latin words of two or more letters. No digits, spaces or URLs. */
        fun isLearnable(word: String): Boolean {
            if (word.isEmpty() || word.length > 24) return false
            if (word.any { it.isDigit() || it.isWhitespace() || it == '/' || it == '@' }) return false
            if (word.any { Jamo.isSyllable(it) }) return true
            return word.length >= 2 && word.all { it.isLetter() || it == '\'' }
        }
    }
}
