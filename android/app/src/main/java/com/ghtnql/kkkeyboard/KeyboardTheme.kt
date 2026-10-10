package com.ghtnql.kkkeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

enum class KeyboardThemeMode(val persistedValue: String) {
    SYSTEM("system"), LIGHT("basic_light"), DARK("basic_dark"),
    SEOUL_DAY("seoul_day"), SEOUL_NIGHT("seoul_night");

    companion object {
        fun fromPersistedValue(value: String?) = entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
    }
}

data class KeyboardPalette(
    val keyboardSurface: Int,
    val keySurface: Int,
    val controlSurface: Int,
    val border: Int,
    val text: Int,
    val accent: Int,
    val onAccent: Int,
    val ripple: Int,
    val flickHint: Int,
    val flickSelected: Int,
)

object KeyboardThemeSettings {
    private const val PREFS = "keyboard_layout"
    private const val KEY = "theme"
    private const val KEY_SEOUL_UNLOCK_EXPIRY = "theme_seoul_unlock_expiry"

    const val SEOUL_UNLOCK_DURATION_MILLIS: Long = 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readStored(context: Context): KeyboardThemeMode =
        KeyboardThemeMode.fromPersistedValue(prefs(context).getString(KEY, null))

    private fun readExpiryMillis(context: Context): Long =
        prefs(context).getLong(KEY_SEOUL_UNLOCK_EXPIRY, 0L)

    @JvmOverloads
    fun seoulUnlockRemainingMillis(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val expiry = readExpiryMillis(context)
        if (expiry <= 0L || expiry <= nowMillis) return 0L
        return expiry - nowMillis
    }

    @JvmOverloads
    fun isSeoulUnlocked(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = seoulUnlockRemainingMillis(context, nowMillis) > 0L

    @JvmOverloads
    fun grantSeoulUnlock(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val expiry = nowMillis + SEOUL_UNLOCK_DURATION_MILLIS
        prefs(context).edit().putLong(KEY_SEOUL_UNLOCK_EXPIRY, expiry).apply()
        return expiry
    }

    @JvmOverloads
    fun read(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): KeyboardThemeMode {
        val stored = readStored(context)
        if (stored != KeyboardThemeMode.SEOUL_DAY && stored != KeyboardThemeMode.SEOUL_NIGHT) {
            return stored
        }
        if (isSeoulUnlocked(context, nowMillis)) return stored
        val fallback = if (stored == KeyboardThemeMode.SEOUL_DAY) KeyboardThemeMode.LIGHT else KeyboardThemeMode.DARK
        prefs(context).edit()
            .putString(KEY, fallback.persistedValue)
            .remove(KEY_SEOUL_UNLOCK_EXPIRY)
            .apply()
        return fallback
    }

    @JvmOverloads
    fun write(
        context: Context,
        mode: KeyboardThemeMode,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if ((mode == KeyboardThemeMode.SEOUL_DAY || mode == KeyboardThemeMode.SEOUL_NIGHT) &&
            !isSeoulUnlocked(context, nowMillis)
        ) {
            return false
        }
        prefs(context).edit().putString(KEY, mode.persistedValue).apply()
        return true
    }

    // Portrait night loads the wide seoul_night.jpg; KoreanKeyboardService crops
    // a deterministic left 8:5 region (x=0..~768 of 1440x480) so Jamsil/Lotte
    // World Tower shows instead of the Namsan view in seoul_night_portrait.jpg.
    // Day and all landscape selections are unchanged.
    fun backgroundAsset(context: Context, orientation: KeyboardOrientation): String? = when (read(context)) {
        KeyboardThemeMode.SEOUL_DAY -> if (orientation == KeyboardOrientation.PORTRAIT) "seoul_day_portrait.jpg" else "seoul_day.jpg"
        KeyboardThemeMode.SEOUL_NIGHT -> "seoul_night.jpg"
        else -> null
    }

    fun palette(context: Context): KeyboardPalette {
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = when (read(context)) {
            KeyboardThemeMode.SYSTEM -> systemDark
            KeyboardThemeMode.LIGHT -> false
            KeyboardThemeMode.DARK -> true
            KeyboardThemeMode.SEOUL_DAY -> false
            KeyboardThemeMode.SEOUL_NIGHT -> true
        }
        return when (read(context)) {
            KeyboardThemeMode.SEOUL_DAY -> SEOUL_DAY
            KeyboardThemeMode.SEOUL_NIGHT -> SEOUL_NIGHT
            else -> if (dark) DARK else LIGHT
        }
    }

    val LIGHT = KeyboardPalette(
        Color.rgb(236, 238, 241), Color.WHITE, Color.rgb(220, 225, 231),
        Color.rgb(199, 205, 212), Color.rgb(32, 36, 42), Color.rgb(33, 107, 87),
        Color.WHITE, Color.argb(48, 33, 107, 87), Color.rgb(82, 96, 106), Color.rgb(215, 235, 227),
    )
    val DARK = KeyboardPalette(
        Color.rgb(22, 26, 29), Color.rgb(43, 49, 54), Color.rgb(58, 66, 72),
        Color.rgb(86, 97, 106), Color.rgb(245, 247, 248), Color.rgb(90, 200, 168),
        Color.rgb(11, 33, 26), Color.argb(64, 90, 200, 168), Color.rgb(180, 191, 199), Color.rgb(55, 91, 81),
    )

    val SEOUL_DAY = KeyboardPalette(
        Color.rgb(216, 231, 237), Color.argb(198, 250, 252, 253), Color.argb(190, 215, 235, 247),
        Color.rgb(126, 177, 208), Color.rgb(28, 48, 67), Color.rgb(68, 132, 180),
        Color.rgb(17, 54, 82), Color.argb(55, 83, 155, 198), Color.rgb(71, 94, 110), Color.rgb(203, 228, 232),
    )
    val SEOUL_NIGHT = KeyboardPalette(
        Color.rgb(24, 37, 64), Color.argb(158, 92, 103, 150), Color.argb(166, 52, 65, 111),
        Color.rgb(190, 207, 255), Color.WHITE, Color.rgb(93, 121, 192),
        Color.WHITE, Color.argb(76, 178, 198, 255), Color.rgb(220, 225, 247), Color.rgb(84, 103, 163),
    )
}
