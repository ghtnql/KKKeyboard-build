package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HangulProbeTest {
    @Test
    fun composesBasicSyllable() {
        val composer = HangulProbe()
        assertEquals("가", composer.compose(0, 0))
        assertEquals("각", composer.compose(0, 0, 1))
        assertEquals("힣", composer.compose(18, 20, 27))
        assertFailsWith<IllegalArgumentException> { composer.compose(19, 0) }
    }
}
