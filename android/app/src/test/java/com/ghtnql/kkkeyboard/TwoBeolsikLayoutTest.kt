package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoBeolsikLayoutTest {
    @Test
    fun characterAndSymbolRowsUseSameTenUnitGrid() {
        assertEquals(10, TwoBeolsikLayout.characterRows.first().size)
        assertEquals(9, TwoBeolsikLayout.characterRows.last().size)
        assertEquals(TwoBeolsikLayout.ROW_UNITS, TwoBeolsikLayout.bottomRow.size + 2 * TwoBeolsikLayout.EDGE_KEY_UNITS, 0f)
        assertEquals(3, SymbolLayout.pages.size)
        SymbolLayout.pages.forEach { page ->
            assertEquals(10, page.top.size)
            assertEquals(10, page.middle.size)
            assertEquals(7, page.bottom.size)
            assertTrue((page.top + page.middle + page.bottom).all { it.length == 1 })
        }
    }

    @Test
    fun expandedSymbolsKeepFrequentArrowsOnSecondPageAndDecorationsOnThird() {
        val symbols = SymbolLayout.pages.flatMap { it.top + it.middle + it.bottom }
        val secondPage = SymbolLayout.pages[1].top + SymbolLayout.pages[1].middle + SymbolLayout.pages[1].bottom
        val thirdPage = SymbolLayout.pages[2].top + SymbolLayout.pages[2].middle + SymbolLayout.pages[2].bottom
        listOf("←", "→", "↑", "↓").forEach { assertTrue("Frequent arrow must be on page 2: $it", it in secondPage) }
        listOf("♡", "♥", "☆", "★", "※", "・", "々", "〒", "✓").forEach {
            assertTrue("Missing page 3 symbol $it", it in thirdPage)
        }
        listOf("⇐", "⇒", "⇑", "⇓").forEach { assertFalse("Redundant arrow should be removed: $it", it in symbols) }
    }

    @Test
    fun shiftedConsonantsUseDoubleJamo() {
        assertEquals("ㅃ", TwoBeolsikLayout.labelFor("ㅂ", shifted = true))
        assertEquals("ㅉ", TwoBeolsikLayout.labelFor("ㅈ", shifted = true))
        assertEquals("ㄸ", TwoBeolsikLayout.labelFor("ㄷ", shifted = true))
        assertEquals("ㄲ", TwoBeolsikLayout.labelFor("ㄱ", shifted = true))
        assertEquals("ㅆ", TwoBeolsikLayout.labelFor("ㅅ", shifted = true))
    }

    @Test
    fun shiftedVowelsUseExpectedVariants() {
        assertEquals("ㅒ", TwoBeolsikLayout.labelFor("ㅐ", shifted = true))
        assertEquals("ㅖ", TwoBeolsikLayout.labelFor("ㅔ", shifted = true))
    }

    @Test
    fun keysWithoutShiftVariantStayUnchanged() {
        assertEquals("ㅏ", TwoBeolsikLayout.labelFor("ㅏ", shifted = true))
        assertEquals("ㅏ", TwoBeolsikLayout.labelFor("ㅏ", shifted = false))
        assertFalse(TwoBeolsikLayout.hasShiftVariant("ㅏ"))
        assertTrue(TwoBeolsikLayout.hasShiftVariant("ㄱ"))
    }

    @Test
    fun auxiliaryNumberRowHasStableTenKeyOrder() {
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            TwoBeolsikLayout.auxiliaryNumberRow,
        )
    }
}
