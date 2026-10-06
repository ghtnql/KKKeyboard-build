package com.ghtnql.kkkeyboard

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR")
class AppUiLanguageTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After fun clearPreferences() {
        context.getSharedPreferences("app_ui", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun mapsSupportedDeviceLocalesAndFallsBackToEnglish() {
        assertEquals(AppUiLanguage.JAPANESE, AppUiLanguage.fromLanguageTag("ja-JP"))
        assertEquals(AppUiLanguage.ENGLISH, AppUiLanguage.fromLanguageTag("en-US"))
        assertEquals(AppUiLanguage.KOREAN, AppUiLanguage.fromLanguageTag("ko-KR"))
        assertEquals(AppUiLanguage.ENGLISH, AppUiLanguage.fromLanguageTag("fr-FR"))
    }

    @Test fun persistsExplicitSelectionAndWrapsOnlyRequestedContext() {
        assertEquals(AppUiLanguage.KOREAN, AppUiLanguageSettings.read(context))

        AppUiLanguageSettings.write(context, AppUiLanguage.JAPANESE)

        assertEquals(AppUiLanguage.JAPANESE, AppUiLanguageSettings.read(context))
        val localized = AppUiLanguageSettings.wrap(context)
        assertEquals("キーボード設定", localized.getString(R.string.keyboard_settings))
        assertEquals("키보드 설정", context.getString(R.string.keyboard_settings))
    }
}
