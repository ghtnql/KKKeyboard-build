package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinQwertyLayoutTest {
    @Test
    fun rowsUseExactMobileQwertyOrder() {
        assertEquals(
            listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
            ),
            LatinQwertyLayout.rows,
        )
        assertTrue(LatinQwertyLayout.rows.flatten().all { it.length == 1 })
        assertEquals(LatinQwertyLayout.rows.take(2), LatinQwertyLayout.characterRows)
        assertEquals(LatinQwertyLayout.rows.last(), LatinQwertyLayout.bottomRow)
    }

    @Test
    fun shiftMapsEveryLetterBetweenLowercaseAndUppercase() {
        LatinQwertyLayout.rows.flatten().forEach { key ->
            assertEquals(key, LatinQwertyLayout.labelFor(key, shifted = false))
            assertEquals(key.uppercase(), LatinQwertyLayout.labelFor(key, shifted = true))
        }
    }

    @Test
    fun onlyLowercaseLatinKeysAreShiftable() {
        LatinQwertyLayout.rows.flatten().forEach { assertTrue(LatinQwertyLayout.isShiftable(it)) }
        LatinQwertyLayout.rows.flatten().forEach { assertTrue(LatinQwertyLayout.hasShiftVariant(it)) }
        listOf("A", "1", "?", "ㅂ", "", "ab").forEach {
            assertFalse(LatinQwertyLayout.isShiftable(it))
            assertFalse(LatinQwertyLayout.hasShiftVariant(it))
            assertEquals(it, LatinQwertyLayout.labelFor(it, shifted = true))
        }
    }
}
