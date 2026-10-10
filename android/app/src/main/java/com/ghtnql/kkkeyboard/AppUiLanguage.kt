package com.ghtnql.kkkeyboard

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppUiLanguage(val persistedValue: String, val languageTag: String) {
    KOREAN("ko", "ko"),
    JAPANESE("ja", "ja"),
    ENGLISH("en", "en");

    companion object {
        fun fromPersistedValue(value: String?): AppUiLanguage? =
            entries.firstOrNull { it.persistedValue == value }

        fun fromLanguageTag(languageTag: String?): AppUiLanguage = when (
            Locale.forLanguageTag(languageTag.orEmpty()).language
        ) {
            "ja" -> JAPANESE
            "ko" -> KOREAN
            else -> ENGLISH
        }
    }
}

/** Applies the selected language only to contexts explicitly wrapped by the host app. */
object AppUiLanguageSettings {
    private const val PREFS = "app_ui"
    private const val KEY_LANGUAGE = "language"

    fun read(context: Context): AppUiLanguage {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppUiLanguage.fromPersistedValue(stored)
            ?: AppUiLanguage.fromLanguageTag(context.resources.configuration.locales[0].toLanguageTag())
    }

    fun write(context: Context, language: AppUiLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.persistedValue)
            .apply()
    }

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(read(context).languageTag)
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
    }
}
