package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HangulKanaTransliteratorTest {
    private val transliterator = HangulKanaTransliterator()

    @Test
    fun mapsRepresentativeSyllablesAndFinals() {
        assertEquals("がなだ", transliterator.transliterate("가나다"))
        assertEquals("かがただ", transliterator.transliterate("카가타다"))
        assertEquals("つちゅ", transliterator.transliterate("츠츄"))
        assertEquals("きって", transliterator.transliterate("킷테"))
        assertEquals("りょこ", transliterator.transliterate("료코"))
    }

    @Test
    fun exposesAmbiguousDakutenAndVLoanwordCandidatesWithoutChangingPrimaryReading() {
        assertEquals(listOf("ず", "づ"), transliterator.transliterateCandidates("즈"))
        assertEquals(listOf("ば", "ゔぁ"), transliterator.transliterateCandidates("바"))
        assertEquals(
            listOf("えばんげりおん", "えゔぁんげりおん"),
            transliterator.transliterateCandidates("에반게리온"),
        )
    }

    @Test
    fun skipsSpacesAndRejectsEmptyOrNonSyllableInput() {
        assertEquals("がなだ", transliterator.transliterate("가 나다"))
        assertNull(transliterator.transliterate(""))
        assertNull(transliterator.transliterate("   "))
        assertNull(transliterator.transliterate("ㄱㅏ"))
        assertNull(transliterator.transliterate("가A"))
    }
}
