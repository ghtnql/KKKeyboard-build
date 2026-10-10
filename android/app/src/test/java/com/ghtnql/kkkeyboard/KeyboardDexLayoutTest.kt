package com.ghtnql.kkkeyboard

import android.content.Context
import android.content.res.Configuration
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
@Config(sdk = [35], qualifiers = "ko-rKR-sw720dp-land-mdpi")
class KeyboardDexLayoutTest {
    private val services = mutableListOf<ServiceController<KoreanKeyboardService>>()
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        app.getSharedPreferences("keyboard_layout", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("keyboard_input_mode", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanup() {
        services.forEach { it.destroy() }
        clearPreferences()
    }

    private fun createService(): KoreanKeyboardService =
        Robolectric.buildService(KoreanKeyboardService::class.java).create()
            .also { services += it }
            .get()

    private fun inputLayout(service: KoreanKeyboardService): InputLayout =
        ReflectionHelpers.getField(service, "inputLayout")

    private fun layoutOrientation(service: KoreanKeyboardService): KeyboardOrientation =
        ReflectionHelpers.getField(service, "layoutOrientation")

    private fun dexConfiguration(service: KoreanKeyboardService) =
        Configuration(service.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_DESK
            screenWidthDp = 1920
            screenHeightDp = 1080
            smallestScreenWidthDp = 720
        }

    @Test
    fun dexConfigurationChangeRestoresEveryPersistedUserLayout() {
        val service = createService()
        InputLayout.entries.forEach { preferred ->
            KeyboardLayoutSettings.writeInputLayout(app, preferred)
            ReflectionHelpers.setField(
                service,
                "inputLayout",
                if (preferred == InputLayout.QWERTY) InputLayout.CHEONJIIN else InputLayout.QWERTY,
            )

            service.onConfigurationChanged(dexConfiguration(service))

            assertEquals(preferred, inputLayout(service))
            assertEquals(KeyboardOrientation.LANDSCAPE, layoutOrientation(service))
        }
    }

    @Test
    fun recreatedDexInputViewLoadsCheonjiinInsteadOfQwertyFallback() {
        KeyboardLayoutSettings.writeInputLayout(app, InputLayout.CHEONJIIN)
        val service = createService()

        service.onCreateInputView()

        assertEquals(InputLayout.CHEONJIIN, inputLayout(service))
        assertEquals(KeyboardOrientation.LANDSCAPE, layoutOrientation(service))
    }
}
