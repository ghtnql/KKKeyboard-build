package com.ghtnql.kkkeyboard.sharedcore

/** Opt-in matching for Hangul approximations of Japanese pronunciation. */
class JapanesePronunciationMatcher {
    fun canonicalize(input: String): String = buildString(input.length) {
        var previous: Syllable? = null
        input.forEach { raw ->
            val syllable = decompose(raw)
            if (syllable == null) {
                append(raw)
                previous = null
                return@forEach
            }
            if (previous?.let { isLongVowelExtension(it, syllable) } == true) return@forEach
            val canonical = syllable.copy(initial = when (syllable.initial) { 15 -> 0; 16 -> 3; else -> syllable.initial })
            append(compose(canonical))
            previous = canonical
        }
    }

    fun matches(input: String, expected: String): Boolean = canonicalize(input) == canonicalize(expected)

    fun matchesPrefix(input: String, expected: String): Boolean = canonicalize(expected).startsWith(canonicalize(input))

    private fun isLongVowelExtension(previous: Syllable, current: Syllable): Boolean {
        if (previous.final != 0 || current.initial != 11 || current.final != 0) return false
        return current.medial in when (previous.medial) {
            0, 2, 9 -> setOf(0)
            1, 3, 10 -> setOf(1, 20)
            4, 6, 14 -> setOf(4)
            5, 7, 11, 15 -> setOf(5, 20)
            8, 12 -> setOf(8, 13)
            13, 17 -> setOf(13)
            16, 19, 20 -> setOf(20)
            18 -> setOf(18)
            else -> emptySet()
        }
    }

    private data class Syllable(val initial: Int, val medial: Int, val final: Int)

    private fun decompose(character: Char): Syllable? {
        if (character !in '\uAC00'..'\uD7A3') return null
        val value = character.code - 0xAC00
        return Syllable(value / 588, (value % 588) / 28, value % 28)
    }

    private fun compose(syllable: Syllable): Char =
        (0xAC00 + syllable.initial * 588 + syllable.medial * 28 + syllable.final).toChar()
}
