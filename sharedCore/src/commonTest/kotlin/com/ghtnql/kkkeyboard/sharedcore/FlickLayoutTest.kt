package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlickLayoutTest {
    @Test fun nativeBridgeUsesSameHangulMappingAsAndroid() {
        val bridge = FlickLayoutBridge()
        assertEquals("ㄱ", bridge.key(1, 0).label(FlickDirection.CENTER))
        assertEquals("ㅋ", bridge.key(1, 0).label(FlickDirection.LEFT))
        assertEquals("ㄲ", bridge.key(1, 0).label(FlickDirection.UP))
        assertNull(bridge.key(1, 0).label(FlickDirection.DOWN))
        assertEquals("ㅚ", bridge.key(0, 2).label(FlickDirection.DOWN))
    }
    @Test fun deadZoneAxisAndHysteresisMatchForBothRenderers() {
        val bridge = FlickLayoutBridge()
        assertEquals(FlickDirection.CENTER, bridge.resolve(5f, 5f, 20f, FlickDirection.CENTER))
        assertEquals(FlickDirection.LEFT, bridge.resolve(-30f, 2f, 20f, FlickDirection.CENTER))
        assertEquals(FlickDirection.UP, bridge.resolve(2f, -30f, 20f, FlickDirection.CENTER))
        assertEquals(FlickDirection.RIGHT, bridge.resolve(30f, 2f, 20f, FlickDirection.CENTER))
        assertEquals(FlickDirection.DOWN, bridge.resolve(2f, 30f, 20f, FlickDirection.CENTER))
        assertEquals(FlickDirection.RIGHT, bridge.resolve(25f, 27f, 20f, FlickDirection.RIGHT))
    }
}
