package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLanguageTest {
    @Test
    fun nextCyclesInKoreanJapaneseEnglishOrder() {
        assertEquals(KeyboardLanguage.JAPANESE, KeyboardLanguage.KOREAN.next())
        assertEquals(KeyboardLanguage.ENGLISH, KeyboardLanguage.JAPANESE.next())
        assertEquals(KeyboardLanguage.KOREAN, KeyboardLanguage.ENGLISH.next())
    }

    @Test
    fun englishAlwaysUsesQwerty() {
        InputLayout.entries.forEach { preferred ->
            assertEquals(InputLayout.QWERTY, KeyboardLanguage.ENGLISH.effectiveLayout(preferred))
        }
    }

    @Test
    fun koreanAndJapanesePreserveEveryPreferredLayout() {
        listOf(KeyboardLanguage.KOREAN, KeyboardLanguage.JAPANESE).forEach { language ->
            InputLayout.entries.forEach { preferred ->
                assertEquals(preferred, language.effectiveLayout(preferred))
            }
        }
    }
}
