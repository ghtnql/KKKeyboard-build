package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyboardLayoutSettingsTest {
    @Test
    fun persistedHeightValuesRoundTrip() {
        KeyboardHeight.entries.forEach { height ->
            assertEquals(height, KeyboardHeight.fromPersistedValue(height.persistedValue))
        }
    }

    @Test
    fun unknownHeightFallsBackToNormal() {
        assertEquals(KeyboardHeight.NORMAL, KeyboardHeight.fromPersistedValue(null))
        assertEquals(KeyboardHeight.NORMAL, KeyboardHeight.fromPersistedValue("unexpected"))
    }

    @Test
    fun inputLayoutsRoundTripAndMissingValueDefaultsToCheonjiin() {
        InputLayout.entries.forEach { layout ->
            assertEquals(layout, InputLayout.fromPersistedValue(layout.persistedValue))
        }
        assertEquals("cheonjiin_plus", InputLayout.CHEONJIIN_PLUS.persistedValue)
        assertEquals(InputLayout.CHEONJIIN_PLUS, InputLayout.fromPersistedValue("cheonjiin_plus"))
        assertEquals(InputLayout.CHEONJIIN, InputLayout.fromPersistedValue(null))
        assertEquals(InputLayout.CHEONJIIN, InputLayout.fromPersistedValue("unexpected"))
    }

    @Test
    fun supportedHeightsRemainOrderedAndDistinct() {
        assertEquals(listOf(44, 50, 58), KeyboardHeight.entries.map { it.keyHeightDp })
    }

    @Test
    fun orientationProfilesMapFromAndroidConfigurationValues() {
        // Android Configuration orientation values: undefined=0, portrait=1, landscape=2.
        assertEquals(KeyboardOrientation.PORTRAIT, KeyboardOrientation.fromConfigurationOrientation(0))
        assertEquals(KeyboardOrientation.PORTRAIT, KeyboardOrientation.fromConfigurationOrientation(1))
        assertEquals(KeyboardOrientation.LANDSCAPE, KeyboardOrientation.fromConfigurationOrientation(2))
    }

    @Test
    fun orientationProfilesUseDistinctPreferenceNamespaces() {
        assertNotEquals(
            KeyboardOrientation.PORTRAIT.preferenceSuffix,
            KeyboardOrientation.LANDSCAPE.preferenceSuffix,
        )
    }
}
