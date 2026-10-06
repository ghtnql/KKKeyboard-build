package com.ghtnql.kkkeyboard

enum class CheonjiinKey(
    val label: String,
    val consonants: String = "",
    val vowelStroke: Char? = null,
) {
    I("ㅣ", vowelStroke = 'ㅣ'),
    DOT("·", vowelStroke = '·'),
    EU("ㅡ", vowelStroke = 'ㅡ'),
    GIYEOK("ㄱㅋ", "ㄱㅋㄲ"),
    NIEUN("ㄴㄹ", "ㄴㄹ"),
    DIGEUT("ㄷㅌ", "ㄷㅌㄸ"),
    BIEUP("ㅂㅍ", "ㅂㅍㅃ"),
    SIOT("ㅅㅎ", "ㅅㅎㅆ"),
    JIEUT("ㅈㅊ", "ㅈㅊㅉ"),
    IEUNG("ㅇㅁ", "ㅇㅁ"),
}

sealed class CheonjiinAction {
    object None : CheonjiinAction()
    data class Append(val character: Char) : CheonjiinAction()
    data class ReplaceLast(val character: Char) : CheonjiinAction()
    object RemoveLast : CheonjiinAction()
}

/**
 * Converts Samsung-style 3x4 Cheonjiin taps into modern Hangul jamo edits.
 * The editor-facing composer stays layout-independent.
 */
class CheonjiinInput {
    private val vowelStrokes = StringBuilder()
    private var vowelEmitted = false
    private var lastConsonant: CheonjiinKey? = null
    private var consonantIndex = 0
    private var lastConsonantAt = Long.MIN_VALUE
    private var lastDirectConsonant: Char? = null
    private var lastDirectConsonantAt = Long.MIN_VALUE

    fun input(key: CheonjiinKey, nowMillis: Long, cycleTimeoutMillis: Int): CheonjiinAction {
        val stroke = key.vowelStroke
        return if (stroke != null) inputVowelStroke(stroke) else inputConsonant(key, nowMillis, cycleTimeoutMillis)
    }

    /** Split keys bypass grouped-key cycling while preserving the same vowel/composition pipeline. */
    fun inputDirectConsonant(character: Char): CheonjiinAction {
        require(character in "ㄱㅋㄴㄹㄷㅌㅂㅍㅅㅎㅈㅊㅇㅁㄲㄸㅃㅆㅉ")
        reset()
        return CheonjiinAction.Append(character)
    }

    /**
     * Plus-layout tap handling. A quick second tap on ㄱ/ㄷ/ㅂ/ㅅ/ㅈ replaces
     * the first jamo with its tense consonant. Other direct keys never cycle.
     */
    fun inputDirectConsonant(character: Char, nowMillis: Long, doubleTapTimeoutMillis: Int): CheonjiinAction {
        require(character in "ㄱㅋㄴㄹㄷㅌㅂㅍㅅㅎㅈㅊㅇㅁ")
        resetVowel()
        lastConsonant = null
        consonantIndex = 0
        lastConsonantAt = Long.MIN_VALUE

        val tense = TENSE_BY_BASE[character]
        val canDoubleTap = tense != null &&
            character == lastDirectConsonant &&
            nowMillis - lastDirectConsonantAt <= doubleTapTimeoutMillis
        return if (canDoubleTap) {
            lastDirectConsonant = null
            lastDirectConsonantAt = Long.MIN_VALUE
            CheonjiinAction.ReplaceLast(tense)
        } else {
            lastDirectConsonant = if (tense != null) character else null
            lastDirectConsonantAt = if (tense != null) nowMillis else Long.MIN_VALUE
            CheonjiinAction.Append(character)
        }
    }

    /** Returns null when normal editor backspace should run. */
    fun backspace(): CheonjiinAction? {
        if (vowelStrokes.isEmpty()) {
            reset()
            return null
        }
        val hadEmittedVowel = vowelEmitted
        vowelStrokes.deleteCharAt(vowelStrokes.lastIndex)
        val previousVowel = VOWELS[vowelStrokes.toString()]
        vowelEmitted = previousVowel != null
        return when {
            !hadEmittedVowel -> CheonjiinAction.None
            previousVowel != null -> CheonjiinAction.ReplaceLast(previousVowel)
            else -> CheonjiinAction.RemoveLast
        }
    }

