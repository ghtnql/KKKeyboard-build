package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals

class MonetizationTest {
    @Test fun formatsTrialRemaining() {
        assertEquals("만료됨", formatTrialRemaining(0))
        assertEquals("만료됨", formatTrialRemaining(-5))
        assertEquals("52분 남음", formatTrialRemaining(52 * 60_000))
        assertEquals("1시간 남음", formatTrialRemaining(60 * 60_000))
        assertEquals("23시간 12분 남음", formatTrialRemaining((23 * 60 + 12) * 60_000))
    }
}
