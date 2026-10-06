package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JapanesePronunciationMatcherTest {
    private val matcher = JapanesePronunciationMatcher()

    @Test fun requiredJapaneseReadingsMatch() {
        for ((input, expected) in listOf(
            "규슈" to "큐슈", "도쿄" to "토쿄", "규우뉴우" to "규뉴",
            "쿄오" to "쿄", "토우쿄우" to "토쿄", "벤쿄오" to "벤쿄",
        )) {
            assertTrue(matcher.matches(input, expected), "$input / $expected")
            assertTrue(matcher.matches(expected, input), "$expected / $input")
        }
    }

    @Test fun everyVowelNucleusAndCompoundUsesOnlyItsAllowedExtensions() {
        val allowedExtensions = listOf(
            listOf(0), listOf(1, 20), listOf(0), listOf(1, 20), listOf(4),
            listOf(5, 20), listOf(4), listOf(5, 20), listOf(8, 13), listOf(0),
            listOf(1, 20), listOf(5, 20), listOf(8, 13), listOf(13), listOf(4),
            listOf(5, 20), listOf(20), listOf(13), listOf(18), listOf(20), listOf(20),
        )
        for (medial in 0..20) {
            val base = syllable(0, medial).toString()
            for (extension in 0..20) {
                val input = base + syllable(11, extension)
                assertEquals(
                    if (extension in allowedExtensions[medial]) base else input,
                    matcher.canonicalize(input), "medial $medial + $extension",
                )
            }
        }
    }

    @Test fun onsetFoldingPreservesMedialsAndEveryFinal() {
        for (medial in 0..20) {
            for (final in 0..27) {
                assertEquals(syllable(0, medial, final).toString(), matcher.canonicalize(syllable(15, medial, final).toString()))
                assertEquals(syllable(3, medial, final).toString(), matcher.canonicalize(syllable(16, medial, final).toString()))
            }
        }
        for (onset in 0..18) {
            if (onset == 15 || onset == 16) continue
            val input = syllable(onset, 0).toString()
            assertEquals(input, matcher.canonicalize(input))
        }
        assertFalse(matcher.matches("까", "가"))
        assertFalse(matcher.matches("따", "다"))
    }

    @Test fun hiatusCodasAndStandaloneJamoRemainSignificant() {
        for (input in listOf("아이", "아우", "어아", "이아", "각아", "가앙", "각앙", "ㄱㅋㄷㅌ", "ᄀᄏᄃᄐ", "ㅏㅏ", "ありがとう")) {
            assertEquals(input, matcher.canonicalize(input), input)
        }
        for (final in 1..27) {
            val closed = syllable(0, 0, final).toString()
            assertEquals(closed + "아", matcher.canonicalize(closed + "아"))
            val extensionWithFinal = syllable(11, 0, final).toString()
            assertEquals("가" + extensionWithFinal, matcher.canonicalize("가" + extensionWithFinal))
        }
        assertFalse(matcher.matches("아이", "아"))
        assertFalse(matcher.matches("아우", "아"))
        assertFalse(matcher.matches("각아", "각"))
        assertFalse(matcher.matches("가앙", "가"))
        assertFalse(matcher.matches("ㅋ", "ㄱ"))
        assertFalse(matcher.matches("ㅌ", "ㄷ"))
    }

    @Test fun whitespacePunctuationAndNonHangulArePreservedBoundaries() {
        for (input in listOf(" 가 아 ", "가\t아", "가\n아", "가,아", "가・아", "가A아", "가🙂아")) {
            assertEquals(input, matcher.canonicalize(input))
        }
        assertEquals(" 도 교 ", matcher.canonicalize(" 토 쿄 "))
        assertFalse(matcher.matches("토 쿄", "도쿄"))
        assertFalse(matcher.matches(" 도쿄", "도쿄"))
        assertFalse(matcher.matches("도쿄!", "도쿄"))
        assertEquals("", matcher.canonicalize(""))
    }

    @Test fun canonicalizationIsIdempotent() {
        for (input in listOf("규우뉴우", "토우쿄우", "벤쿄오", "쿄오오우", "가아아", "아이", "각아", "ㅋㅌ", " 토우 쿄오! ")) {
            val canonical = matcher.canonicalize(input)
            assertEquals(canonical, matcher.canonicalize(canonical), input)
        }
    }

    @Test fun prefixMatchingIsOptInAndPreservesBoundaries() {
        assertTrue(matcher.matchesPrefix("토우", "도쿄"))
        assertTrue(matcher.matchesPrefix("규", "큐우슈"))
        assertTrue(matcher.matchesPrefix("", "도쿄"))
        assertFalse(matcher.matchesPrefix("도쿄역", "토쿄"))
        assertFalse(matcher.matchesPrefix("토 쿄", "도쿄"))
        assertFalse(matcher.matchesPrefix(" 토", "도쿄"))
        assertFalse(matcher.matchesPrefix("토!", "도쿄"))
        assertFalse(matcher.matchesPrefix("ㅋ", "규슈"))
        assertFalse(matcher.matchesPrefix("ㅌ", "도쿄"))
        assertTrue(matcher.matchesPrefix("도 ", "토 쿄"))
    }

    @Test fun genericNormalizationAndDefaultPracticeRemainExact() {
        assertFalse(normalizeAnswer("토쿄") == normalizeAnswer("도쿄"))
        assertFalse(normalizeAnswer("규우뉴우") == normalizeAnswer("규뉴"))
        val engine = PracticeEngine(listOf(PracticeQuestion("도쿄")), "exact", false)
        assertFalse(engine.submit("토쿄"))
        assertEquals(0, engine.currentIndex)
        assertTrue(engine.submit("도쿄"))
    }

    private fun syllable(onset: Int, medial: Int, final: Int = 0): Char =
        (0xAC00 + onset * 588 + medial * 28 + final).toChar()
}
