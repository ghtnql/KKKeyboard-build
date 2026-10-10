package com.ghtnql.kkkeyboard

/** Keeps the entire reading editable until a word candidate is committed. */
class JapaneseWordComposer {
    private val composer = HangulComposer()
    private val keys = StringBuilder()
    private val prefix = StringBuilder()
    private val syllableBreaks = mutableSetOf<Int>()

    val text: String
        get() = prefix.toString() + composer.currentText()

    val isActive: Boolean
        get() = keys.isNotEmpty()

    fun input(character: Char): Boolean {
        keys.append(character)
        prefix.append(composer.input(character).commit)
        if (text.length <= JapaneseTransliterator.maxInputLength) return true
        backspace()
        return false
    }

    fun backspace(): Boolean {
        if (keys.isEmpty()) return false
        keys.deleteCharAt(keys.lastIndex)
        syllableBreaks.removeAll { it > keys.length }
        replay()
        return true
    }

    /** Keeps the reading active while fixing the current Cheonjiin syllable. */
    fun finishSyllable(): Boolean {
        if (keys.isEmpty() || !syllableBreaks.add(keys.length)) return false
        replay()
        return true
    }

    fun replaceLast(character: Char): Boolean {
        if (keys.isEmpty()) return false
        keys.setCharAt(keys.lastIndex, character)
        replay()
        return true
    }

    private fun replay() {
        // Replay this bounded reading so edits can cross syllable boundaries.
        composer.reset()
        prefix.setLength(0)
        keys.forEachIndexed { index, key ->
            if (index in syllableBreaks) prefix.append(composer.flush())
            prefix.append(composer.input(key).commit)
        }
        if (keys.length in syllableBreaks) prefix.append(composer.flush())
    }

    fun candidates(): List<String> = JapaneseTransliterator.candidatesExact(text)

    fun suggestions(): List<String> = JapaneseTransliterator.suggestionsExact(text)

    fun reset() {
        keys.setLength(0)
        prefix.setLength(0)
        syllableBreaks.clear()
        composer.reset()
    }
}
