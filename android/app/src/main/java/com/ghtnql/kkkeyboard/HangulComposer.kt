package com.ghtnql.kkkeyboard

import com.ghtnql.kkkeyboard.sharedcore.HangulProbe

class HangulComposer {
    private val sharedSyllableComposer = HangulProbe()
    data class Edit(
        val commit: String = "",
        val composing: String? = null,
        val consumed: Boolean = true,
    )

    private var initial = -1
    private var medial = -1
    private var finalIndex = -1

    fun input(ch: Char): Edit {
        val initialIndex = initialIndexOf(ch)
        if (initialIndex >= 0) return inputConsonant(ch, initialIndex)

        val medialIndex = medialIndexOf(ch)
        if (medialIndex >= 0) return inputVowel(ch, medialIndex)

        val pending = flush()
        return Edit(commit = pending + ch, composing = null)
    }

    fun backspace(): Edit {
        when {
            finalIndex > 0 -> {
                val currentFinal = FINALS[finalIndex]
                val split = COMPOUND_FINAL_SPLIT[currentFinal]
                finalIndex = if (split != null) finalIndexOf(split.first) else -1
            }
            medial >= 0 -> {
                val currentMedial = VOWELS[medial]
                val base = COMPOUND_MEDIAL_BASE[currentMedial]
                medial = if (base != null) medialIndexOf(base) else -1
            }
            initial >= 0 -> initial = -1
            else -> return Edit(consumed = false)
        }
        return Edit(composing = currentText().ifEmpty { null })
    }

    /** Replaces the most recently entered jamo without briefly mutating the editor. */
    fun replaceLast(ch: Char): Edit {
        val newInitial = initialIndexOf(ch)
        val newMedial = medialIndexOf(ch)
        val newFinal = finalIndexOf(ch)
        when {
            finalIndex > 0 && newFinal > 0 -> {
                val split = COMPOUND_FINAL_SPLIT[FINALS[finalIndex]]
                if (split != null) {
                    val compound = compoundFinal(split.first, ch)
                    if (compound != null) {
                        finalIndex = finalIndexOf(compound)
                        return Edit(composing = currentText())
                    }
                    finalIndex = finalIndexOf(split.first)
                    val commit = currentText()
                    initial = newInitial
                    medial = -1
                    finalIndex = -1
                    return Edit(commit = commit, composing = currentText())
                }
                finalIndex = newFinal
                return Edit(composing = currentText())
            }
            medial >= 0 && newMedial >= 0 -> {
                medial = newMedial
                return Edit(composing = currentText())
            }
            initial >= 0 && medial < 0 && newInitial >= 0 -> {
                initial = newInitial
                return Edit(composing = currentText())
            }
        }
        backspace()
        return input(ch)
    }

    fun flush(): String {
        val text = currentText()
        clear()
        return text
    }

    fun reset() = clear()

    fun currentText(): String {
        if (initial < 0) {
            return if (medial >= 0) VOWELS[medial].toString() else ""
        }
        if (medial < 0) return INITIALS[initial].toString()

        val jong = if (finalIndex > 0) finalIndex else 0
        return sharedSyllableComposer.compose(initial, medial, jong)
    }

    private fun inputConsonant(ch: Char, newInitial: Int): Edit {
        if (initial < 0) {
            if (medial >= 0) {
                val commit = currentText()
                medial = -1
                initial = newInitial
                return Edit(commit = commit, composing = currentText())
            }
            initial = newInitial
            return Edit(composing = currentText())
        }

        if (medial < 0) {
            val commit = currentText()
            initial = newInitial
            return Edit(commit = commit, composing = currentText())
        }

        if (finalIndex < 0) {
            val candidateFinal = finalIndexOf(ch)
            if (candidateFinal > 0) {
                finalIndex = candidateFinal
                return Edit(composing = currentText())
            }
        } else {
            val currentFinal = FINALS[finalIndex]
            val compound = compoundFinal(currentFinal, ch)
            if (compound != null) {
                finalIndex = finalIndexOf(compound)
                return Edit(composing = currentText())
            }
        }

        val commit = currentText()
        initial = newInitial
        medial = -1
        finalIndex = -1
        return Edit(commit = commit, composing = currentText())
    }

    private fun inputVowel(ch: Char, newMedial: Int): Edit {
        if (initial < 0) {
            if (medial < 0) {
                medial = newMedial
                return Edit(composing = currentText())
            }

            val compound = compoundMedial(VOWELS[medial], ch)
            if (compound != null) {
                medial = medialIndexOf(compound)
                return Edit(composing = currentText())
            }

            val commit = currentText()
            medial = newMedial
            return Edit(commit = commit, composing = currentText())
        }

        if (medial < 0) {
            medial = newMedial
            return Edit(composing = currentText())
        }

        if (finalIndex > 0) {
            val finalChar = FINALS[finalIndex]
            val split = COMPOUND_FINAL_SPLIT[finalChar]

            if (split != null) {
                finalIndex = finalIndexOf(split.first)
                val commit = currentText()
                initial = initialIndexOf(split.second)
                medial = newMedial
                finalIndex = -1
                return Edit(commit = commit, composing = currentText())
            }

            finalIndex = -1
            val commit = currentText()
            initial = initialIndexOf(finalChar)
            medial = newMedial
            finalIndex = -1
            return Edit(commit = commit, composing = currentText())
        }

        val compound = compoundMedial(VOWELS[medial], ch)
        if (compound != null) {
            medial = medialIndexOf(compound)
            return Edit(composing = currentText())
        }

        val commit = currentText()
        initial = -1
        medial = newMedial
        finalIndex = -1
        return Edit(commit = commit, composing = currentText())
    }

    private fun clear() {
        initial = -1
        medial = -1
        finalIndex = -1
    }

