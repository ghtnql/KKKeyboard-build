package com.ghtnql.kkkeyboard

import com.ghtnql.kkkeyboard.sharedcore.CandidateInputTokenBuffer

/** Android adapter for the shared candidate input token buffer. */
class CandidateInputBuffer {
    private val shared = CandidateInputTokenBuffer()

    fun apply(edit: HangulComposer.Edit) {
        shared.applyCommitted(edit.commit)
    }

    fun removeCommittedCodePoint() = shared.removeLastCommittedCharacter()

    fun hasCommittedToken(): Boolean = shared.hasCommittedToken()

    fun replaceCurrent(value: String) = shared.replaceCurrent(value)

    fun current(composing: String): String = shared.current(composing)

    fun currentForLookup(composing: String, maxLength: Int): String? =
        shared.currentForLookup(composing, maxLength)

    fun clear() = shared.clear()
}
