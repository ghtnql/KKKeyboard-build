package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseWordComposerTest {
    @Test
    fun `typing several syllables keeps the whole word available for conversion`() {
        val word = JapaneseWordComposer()
        type(word, "ㅇㅗ")
        assertEquals("오", word.text)
        type(word, "ㅎㅏ")
        assertEquals("오하", word.text)
        type(word, "ㅇㅛㅇㅜ")
        assertEquals("오하요우", word.text)
        assertEquals(listOf("おはよう"), word.candidates())
    }

    @Test
    fun `whole word lookup preserves voiced greeting and long phrase spelling`() {
        val word = JapaneseWordComposer()
        type(word, "ㄱㅗㅈㅏㅇㅣㅁㅏㅅㅡ")
        assertEquals("고자이마스", word.text)
        assertEquals(listOf("ございます"), word.candidates())

        word.reset()
        type(word, "ㅇㅗㅎㅏㅇㅛㄱㅗㅈㅏㅇㅣㅁㅏㅅㅡ")
        assertEquals("오하요고자이마스", word.text)
        assertEquals(listOf("おはようございます"), word.candidates())
    }

    @Test
    fun `word candidates include existing kanji dictionary entries`() {
        val word = JapaneseWordComposer()
        type(word, "ㄴㅣㅎㅗㄴ")
        assertEquals(listOf("にほん", "日本"), word.candidates())
        word.reset()
        type(word, "ㄷㅗㅋㅛ")
        assertEquals(listOf("とうきょう", "東京"), word.candidates())
    }

    @Test
    fun `backspace crosses syllable boundaries without dropping the prefix`() {
        val word = JapaneseWordComposer()
        type(word, "ㅇㅗㅎㅏㅇㅛ")
        assertTrue(word.backspace())
        assertEquals("오항", word.text)
        assertTrue(word.backspace())
        assertEquals("오하", word.text)
        type(word, "ㅇㅛㅇㅜ")
        assertEquals("오하요우", word.text)
        assertEquals(listOf("おはよう"), word.candidates())
    }

    @Test
    fun `deleting final vowel restores previous syllable final consonant`() {
        val word = JapaneseWordComposer()
        type(word, "ㄱㅏㄴㅏ")
        assertEquals("가나", word.text)
        word.backspace()
        assertEquals("간", word.text)
        type(word, "ㅏ")
        assertEquals("가나", word.text)
    }

    @Test
    fun `incomplete reading does not expose a stale shorter word candidate`() {
        val word = JapaneseWordComposer()
        type(word, "ㅇㅗㅎㅏㅇㅛㅃ")
        assertTrue(word.candidates().isEmpty())
        word.backspace()
        type(word, "ㅇㅜ")
        assertEquals(listOf("おはよう"), word.candidates())
    }

    @Test
    fun `reset starts an independent word and empty backspace falls through`() {
        val word = JapaneseWordComposer()
        type(word, "ㅇㅗㅎㅏㅇㅛ")
        word.reset()
        assertEquals("", word.text)
        assertFalse(word.isActive)
        assertFalse(word.backspace())
        type(word, "ㄱㅗㅈㅏㅇㅣㅁㅏㅅㅡ")
        assertEquals("고자이마스", word.text)
    }

    @Test
    fun `overlength input leaves the previous word intact for committing`() {
        val word = JapaneseWordComposer()
        type(word, "ㅇㅏ".repeat(JapaneseTransliterator.maxInputLength))
        val before = word.text
        assertFalse(word.input('ㅏ'))
        assertEquals(before, word.text)
    }

    @Test
    fun `replace last supports multi tap layouts without losing the word`() {
        val word = JapaneseWordComposer()
        type(word, "ㅇㅏㄴㅣ")
        assertTrue(word.replaceLast('ㅏ'))
        assertEquals("아나", word.text)
        assertTrue(word.replaceLast('ㅑ'))
        assertEquals("아냐", word.text)
    }

    private fun type(word: JapaneseWordComposer, keys: String) {
        keys.forEach { assertTrue(word.input(it)) }
    }
}
