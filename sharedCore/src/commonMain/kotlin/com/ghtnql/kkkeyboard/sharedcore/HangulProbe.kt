package com.ghtnql.kkkeyboard.sharedcore

/** Unicode Hangul syllable composition POC for both native keyboard shells. */
class HangulProbe {
    fun compose(initialIndex: Int, vowelIndex: Int, finalIndex: Int = 0): String {
        require(initialIndex in 0..18 && vowelIndex in 0..20 && finalIndex in 0..27)
        return (0xAC00 + (initialIndex * 21 + vowelIndex) * 28 + finalIndex).toChar().toString()
    }
}
