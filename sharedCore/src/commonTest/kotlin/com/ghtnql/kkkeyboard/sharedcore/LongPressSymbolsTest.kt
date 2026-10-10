package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongPressSymbolsTest {
    @Test fun catalogsHaveStableIdsLabelsAndOrder() {
        assertTrue(LongPressCatalog.choices("hangul_flick", "ㅣ", listOf("!", "@", "#")).isEmpty())
        assertEquals(listOf("ㅣ", "·", "ㅡ", "ㄱㅋ", "ㄴㄹ", "ㄷㅌ", "ㅂㅍ", "ㅅㅎ", "ㅈㅊ", "ㅇㅁ"), LongPressCatalog.keys("cheonjiin").map { it.id })
        assertEquals(listOf("ㅣ", "·", "ㅡ", "ㄱ", "ㅋ", "ㄴ", "ㄹ", "ㄷ", "ㅌ", "ㅂ", "ㅍ", "ㅅ", "ㅎ", "ㅈ", "ㅊ", "ㅇ", "ㅁ"), LongPressCatalog.keys("cheonjiin_plus").map { it.id })
        assertEquals("qwertyuiopasdfghjklzxcvbnm", LongPressCatalog.keys("qwerty").joinToString("") { it.id })
        assertEquals("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ", LongPressCatalog.keys("qwerty").joinToString("") { it.label })
        listOf("cheonjiin", "cheonjiin_plus", "qwerty").forEach { layout ->
            assertEquals(LongPressCatalog.keys(layout).size, LongPressCatalog.keys(layout).map { it.id }.distinct().size)
            assertTrue(LongPressCatalog.keys(layout).all { it.defaultSlots.size == 3 })
        }
    }
    @Test fun defaultsCoverAllKeypadAndQwertyNumberPairsAndExcludeFlick() {
        val pairs = listOf("1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%", "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")")
        assertEquals(pairs.map { listOf(it.first, it.second, "") }, LongPressCatalog.keys("cheonjiin").map { it.defaultSlots })
        assertEquals(pairs.map { listOf(it.first, it.second, "") }, LongPressCatalog.keys("qwerty").take(10).map { it.defaultSlots })
        assertTrue(LongPressCatalog.keys("hangul_flick").isEmpty())
        assertEquals(null, LongPressCatalog.key("hangul_flick", "ㅣ"))
    }
    @Test fun overridesPreserveEmptySlotsAndNullRestoresDefaults() {
        assertEquals(listOf("1", "!", ""), LongPressCatalog.slots("cheonjiin", "ㅣ", null))
        assertEquals(listOf("", "🙂", ""), LongPressCatalog.slots("cheonjiin", "ㅣ", listOf("", "🙂")))
        assertEquals(listOf("a", "b", "c"), LongPressCatalog.slots("cheonjiin", "ㅣ", listOf("a", "b", "c", "d")))
        assertEquals(listOf("", "", ""), LongPressCatalog.slots("unknown", "unknown", null))
        assertFalse(LongPressCatalog.storageKey("cheonjiin", "ㅣ") == LongPressCatalog.storageKey("cheonjiin_plus", "ㅣ"))
    }
    @Test fun fixedTenseCannotBeReplacedByUserSlots() {
        assertTrue(LongPressCatalog.keys("cheonjiin").all { it.fixedJamo.isEmpty() })
        mapOf("ㅋ" to "ㄲ", "ㅌ" to "ㄸ", "ㅍ" to "ㅃ", "ㅎ" to "ㅆ", "ㅊ" to "ㅉ").forEach { (key, tense) ->
            assertEquals(listOf(tense, "custom"), LongPressCatalog.choices("cheonjiin_plus", key, listOf("", "custom", "")))
            assertEquals(listOf(tense), LongPressCatalog.choices("cheonjiin_plus", key, listOf("", "", "")))
        }
        assertEquals(listOf("4", "$"), LongPressCatalog.choices("cheonjiin_plus", "ㄱ", LongPressCatalog.slots("cheonjiin_plus", "ㄱ", null)))
    }
    @Test fun pointerSequenceEmitsOnlyOneTapOrHold() {
        val gesture = LongPressGestureState()
        assertFalse(gesture.release())
        assertFalse(gesture.hold())
        gesture.down(); assertTrue(gesture.release()); assertFalse(gesture.release())
        gesture.down(); assertTrue(gesture.hold()); assertFalse(gesture.hold()); assertFalse(gesture.release())
        gesture.down(); gesture.cancel(); assertFalse(gesture.hold()); assertFalse(gesture.release())
        gesture.down(); assertTrue(gesture.hold()); gesture.cancel(); assertFalse(gesture.release())
        gesture.down(); assertTrue(gesture.release())
    }
}
