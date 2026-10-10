package com.ghtnql.kkkeyboard

import android.content.Context

enum class KeyboardLanguage {
    KOREAN,
    JAPANESE,
    ENGLISH,
    ;

    fun next(order: List<KeyboardLanguage> = entries): KeyboardLanguage {
        val valid = KeyboardLanguageOrderSettings.normalizeOrder(order)
        val index = valid.indexOf(this).coerceAtLeast(0)
        return valid[(index + 1) % valid.size]
    }

    fun effectiveLayout(preferredHangulLayout: InputLayout): InputLayout =
        if (this == ENGLISH) InputLayout.QWERTY else preferredHangulLayout
}

object KeyboardLanguageOrderSettings {
    private const val PREFS = "keyboard_input_mode"
    private const val KEY = "keyboard_language_order"

    fun normalizeOrder(values: Iterable<KeyboardLanguage>): List<KeyboardLanguage> {
        val result = values.filter { it in KeyboardLanguage.entries }.distinct().toMutableList()
        KeyboardLanguage.entries.forEach { if (it !in result) result += it }
        return result
    }

    fun read(context: Context): List<KeyboardLanguage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw == null) return KeyboardLanguage.entries
        val parsed = raw.split(',').mapNotNull { value ->
            runCatching { KeyboardLanguage.valueOf(value) }.getOrNull()
        }
        return normalizeOrder(parsed)
    }

    fun write(context: Context, order: List<KeyboardLanguage>) {
        val normalized = normalizeOrder(order)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, normalized.joinToString(",") { it.name })
            .apply()
    }
}
