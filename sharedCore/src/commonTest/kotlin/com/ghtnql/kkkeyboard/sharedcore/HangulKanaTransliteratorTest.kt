package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HangulKanaTransliteratorTest {
    private val transliterator = HangulKanaTransliterator()

    @Test
    fun mapsRepresentativeSyllablesAndFinals() {
        assertEquals("かなだ", transliterator.transliterate("가나다"))
        assertEquals("きって", transliterator.transliterate("킷테"))
        assertEquals("りょこ", transliterator.transliterate("료코"))
    }

    @Test
    fun skipsSpacesAndRejectsEmptyOrNonSyllableInput() {
        assertEquals("かなだ", transliterator.transliterate("가 나다"))
        assertNull(transliterator.transliterate(""))
        assertNull(transliterator.transliterate("   "))
        assertNull(transliterator.transliterate("ㄱㅏ"))
        assertNull(transliterator.transliterate("가A"))
    }
}
