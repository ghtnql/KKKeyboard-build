package com.ghtnql.kkkeyboard

import org.junit.Assert.*
import org.junit.Test

class HangulFlickLayoutTest {
    @Test fun twelveKeysCoverEveryModernInitialAndVowelOnce() {
        assertEquals(4, HangulFlickLayout.rows.size)
        assertTrue(HangulFlickLayout.rows.all { it.size == 3 })
        val labels = HangulFlickLayout.rows.flatten().flatMap { key -> FlickDirection.entries.mapNotNull(key::label) }
        val expected = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".map(Char::toString)
        assertEquals(expected.toSet(), labels.toSet())
        assertEquals(40, labels.size)
    }

    @Test fun directionUsesTouchOriginAndAllFourAxes() {
        assertEquals(FlickDirection.CENTER, FlickDirectionResolver.resolve(8f, 4f, 20f))
        assertEquals(FlickDirection.LEFT, FlickDirectionResolver.resolve(-30f, 2f, 20f))
        assertEquals(FlickDirection.UP, FlickDirectionResolver.resolve(2f, -30f, 20f))
        assertEquals(FlickDirection.RIGHT, FlickDirectionResolver.resolve(30f, 2f, 20f))
        assertEquals(FlickDirection.DOWN, FlickDirectionResolver.resolve(2f, 30f, 20f))
    }

    @Test fun diagonalJitterAndReturnToCenterArePredictable() {
        assertEquals(FlickDirection.UP, FlickDirectionResolver.resolve(31f, -30f, 20f, FlickDirection.UP))
        assertEquals(FlickDirection.RIGHT, FlickDirectionResolver.resolve(40f, -25f, 20f, FlickDirection.UP))
        assertEquals(FlickDirection.RIGHT, FlickDirectionResolver.resolve(16f, 0f, 20f, FlickDirection.RIGHT))
        assertEquals(FlickDirection.CENTER, FlickDirectionResolver.resolve(5f, 5f, 20f, FlickDirection.RIGHT))
    }

    @Test fun sensitivityChangesRequiredTravelNotTime() {
        assertEquals(FlickDirection.LEFT, FlickDirectionResolver.resolve(-16f, 0f, 12f))
        assertEquals(FlickDirection.CENTER, FlickDirectionResolver.resolve(-16f, 0f, 32f))
    }

    @Test fun unknownSavedLayoutFallsBackToCheonjiin() {
        assertEquals(InputLayout.CHEONJIIN, InputLayout.fromPersistedValue(null))
        assertEquals(InputLayout.CHEONJIIN, InputLayout.fromPersistedValue("unknown"))
        InputLayout.entries.forEach { assertEquals(it, InputLayout.fromPersistedValue(it.persistedValue)) }
    }
}
