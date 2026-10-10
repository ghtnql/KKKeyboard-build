package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JamoLearningTest {
    private fun syllable(c: String, v: String) = buildJamoSyllable(
        basicConsonants.single { it.glyph == c }, basicVowels.single { it.glyph == v })

    @Test fun basicLettersComposeWithTheirActualUnicodeIndices() {
        assertEquals("가", syllable("ㄱ", "ㅏ"))
        assertEquals("너", syllable("ㄴ", "ㅓ"))
        assertEquals("므", syllable("ㅁ", "ㅡ"))
        assertEquals("히", syllable("ㅎ", "ㅣ"))
        assertEquals("아", syllable("ㅇ", "ㅏ"))
        assertEquals("유", syllable("ㅇ", "ㅠ"))
    }

    @Test fun everyBasicCombinationIsOneDistinctSyllableWithoutFinalConsonant() {
        val syllables = basicConsonants.flatMap { c -> basicVowels.map { v -> buildJamoSyllable(c, v) } }
        assertEquals(140, syllables.toSet().size)
        syllables.forEach { word ->
            assertEquals(1, word.length)
            assertEquals(0, (word.single().code - 0xAC00) % 28)
        }
    }

    @Test fun everyBasicCombinationHasHiraganaAndKatakanaPronunciationGuide() {
        basicConsonants.forEach { consonant ->
            basicVowels.forEach { vowel ->
                val reading = buildJamoKanaReading(consonant, vowel)
                assertEquals(true, reading.hiragana.any { it in '\u3041'..'\u3096' })
                assertEquals(true, reading.katakana.any { it in '\u30A1'..'\u30F6' })
            }
        }
        assertEquals(JamoKanaReading("ちゃ", "チャ"), buildJamoKanaReading(
            basicConsonants.single { it.glyph == "ㅊ" }, basicVowels.single { it.glyph == "ㅏ" }))
        assertEquals(JamoKanaReading("ちゃ", "チャ"), buildJamoKanaReading(
            basicConsonants.single { it.glyph == "ㅈ" }, basicVowels.single { it.glyph == "ㅏ" }))
        assertEquals(JamoKanaReading("か", "カ"), buildJamoKanaReading(
            basicConsonants.single { it.glyph == "ㄱ" }, basicVowels.single { it.glyph == "ㅏ" }))
        assertEquals(JamoKanaReading("あ", "ア"), buildJamoKanaReading(
            basicConsonants.single { it.glyph == "ㅇ" }, basicVowels.single { it.glyph == "ㅏ" }))
    }

    @Test fun swappingConsonantAndVowelIsRejected() {
        assertFailsWith<IllegalArgumentException> { buildJamoSyllable(basicVowels.first(), basicConsonants.first()) }
    }
}
