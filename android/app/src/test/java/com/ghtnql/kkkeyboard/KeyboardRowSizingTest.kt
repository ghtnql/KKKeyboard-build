package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardRowSizingTest {
    @Test
    fun `base row counts match the rows actually rendered for each field mode`() {
        assertEquals(5, KeyboardBaseRows.forMode(InputFieldMode.TEXT))
        assertEquals(5, KeyboardBaseRows.forMode(InputFieldMode.PASSWORD))
        assertEquals(5, KeyboardBaseRows.forMode(InputFieldMode.EMAIL))
        assertEquals(5, KeyboardBaseRows.forMode(InputFieldMode.URI))
        assertEquals(5, KeyboardBaseRows.forMode(InputFieldMode.NUMBER))
        assertEquals(6, KeyboardBaseRows.forMode(InputFieldMode.PHONE))
    }

    @Test
    fun `keeps requested height and optional rows when they fit`() {
        val plan = KeyboardRowSizing.plan(
            requestedKeyHeightDp = 50,
            availableHeightDp = 520,
            baseRowCount = 6,
            numberRowRequested = true,
            cursorRowRequested = true,
            supportsNumberRow = true,
        )

        assertEquals(50, plan.keyHeightDp)
        assertTrue(plan.numberRowEnabled)
        assertTrue(plan.cursorRowEnabled)
    }

    @Test
    fun `drops cursor before number row on constrained landscape`() {
        val plan = KeyboardRowSizing.plan(
            requestedKeyHeightDp = 58,
            availableHeightDp = 360,
            baseRowCount = 6,
            numberRowRequested = true,
            cursorRowRequested = true,
            supportsNumberRow = true,
        )

        assertTrue(plan.numberRowEnabled)
        assertFalse(plan.cursorRowEnabled)
        assertEquals(47, plan.keyHeightDp)
    }

    @Test
    fun `drops optional rows before shrinking core keys`() {
        val plan = KeyboardRowSizing.plan(
            requestedKeyHeightDp = 58,
            availableHeightDp = 320,
            baseRowCount = 6,
            numberRowRequested = true,
            cursorRowRequested = true,
            supportsNumberRow = true,
        )

        assertFalse(plan.numberRowEnabled)
        assertFalse(plan.cursorRowEnabled)
        assertEquals(49, plan.keyHeightDp)
    }

    @Test
    fun `shrinks core rows only when the core layout itself cannot fit`() {
        val plan = KeyboardRowSizing.plan(
            requestedKeyHeightDp = 58,
            availableHeightDp = 260,
            baseRowCount = 6,
            numberRowRequested = false,
            cursorRowRequested = false,
            supportsNumberRow = true,
        )

        assertEquals(39, plan.keyHeightDp)
    }

    @Test
    fun `never enables number row for layouts that do not support it`() {
        val plan = KeyboardRowSizing.plan(
            requestedKeyHeightDp = 50,
            availableHeightDp = 420,
            baseRowCount = 5,
            numberRowRequested = true,
            cursorRowRequested = true,
            supportsNumberRow = false,
        )

        assertFalse(plan.numberRowEnabled)
        assertTrue(plan.cursorRowEnabled)
    }
}
