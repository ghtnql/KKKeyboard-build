package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HangulComposerTest {
    @Test
    fun composesBasicSyllable() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅏ')
        assertEquals("가", c.currentText())
    }

    @Test
    fun composesFinalConsonant() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅏ')
        c.input('ㄴ')
        assertEquals("간", c.currentText())
    }

    @Test
    fun preservesAllInitialIndexes() {
        val initials = charArrayOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
        )

        initials.forEachIndexed { index, initial ->
            val c = HangulComposer()
            c.input(initial)
            c.input('ㅏ')
            val expected = (0xAC00 + index * 21 * 28).toChar().toString()
            assertEquals(expected, c.currentText())
        }
    }

    @Test
    fun preservesAllMedialIndexes() {
        val vowels = charArrayOf(
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ',
        )

        vowels.forEachIndexed { index, vowel ->
            val c = HangulComposer()
            c.input('ㄱ')
            c.input(vowel)
            val expected = (0xAC00 + index * 28).toChar().toString()
            assertEquals(expected, c.currentText())
        }
    }

    @Test
    fun preservesAllFinalIndexesThroughRealKeySequences() {
        val finalKeySequences = listOf(
            charArrayOf('ㄱ'),
            charArrayOf('ㄲ'),
            charArrayOf('ㄱ', 'ㅅ'),
            charArrayOf('ㄴ'),
            charArrayOf('ㄴ', 'ㅈ'),
            charArrayOf('ㄴ', 'ㅎ'),
            charArrayOf('ㄷ'),
            charArrayOf('ㄹ'),
            charArrayOf('ㄹ', 'ㄱ'),
            charArrayOf('ㄹ', 'ㅁ'),
            charArrayOf('ㄹ', 'ㅂ'),
            charArrayOf('ㄹ', 'ㅅ'),
            charArrayOf('ㄹ', 'ㅌ'),
            charArrayOf('ㄹ', 'ㅍ'),
            charArrayOf('ㄹ', 'ㅎ'),
            charArrayOf('ㅁ'),
            charArrayOf('ㅂ'),
            charArrayOf('ㅂ', 'ㅅ'),
            charArrayOf('ㅅ'),
            charArrayOf('ㅆ'),
            charArrayOf('ㅇ'),
            charArrayOf('ㅈ'),
            charArrayOf('ㅊ'),
            charArrayOf('ㅋ'),
            charArrayOf('ㅌ'),
            charArrayOf('ㅍ'),
            charArrayOf('ㅎ'),
        )

        finalKeySequences.forEachIndexed { index, keys ->
            val c = HangulComposer()
            c.input('ㄱ')
            c.input('ㅏ')
            keys.forEach(c::input)
            val expected = (0xAC00 + index + 1).toChar().toString()
            assertEquals(expected, c.currentText())
        }
    }

    @Test
    fun movesSimpleFinalToNextSyllableWhenVowelFollows() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅏ')
        c.input('ㄴ')
        val edit = c.input('ㅏ')
        assertEquals("가", edit.commit)
        assertEquals("나", edit.composing)
    }

    @Test
    fun composesCompoundVowel() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅗ')
        c.input('ㅏ')
        assertEquals("과", c.currentText())
    }

    @Test
    fun preservesAllCompoundVowelMappings() {
        val cases = listOf(
            Triple('ㅗ', 'ㅏ', "과"),
            Triple('ㅗ', 'ㅐ', "괘"),
            Triple('ㅗ', 'ㅣ', "괴"),
            Triple('ㅜ', 'ㅓ', "궈"),
            Triple('ㅜ', 'ㅔ', "궤"),
            Triple('ㅜ', 'ㅣ', "귀"),
            Triple('ㅡ', 'ㅣ', "긔"),
        )

        cases.forEach { (first, second, expected) ->
            val c = HangulComposer()
            c.input('ㄱ')
            c.input(first)
            c.input(second)
            assertEquals(expected, c.currentText())
        }
    }

    @Test
    fun composesCompoundFinalAndSplitsItBeforeVowel() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅏ')
        c.input('ㅂ')
        c.input('ㅅ')
        assertEquals("값", c.currentText())

        val edit = c.input('ㅏ')
        assertEquals("갑", edit.commit)
        assertEquals("사", edit.composing)
    }

    @Test
    fun preservesAllCompoundFinalMappings() {
        val cases = listOf(
            Triple('ㄱ', 'ㅅ', "갃"),
            Triple('ㄴ', 'ㅈ', "갅"),
            Triple('ㄴ', 'ㅎ', "갆"),
            Triple('ㄹ', 'ㄱ', "갉"),
            Triple('ㄹ', 'ㅁ', "갊"),
            Triple('ㄹ', 'ㅂ', "갋"),
            Triple('ㄹ', 'ㅅ', "갌"),
            Triple('ㄹ', 'ㅌ', "갍"),
            Triple('ㄹ', 'ㅍ', "갎"),
            Triple('ㄹ', 'ㅎ', "갏"),
            Triple('ㅂ', 'ㅅ', "값"),
        )

        cases.forEach { (first, second, expected) ->
            val c = HangulComposer()
            c.input('ㄱ')
            c.input('ㅏ')
            c.input(first)
            c.input(second)
            assertEquals(expected, c.currentText())
        }
    }

    @Test
    fun standaloneVowelRemainsJamo() {
        val c = HangulComposer()
        c.input('ㅏ')
        assertEquals("ㅏ", c.currentText())
    }

    @Test
    fun backspaceWalksCompoundVowelBack() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅗ')
        c.input('ㅏ')
        assertEquals("고", c.backspace().composing)
        assertEquals("ㄱ", c.backspace().composing)
    }

    @Test
    fun backspaceWalksCompositionBack() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅏ')
        c.input('ㄴ')
        assertEquals("가", c.backspace().composing)
        assertEquals("ㄱ", c.backspace().composing)
        val last = c.backspace()
        assertEquals(null, last.composing)
        assertFalse(c.backspace().consumed)
    }

    @Test
    fun replacesCurrentJamoWithoutSplittingCompoundVowels() {
        val c = HangulComposer()
        c.input('ㄱ')
        c.input('ㅘ')
        assertEquals("괘", c.replaceLast('ㅙ').composing)
        assertEquals("괴", c.replaceLast('ㅚ').composing)
        c.input('ㄱ')
        assertEquals("괵", c.currentText())
        assertEquals("괸", c.replaceLast('ㄴ').composing)
    }

    @Test
    fun repeatedFastCompositionDoesNotLoseOrLeakState() {
        val c = HangulComposer()
        val output = StringBuilder()

        repeat(2_000) {
            c.input('ㅎ')
            c.input('ㅏ')
            c.input('ㄴ')
            output.append(c.flush())
            assertEquals("", c.currentText())
        }

        assertEquals(2_000, output.length)
        assertEquals("한".repeat(2_000), output.toString())
    }

    @Test
    fun cheonjiinReplayChangesACommittedInitialIntoCompoundFinal() {
        val c = CheonjiinComposer()
        "ㅇㅏㄴㅅ".forEach(c::append)
        assertEquals("안ㅅ", c.text)
        assertEquals(true, c.replaceLast('ㅎ'))
        assertEquals("않", c.text)
        assertEquals(true, c.removeLast())
        assertEquals("안", c.text)
        c.reset()
        assertEquals("", c.text)
        assertEquals(false, c.replaceLast('ㅎ'))
    }
}
