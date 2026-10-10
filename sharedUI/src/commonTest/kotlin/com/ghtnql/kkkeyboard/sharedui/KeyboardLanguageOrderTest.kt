package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardLanguageOrderTest {
    @Test fun malformedStoredOrderRetainsValidChoicesAndRestoresMissingLanguages() {
        assertEquals(listOf("english", "korean", "japanese"), normalizeKeyboardLanguageOrder(listOf(" ENGLISH ", "bogus", "english", "KOREAN")))
        assertEquals(defaultKeyboardLanguageOrder, normalizeKeyboardLanguageOrder(emptyList()))
    }
    @Test fun dropMovesFirstToLastAndClampsOutsideList() {
        val changed = moveKeyboardLanguage(defaultKeyboardLanguageOrder, "korean", 2)
        assertEquals(listOf("japanese", "english", "korean"), changed)
        assertEquals(listOf("korean", "japanese", "english"), moveKeyboardLanguage(changed, "korean", -20))
        assertEquals(changed, moveKeyboardLanguage(defaultKeyboardLanguageOrder, "korean", 20))
    }
    @Test fun cancelOrUnknownLanguageDoesNotLoseEntries() {
        assertEquals(defaultKeyboardLanguageOrder, moveKeyboardLanguage(defaultKeyboardLanguageOrder, "unknown", 1))
        assertEquals(defaultKeyboardLanguageOrder, moveKeyboardLanguage(defaultKeyboardLanguageOrder, "japanese", 1))
    }
}
