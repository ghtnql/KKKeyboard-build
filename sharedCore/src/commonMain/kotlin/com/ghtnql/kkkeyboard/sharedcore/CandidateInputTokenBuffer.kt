package com.ghtnql.kkkeyboard.sharedcore

/** Tracks the committed Hangul span that may be used for candidate lookup. */
class CandidateInputTokenBuffer {
    private val token = StringBuilder()

    fun applyCommitted(value: String) {
        value.forEach { character ->
            if (character.isHangulSyllableOrJamo()) token.append(character) else clear()
        }
    }

    fun removeLastCommittedCharacter() {
        if (token.isNotEmpty()) token.deleteAt(token.lastIndex)
    }

    fun hasCommittedToken(): Boolean = token.isNotEmpty()

    fun replaceCurrent(value: String) {
        token.setLength(0)
        token.append(value)
    }

    fun current(composing: String): String = token.toString() + composing

    /** Checks the bound before allocating the combined lookup string. */
    fun currentForLookup(composing: String, maxLength: Int): String? {
        if (maxLength <= 0 || token.length + composing.length > maxLength) return null
        return current(composing)
    }

    fun clear() {
        token.setLength(0)
    }

    private fun Char.isHangulSyllableOrJamo(): Boolean =
        this in '\uAC00'..'\uD7A3' || this in '\u3131'..'\u318E'
}
