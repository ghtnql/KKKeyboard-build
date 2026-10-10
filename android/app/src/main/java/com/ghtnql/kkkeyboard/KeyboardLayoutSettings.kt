package com.ghtnql.kkkeyboard

import android.content.Context
import com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog

/**
 * Small, allocation-free-at-input-time layout settings surface.
 * Preferences are read when the IME view is created/restarted, never per key press.
 */
enum class KeyboardHeight(val persistedValue: String, val keyHeightDp: Int) {
    COMPACT("compact", 44),
    NORMAL("normal", 50),
    TALL("tall", 58),
    ;

    companion object {
        fun fromPersistedValue(value: String?): KeyboardHeight =
            entries.firstOrNull { it.persistedValue == value } ?: NORMAL
    }
}

enum class InputLayout(val persistedValue: String) {
    QWERTY("qwerty"),
    CHEONJIIN("cheonjiin"),
    CHEONJIIN_PLUS("cheonjiin_plus"),
    HANGUL_FLICK("hangul_flick"),
    ;

    companion object {
        fun fromPersistedValue(value: String?) = entries.firstOrNull { it.persistedValue == value } ?: CHEONJIIN
    }
}

enum class KeyboardOrientation(val preferenceSuffix: String) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape"),
    ;

    companion object {
        // Android Configuration.ORIENTATION_LANDSCAPE is 2. Keep the mapper pure so
        // local JVM tests do not need Android framework classes on their execution path.
        private const val ANDROID_ORIENTATION_LANDSCAPE = 2

        fun fromConfigurationOrientation(orientation: Int): KeyboardOrientation =
            if (orientation == ANDROID_ORIENTATION_LANDSCAPE) LANDSCAPE else PORTRAIT
    }
}

object KeyboardLayoutSettings {
    private const val PREFS_NAME = "keyboard_layout"
    private const val LEGACY_KEY_HEIGHT = "height"
    private const val LEGACY_KEY_NUMBER_ROW = "number_row"

    fun readInputLayout(context: Context): InputLayout = InputLayout.fromPersistedValue(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("input_layout", null),
    )

    fun writeInputLayout(context: Context, layout: InputLayout) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("input_layout", layout.persistedValue).apply()
    }

    fun readLongPressRevision(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("longpress_revision", 0L)

    fun readLongPressSlots(context: Context, layout: InputLayout, keyId: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val base = LongPressCatalog.storageKey(layout.persistedValue, keyId)
        val defaults = LongPressCatalog.slots(layout.persistedValue, keyId, null)
        return List(3) { prefs.getString("${base}_$it", defaults[it]) ?: defaults[it] }
    }

    fun writeLongPressSlots(context: Context, layout: InputLayout, keyId: String, slots: List<String>) {
        val base = LongPressCatalog.storageKey(layout.persistedValue, keyId)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        LongPressCatalog.slots(layout.persistedValue, keyId, slots).forEachIndexed { index, value ->
            editor.putString("${base}_$index", value)
        }
        editor.putLong("longpress_revision", readLongPressRevision(context) + 1).apply()
    }

    fun resetLongPressSlots(context: Context, layout: InputLayout, keyId: String) {
        val base = LongPressCatalog.storageKey(layout.persistedValue, keyId)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        repeat(3) { editor.remove("${base}_$it") }
        editor.putLong("longpress_revision", readLongPressRevision(context) + 1).apply()
    }

    fun readFlickDistance(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt("flick_distance_dp", 20).coerceIn(12, 32)

    fun writeFlickDistance(context: Context, distanceDp: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("flick_distance_dp", distanceDp.coerceIn(12, 32)).apply()
    }

    fun readCheonjiinCycleTimeout(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt("cheonjiin_cycle_timeout_ms", 900).coerceIn(400, 1_600)

    fun writeCheonjiinCycleTimeout(context: Context, timeoutMillis: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("cheonjiin_cycle_timeout_ms", timeoutMillis.coerceIn(400, 1_600))
            .apply()
    }

    private fun heightKey(orientation: KeyboardOrientation) = "height_${orientation.preferenceSuffix}"
    private fun numberRowKey(orientation: KeyboardOrientation) = "number_row_${orientation.preferenceSuffix}"

    fun readHeight(context: Context, orientation: KeyboardOrientation): KeyboardHeight {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = preferences.getString(
            heightKey(orientation),
            preferences.getString(LEGACY_KEY_HEIGHT, null),
        )
        return KeyboardHeight.fromPersistedValue(value)
    }

    fun writeHeight(context: Context, orientation: KeyboardOrientation, height: KeyboardHeight) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(heightKey(orientation), height.persistedValue)
            .apply()
    }

    fun readNumberRowEnabled(context: Context, orientation: KeyboardOrientation): Boolean {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (preferences.contains(numberRowKey(orientation))) {
            preferences.getBoolean(numberRowKey(orientation), false)
        } else {
            preferences.getBoolean(LEGACY_KEY_NUMBER_ROW, false)
        }
    }

    fun writeNumberRowEnabled(context: Context, orientation: KeyboardOrientation, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(numberRowKey(orientation), enabled)
            .apply()
    }

    fun readHapticFeedbackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("haptic_feedback_enabled", true)

    fun writeHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("haptic_feedback_enabled", enabled)
            .apply()
    }

}
