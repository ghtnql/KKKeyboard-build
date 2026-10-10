package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutOptionsTest {
    @Test fun persistedInputLayoutsRoundTripWithCheonjiinFallback() {
        assertEquals(listOf("cheonjiin", "cheonjiin_plus", "qwerty", "hangul_flick"), KeyboardInputLayout.entries.map { it.persistedValue })
        KeyboardInputLayout.entries.forEach { layout ->
            assertEquals(layout, KeyboardInputLayout.fromPersistedValue(layout.persistedValue))
        }
        assertEquals(KeyboardInputLayout.CHEONJIIN_PLUS, KeyboardInputLayout.fromPersistedValue("cheonjiin_plus"))
        assertEquals(KeyboardInputLayout.CHEONJIIN, KeyboardInputLayout.fromPersistedValue(null))
        assertEquals(KeyboardInputLayout.CHEONJIIN, KeyboardInputLayout.fromPersistedValue("unknown"))
    }

    @Test fun iosHeightChoicesHaveStableMeanings() {
        assertEquals(220, LayoutHeight.COMPACT.iosPoints)
        assertEquals(260, LayoutHeight.NORMAL.iosPoints)
        assertEquals(300, LayoutHeight.TALL.iosPoints)
        assertEquals(LayoutHeight.COMPACT, LayoutHeight.fromIosPoints(220))
        assertEquals(LayoutHeight.NORMAL, LayoutHeight.fromIosPoints(260))
        assertEquals(LayoutHeight.TALL, LayoutHeight.fromIosPoints(300))
        assertEquals(LayoutHeight.NORMAL, LayoutHeight.fromIosPoints(999))
    }
}