    companion object {
        private val INITIALS = charArrayOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        private val VOWELS = charArrayOf(
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
        )

        private val FINALS = charArrayOf(
            '\u0000', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
            'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
        )

        /** O(1) hot-path lookup; avoids scanning INITIALS for every key input. */
        private fun initialIndexOf(ch: Char): Int = when (ch) {
            'ㄱ' -> 0
            'ㄲ' -> 1
            'ㄴ' -> 2
            'ㄷ' -> 3
            'ㄸ' -> 4
            'ㄹ' -> 5
            'ㅁ' -> 6
            'ㅂ' -> 7
            'ㅃ' -> 8
            'ㅅ' -> 9
            'ㅆ' -> 10
            'ㅇ' -> 11
            'ㅈ' -> 12
            'ㅉ' -> 13
            'ㅊ' -> 14
            'ㅋ' -> 15
            'ㅌ' -> 16
            'ㅍ' -> 17
            'ㅎ' -> 18
            else -> -1
        }

        /** O(1) hot-path lookup; avoids scanning VOWELS for every key input. */
        private fun medialIndexOf(ch: Char): Int = when (ch) {
            'ㅏ' -> 0
            'ㅐ' -> 1
            'ㅑ' -> 2
            'ㅒ' -> 3
            'ㅓ' -> 4
            'ㅔ' -> 5
            'ㅕ' -> 6
            'ㅖ' -> 7
            'ㅗ' -> 8
            'ㅘ' -> 9
            'ㅙ' -> 10
            'ㅚ' -> 11
            'ㅛ' -> 12
            'ㅜ' -> 13
            'ㅝ' -> 14
            'ㅞ' -> 15
            'ㅟ' -> 16
            'ㅠ' -> 17
            'ㅡ' -> 18
            'ㅢ' -> 19
            'ㅣ' -> 20
            else -> -1
        }

        /** O(1) hot-path lookup; avoids scanning FINALS during composition/splitting. */
        private fun finalIndexOf(ch: Char): Int = when (ch) {
            'ㄱ' -> 1
            'ㄲ' -> 2
            'ㄳ' -> 3
            'ㄴ' -> 4
            'ㄵ' -> 5
            'ㄶ' -> 6
            'ㄷ' -> 7
            'ㄹ' -> 8
            'ㄺ' -> 9
            'ㄻ' -> 10
            'ㄼ' -> 11
            'ㄽ' -> 12
            'ㄾ' -> 13
            'ㄿ' -> 14
            'ㅀ' -> 15
            'ㅁ' -> 16
            'ㅂ' -> 17
            'ㅄ' -> 18
            'ㅅ' -> 19
            'ㅆ' -> 20
            'ㅇ' -> 21
            'ㅈ' -> 22
            'ㅊ' -> 23
            'ㅋ' -> 24
            'ㅌ' -> 25
            'ㅍ' -> 26
            'ㅎ' -> 27
            else -> -1
        }

        /**
         * Hot-path lookup without allocating Pair keys for every vowel input.
         */
        private fun compoundMedial(first: Char, second: Char): Char? = when (first) {
            'ㅗ' -> when (second) {
                'ㅏ' -> 'ㅘ'
                'ㅐ' -> 'ㅙ'
                'ㅣ' -> 'ㅚ'
                else -> null
            }
            'ㅜ' -> when (second) {
                'ㅓ' -> 'ㅝ'
                'ㅔ' -> 'ㅞ'
                'ㅣ' -> 'ㅟ'
                else -> null
            }
            'ㅡ' -> if (second == 'ㅣ') 'ㅢ' else null
            else -> null
        }

        private val COMPOUND_MEDIAL_BASE = mapOf(
            'ㅘ' to 'ㅗ', 'ㅙ' to 'ㅗ', 'ㅚ' to 'ㅗ',
            'ㅝ' to 'ㅜ', 'ㅞ' to 'ㅜ', 'ㅟ' to 'ㅜ',
            'ㅢ' to 'ㅡ',
        )

        /**
         * Hot-path lookup without allocating Pair keys for every consonant input.
         */
        private fun compoundFinal(first: Char, second: Char): Char? = when (first) {
            'ㄱ' -> if (second == 'ㅅ') 'ㄳ' else null
            'ㄴ' -> when (second) {
                'ㅈ' -> 'ㄵ'
                'ㅎ' -> 'ㄶ'
                else -> null
            }
            'ㄹ' -> when (second) {
                'ㄱ' -> 'ㄺ'
                'ㅁ' -> 'ㄻ'
                'ㅂ' -> 'ㄼ'
                'ㅅ' -> 'ㄽ'
                'ㅌ' -> 'ㄾ'
                'ㅍ' -> 'ㄿ'
                'ㅎ' -> 'ㅀ'
                else -> null
            }
            'ㅂ' -> if (second == 'ㅅ') 'ㅄ' else null
            else -> null
        }

        private val COMPOUND_FINAL_SPLIT = mapOf(
            'ㄳ' to ('ㄱ' to 'ㅅ'),
            'ㄵ' to ('ㄴ' to 'ㅈ'),
            'ㄶ' to ('ㄴ' to 'ㅎ'),
            'ㄺ' to ('ㄹ' to 'ㄱ'),
            'ㄻ' to ('ㄹ' to 'ㅁ'),
            'ㄼ' to ('ㄹ' to 'ㅂ'),
            'ㄽ' to ('ㄹ' to 'ㅅ'),
            'ㄾ' to ('ㄹ' to 'ㅌ'),
            'ㄿ' to ('ㄹ' to 'ㅍ'),
            'ㅀ' to ('ㄹ' to 'ㅎ'),
            'ㅄ' to ('ㅂ' to 'ㅅ'),
        )
    }
}
