package com.ghtnql.kkkeyboard

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR")
class KeyboardInitialModeTest {
    private val services = mutableListOf<ServiceController<KoreanKeyboardService>>()
    private val preferences get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("keyboard_input_mode", Context.MODE_PRIVATE)

    @Before
    fun clearSelection() { preferences.edit().clear().commit() }

    @After
    fun cleanup() {
        services.forEach { it.destroy() }
        preferences.edit().clear().commit()
    }

    private fun createService(): KoreanKeyboardService =
        Robolectric.buildService(KoreanKeyboardService::class.java).create()
            .also { services += it }.get()

    private fun language(service: KoreanKeyboardService): KeyboardLanguage =
        ReflectionHelpers.getField(service, "keyboardLanguage")

    @Test
    @Config(qualifiers = "ja-rJP")
    fun japaneseSystemStartsJapanese() {
        assertEquals(KeyboardLanguage.JAPANESE, language(createService()))
    }

    @Test
    fun koreanSystemStartsKorean() {
        assertEquals(KeyboardLanguage.KOREAN, language(createService()))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun englishSystemFallsBackToKorean() {
        assertEquals(KeyboardLanguage.KOREAN, language(createService()))
    }

    @Test
    @Config(qualifiers = "ja-rJP")
    fun savedKoreanOverridesJapaneseSystem() {
        preferences.edit().putString("keyboard_language", "KOREAN").commit()
        assertEquals(KeyboardLanguage.KOREAN, language(createService()))
    }

    @Test
    fun savedJapaneseOverridesKoreanSystem() {
        preferences.edit().putString("keyboard_language", "JAPANESE").commit()
        assertEquals(KeyboardLanguage.JAPANESE, language(createService()))
    }

    @Test
    @Config(qualifiers = "ja-rJP")
    fun invalidSavedSelectionFallsBackToSystem() {
        preferences.edit().putString("keyboard_language", "unsupported").commit()
        assertEquals(KeyboardLanguage.JAPANESE, language(createService()))
    }

    @Test
    fun modeCyclePersistsForRecreatedServiceIndependentlyOfLayout() {
        KeyboardLayoutSettings.writeInputLayout(RuntimeEnvironment.getApplication(), InputLayout.HANGUL_FLICK)
        val service = createService()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "cycleKeyboardLanguage")
        assertEquals("JAPANESE", preferences.getString("keyboard_language", null))
        assertEquals(KeyboardLanguage.JAPANESE, language(createService()))
        assertEquals(InputLayout.HANGUL_FLICK, KeyboardLayoutSettings.readInputLayout(service))
        ReflectionHelpers.callInstanceMethod<Unit>(service, "cycleKeyboardLanguage")
        assertEquals("ENGLISH", preferences.getString("keyboard_language", null))
        assertEquals(KeyboardLanguage.ENGLISH, language(createService()))
        ReflectionHelpers.callInstanceMethod<Unit>(service, "cycleKeyboardLanguage")
        assertEquals("KOREAN", preferences.getString("keyboard_language", null))
        assertEquals(KeyboardLanguage.KOREAN, language(createService()))
    }
}
