package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseTransliteratorTest {
    @Test
    fun returnsSeedCandidateForArigato() {
        assertEquals(listOf("ありがとう"), JapaneseTransliterator.candidates("아리가토"))
    }

    @Test
    fun preservesOrthographicGreetingException() {
        assertEquals(listOf("こんにちは"), JapaneseTransliterator.candidates("곤니치와"))
    }

    @Test
    fun convertsReportedOhayoInputsAndFullGreeting() {
        assertEquals(listOf("おはよう"), JapaneseTransliterator.candidates("오하요"))
        assertEquals(listOf("おはよう"), JapaneseTransliterator.candidates("오하요우"))
        assertEquals(listOf("おはよう"), JapaneseTransliterator.candidates("오하이오"))
        assertEquals(listOf("ございます"), JapaneseTransliterator.candidates("고자이마스"))
        assertEquals(
            listOf("おはようございます"),
            JapaneseTransliterator.candidates("오하요 고자이마스"),
        )
    }

    @Test
    fun returnsMultipleCandidatesInStableOrder() {
        assertEquals(listOf("すし", "寿司"), JapaneseTransliterator.candidates("스시"))
    }

    @Test
    fun coversLongVowelSokuonNasalAndYouonFixtures() {
        assertEquals(listOf("コーヒー"), JapaneseTransliterator.candidates("코히"))
        assertEquals(listOf("きって"), JapaneseTransliterator.candidates("킷테"))
        assertEquals(listOf("おんな"), JapaneseTransliterator.candidates("온나"))
        assertEquals(listOf("りょこう"), JapaneseTransliterator.candidates("료코"))
    }

    @Test
    fun ignoresOuterWhitespaceWithoutNetworkOrFallbackGuessing() {
        assertEquals(listOf("ありがとう"), JapaneseTransliterator.candidates("  아리가토  "))
    }

    @Test
    fun exactLookupDoesNotNormalizeHotPathInput() {
        assertEquals(listOf("ありがとう"), JapaneseTransliterator.candidatesExact("아리가토"))
        assertTrue(JapaneseTransliterator.candidatesExact(" 아리가토 ").isEmpty())
    }

    @Test
    fun generatesKanaForHangulOutsideSeedDictionary() {
        assertEquals(listOf("かなだ", "カナダ"), JapaneseTransliterator.candidates("가나다"))
        assertEquals(listOf("ばぱ", "バパ"), JapaneseTransliterator.candidates("바파"))
        assertTrue(JapaneseTransliterator.candidates("latin").isEmpty())
        assertTrue(JapaneseTransliterator.candidatesExact("").isEmpty())
    }
    @Test
    fun acceptsShortenedMilkAndTokyoPronunciations() {
        for (input in listOf("규뉴", "큐뉴", "큐우뉴우")) {
            assertTrue("Missing milk for $input", "牛乳" in JapaneseTransliterator.candidatesExact(input))
        }
        for (input in listOf("토쿄", "도쿄", "토우쿄우", "도오쿄오")) {
            assertTrue("Missing Tokyo for $input", "東京" in JapaneseTransliterator.candidatesExact(input))
            assertTrue("Missing Tokyo suggestion for $input", "東京" in JapaneseTransliterator.suggestionsExact(input))
        }
        assertTrue("牛乳" in JapaneseTransliterator.candidatesExact("규 뉴"))
        assertTrue("牛乳" in JapaneseTransliterator.candidatesExact("규뉴데스").map { it.removeSuffix("です") })
    }

}
