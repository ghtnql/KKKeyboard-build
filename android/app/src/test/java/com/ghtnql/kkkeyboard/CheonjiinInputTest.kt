package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheonjiinInputTest {
    @Test
    fun `all modern vowels follow Cheonjiin writing order`() {
        val cases = mapOf(
            "ㅣ" to 'ㅣ', "ㅡ" to 'ㅡ',
            "ㅣ·" to 'ㅏ', "ㅣ··" to 'ㅑ', "·ㅣ" to 'ㅓ', "··ㅣ" to 'ㅕ',
            "·ㅡ" to 'ㅗ', "··ㅡ" to 'ㅛ', "ㅡ·" to 'ㅜ', "ㅡ··" to 'ㅠ',
            "ㅣ·ㅣ" to 'ㅐ', "ㅣ··ㅣ" to 'ㅒ', "·ㅣㅣ" to 'ㅔ', "··ㅣㅣ" to 'ㅖ',
            "·ㅡㅣ·" to 'ㅘ', "·ㅡㅣ·ㅣ" to 'ㅙ', "·ㅡㅣ" to 'ㅚ',
            "ㅡ··ㅣ" to 'ㅝ', "ㅡ··ㅣㅣ" to 'ㅞ', "ㅡ·ㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ',
        )

        cases.forEach { (strokes, expected) ->
            val input = CheonjiinInput()
            var current: Char? = null
            strokes.forEachIndexed { index, stroke ->
                val action = input.input(keyForStroke(stroke), index.toLong(), 900)
                when (action) {
                    is CheonjiinAction.Append -> current = action.character
                    is CheonjiinAction.ReplaceLast -> current = action.character
                    CheonjiinAction.None -> Unit
                    CheonjiinAction.RemoveLast -> current = null
                }
            }
            assertEquals("strokes=$strokes", expected, current)
        }
    }

    @Test
    fun `consonants cycle only within configured wait`() {
        val input = CheonjiinInput()
        assertEquals(CheonjiinAction.Append('ㄱ'), input.input(CheonjiinKey.GIYEOK, 0, 900))
        assertEquals(CheonjiinAction.ReplaceLast('ㅋ'), input.input(CheonjiinKey.GIYEOK, 300, 900))
        assertEquals(CheonjiinAction.ReplaceLast('ㄲ'), input.input(CheonjiinKey.GIYEOK, 600, 900))
        assertEquals(CheonjiinAction.ReplaceLast('ㄱ'), input.input(CheonjiinKey.GIYEOK, 900, 900))
        assertEquals(CheonjiinAction.Append('ㄱ'), input.input(CheonjiinKey.GIYEOK, 1_801, 900))
    }

    @Test
    fun `different key or vowel ends consonant cycling`() {
        val input = CheonjiinInput()
        input.input(CheonjiinKey.NIEUN, 0, 900)
        assertEquals(CheonjiinAction.Append('ㄱ'), input.input(CheonjiinKey.GIYEOK, 1, 900))
        input.input(CheonjiinKey.I, 2, 900)
        assertEquals(CheonjiinAction.Append('ㄱ'), input.input(CheonjiinKey.GIYEOK, 3, 900))
    }

    @Test
    fun `backspace walks vowel strokes and consumes an unfinished dot`() {
        val input = CheonjiinInput()
        "ㅣ··".forEachIndexed { index, stroke -> input.input(keyForStroke(stroke), index.toLong(), 900) }
        assertEquals(CheonjiinAction.ReplaceLast('ㅏ'), input.backspace())
        assertEquals(CheonjiinAction.ReplaceLast('ㅣ'), input.backspace())
        assertEquals(CheonjiinAction.RemoveLast, input.backspace())
        assertNull(input.backspace())

        input.input(CheonjiinKey.DOT, 10, 900)
        assertEquals(CheonjiinAction.None, input.backspace())
        assertNull(input.backspace())
    }

    private fun keyForStroke(stroke: Char) = when (stroke) {
        'ㅣ' -> CheonjiinKey.I
        '·' -> CheonjiinKey.DOT
        'ㅡ' -> CheonjiinKey.EU
        else -> error("Unknown stroke: $stroke")
    }
}