    fun reset() {
        resetVowel()
        lastConsonant = null
        consonantIndex = 0
        lastConsonantAt = Long.MIN_VALUE
        lastDirectConsonant = null
        lastDirectConsonantAt = Long.MIN_VALUE
    }

    private fun inputConsonant(key: CheonjiinKey, nowMillis: Long, cycleTimeoutMillis: Int): CheonjiinAction {
        require(key.consonants.isNotEmpty())
        resetVowel()
        lastDirectConsonant = null
        lastDirectConsonantAt = Long.MIN_VALUE
        val canCycle = key == lastConsonant && nowMillis - lastConsonantAt <= cycleTimeoutMillis
        consonantIndex = if (canCycle) (consonantIndex + 1) % key.consonants.length else 0
        lastConsonant = key
        lastConsonantAt = nowMillis
        val character = key.consonants[consonantIndex]
        return if (canCycle) CheonjiinAction.ReplaceLast(character) else CheonjiinAction.Append(character)
    }

    private fun inputVowelStroke(stroke: Char): CheonjiinAction {
        lastConsonant = null
        lastDirectConsonant = null
        lastDirectConsonantAt = Long.MIN_VALUE
        val candidate = vowelStrokes.toString() + stroke
        if (VOWEL_PREFIXES.contains(candidate)) return acceptVowelSequence(candidate)

        // The old sequence is already represented in the editor. Start a new vowel.
        resetVowel()
        return acceptVowelSequence(stroke.toString())
    }

    private fun acceptVowelSequence(sequence: String): CheonjiinAction {
        if (!VOWEL_PREFIXES.contains(sequence)) return CheonjiinAction.None
        val hadEmittedVowel = vowelEmitted
        vowelStrokes.append(sequence.substring(vowelStrokes.length))
        val vowel = VOWELS[sequence] ?: return CheonjiinAction.None
        vowelEmitted = true
        return if (hadEmittedVowel) CheonjiinAction.ReplaceLast(vowel) else CheonjiinAction.Append(vowel)
    }

    private fun resetVowel() {
        vowelStrokes.setLength(0)
        vowelEmitted = false
    }

    companion object {
        val rows = listOf(
            listOf(CheonjiinKey.I, CheonjiinKey.DOT, CheonjiinKey.EU),
            listOf(CheonjiinKey.GIYEOK, CheonjiinKey.NIEUN, CheonjiinKey.DIGEUT),
            listOf(CheonjiinKey.BIEUP, CheonjiinKey.SIOT, CheonjiinKey.JIEUT),
            listOf(CheonjiinKey.IEUNG),
        )

        private val TENSE_BY_BASE = mapOf(
            'ㄱ' to 'ㄲ', 'ㄷ' to 'ㄸ', 'ㅂ' to 'ㅃ', 'ㅅ' to 'ㅆ', 'ㅈ' to 'ㅉ',
        )

        private val VOWELS = mapOf(
            "ㅣ" to 'ㅣ', "ㅡ" to 'ㅡ',
            "ㅣ·" to 'ㅏ', "ㅣ··" to 'ㅑ', "·ㅣ" to 'ㅓ', "··ㅣ" to 'ㅕ',
            "·ㅡ" to 'ㅗ', "··ㅡ" to 'ㅛ', "ㅡ·" to 'ㅜ', "ㅡ··" to 'ㅠ',
            "ㅣ·ㅣ" to 'ㅐ', "ㅣ··ㅣ" to 'ㅒ', "·ㅣㅣ" to 'ㅔ', "··ㅣㅣ" to 'ㅖ',
            "·ㅡㅣ·" to 'ㅘ', "·ㅡㅣ·ㅣ" to 'ㅙ', "·ㅡㅣ" to 'ㅚ',
            "ㅡ··ㅣ" to 'ㅝ', "ㅡ··ㅣㅣ" to 'ㅞ', "ㅡ·ㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ',
        )
        private val VOWEL_PREFIXES = buildSet {
            VOWELS.keys.forEach { sequence ->
                for (length in 1..sequence.length) add(sequence.substring(0, length))
            }
        }
    }
}
