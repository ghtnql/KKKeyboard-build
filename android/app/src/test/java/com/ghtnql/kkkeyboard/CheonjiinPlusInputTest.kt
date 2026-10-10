package com.ghtnql.kkkeyboard

import com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog
import org.junit.Assert.*
import org.junit.Test

class CheonjiinPlusInputTest {
    @Test fun directConsonantsNeverCycleAndReuseVowelComposition() {
        val input = CheonjiinInput()
        val composer = CheonjiinComposer()
        fun apply(action: CheonjiinAction) {
            when (action) {
                is CheonjiinAction.Append -> composer.append(action.character)
                is CheonjiinAction.ReplaceLast -> composer.replaceLast(action.character)
                CheonjiinAction.RemoveLast -> composer.removeLast()
                CheonjiinAction.None -> Unit
            }
        }
        for (consonant in "ㄱㅋㄴㄹㄷㅌㅂㅍㅅㅎㅈㅊㅇㅁ") {
            assertEquals(CheonjiinAction.Append(consonant), input.inputDirectConsonant(consonant))
            assertEquals(CheonjiinAction.Append(consonant), input.inputDirectConsonant(consonant))
        }
        input.reset()
        apply(input.inputDirectConsonant('ㄱ'))
        apply(input.input(CheonjiinKey.I, 0, 900))
        apply(input.input(CheonjiinKey.DOT, 1, 900))
        assertEquals("가", composer.text)
        apply(input.input(CheonjiinKey.I, 2, 900))
        assertEquals("개", composer.text)
        apply(input.inputDirectConsonant('ㄴ'))
        apply(input.input(CheonjiinKey.EU, 3, 900))
        assertEquals("개느", composer.text)
    }

    @Test fun quickSecondTapProducesTenseConsonantOnlyForFiveBaseKeys() {
        val pairs = listOf('ㄱ' to 'ㄲ', 'ㄷ' to 'ㄸ', 'ㅂ' to 'ㅃ', 'ㅅ' to 'ㅆ', 'ㅈ' to 'ㅉ')
        for ((base, tense) in pairs) {
            val input = CheonjiinInput()
            assertEquals(CheonjiinAction.Append(base), input.inputDirectConsonant(base, 1_000, 900))
            assertEquals(CheonjiinAction.ReplaceLast(tense), input.inputDirectConsonant(base, 1_100, 900))
            assertEquals(CheonjiinAction.Append(base), input.inputDirectConsonant(base, 1_200, 900))
        }

        for (plain in "ㅋㄴㄹㅌㅍㅎㅊㅇㅁ") {
            val input = CheonjiinInput()
            assertEquals(CheonjiinAction.Append(plain), input.inputDirectConsonant(plain, 1_000, 900))
            assertEquals(CheonjiinAction.Append(plain), input.inputDirectConsonant(plain, 1_100, 900))
        }

        val slow = CheonjiinInput()
        assertEquals(CheonjiinAction.Append('ㄱ'), slow.inputDirectConsonant('ㄱ', 1_000, 900))
        assertEquals(CheonjiinAction.Append('ㄱ'), slow.inputDirectConsonant('ㄱ', 2_000, 900))
    }

    @Test fun vowelBreaksDirectDoubleTapSequence() {
        val input = CheonjiinInput()
        assertEquals(CheonjiinAction.Append('ㄱ'), input.inputDirectConsonant('ㄱ', 1_000, 900))
        assertEquals(CheonjiinAction.Append('ㅣ'), input.input(CheonjiinKey.I, 1_100, 900))
        assertEquals(CheonjiinAction.Append('ㄱ'), input.inputDirectConsonant('ㄱ', 1_200, 900))
    }

    @Test fun allFiveFixedHoldChoicesFeedTheSameComposer() {
        for ((key, tense) in listOf("ㅋ" to 'ㄲ', "ㅌ" to 'ㄸ', "ㅍ" to 'ㅃ', "ㅎ" to 'ㅆ', "ㅊ" to 'ㅉ')) {
            val fixed = LongPressCatalog.choices("cheonjiin_plus", key, listOf("", "", "")).single()
            assertEquals(tense.toString(), fixed)
            val input = CheonjiinInput()
            val action = input.inputDirectConsonant(fixed.single()) as CheonjiinAction.Append
            val composer = CheonjiinComposer()
            composer.append(action.character)
            composer.append((input.input(CheonjiinKey.I, 0, 900) as CheonjiinAction.Append).character)
            composer.replaceLast((input.input(CheonjiinKey.DOT, 1, 900) as CheonjiinAction.ReplaceLast).character)
            assertEquals(mapOf('ㄲ' to "까", 'ㄸ' to "따", 'ㅃ' to "빠", 'ㅆ' to "싸", 'ㅉ' to "짜")[tense], composer.text)
        }
    }
}
