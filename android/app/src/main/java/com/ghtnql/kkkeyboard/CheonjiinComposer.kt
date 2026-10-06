package com.ghtnql.kkkeyboard

/** Keeps the recent Cheonjiin jamo in one editor composing span while a key can cycle. */
internal class CheonjiinComposer {
    private val jamo = StringBuilder()
    private val composer = HangulComposer()

    val isActive: Boolean get() = jamo.isNotEmpty()
    val isFull: Boolean get() = jamo.length >= MAX_JAMO
    var text: String = ""
        private set

    fun append(character: Char) {
        jamo.append(character)
        replay()
    }

    fun replaceLast(character: Char): Boolean {
        if (jamo.isEmpty()) return false
        jamo.setCharAt(jamo.lastIndex, character)
        replay()
        return true
    }

    fun removeLast(): Boolean {
        if (jamo.isEmpty()) return false
        jamo.deleteCharAt(jamo.lastIndex)
        replay()
        return true
    }

    fun reset() {
        jamo.setLength(0)
        composer.reset()
        text = ""
    }

    private fun replay() {
        composer.reset()
        val result = StringBuilder()
        jamo.forEach { result.append(composer.input(it).commit) }
        result.append(composer.currentText())
        text = result.toString()
    }

    private companion object {
        // A bounded composing span prevents an entire long document becoming one IME edit.
        const val MAX_JAMO = 128
    }
}
