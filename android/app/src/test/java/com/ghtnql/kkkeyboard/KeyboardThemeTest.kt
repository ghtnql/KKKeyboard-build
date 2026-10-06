package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardThemeTest {
    @Test fun explicitThemeOverridesSystemModeAndPersists() {
        val context = RuntimeEnvironment.getApplication()
        KeyboardThemeSettings.write(context, KeyboardThemeMode.DARK)
        assertEquals(KeyboardThemeMode.DARK, KeyboardThemeSettings.read(context))
        assertEquals(KeyboardThemeSettings.DARK, KeyboardThemeSettings.palette(context))

        KeyboardThemeSettings.write(context, KeyboardThemeMode.LIGHT)
        assertEquals(KeyboardThemeSettings.LIGHT, KeyboardThemeSettings.palette(context))
    }

    @Test fun allModesRoundTripThroughPrefs() {
        val context = RuntimeEnvironment.getApplication()
        KeyboardThemeSettings.grantSeoulUnlock(context)
        KeyboardThemeMode.entries.forEach { mode ->
            KeyboardThemeSettings.write(context, mode)
            assertEquals(mode, KeyboardThemeSettings.read(context))
            assertEquals(mode, KeyboardThemeMode.fromPersistedValue(mode.persistedValue))
        }
        assertEquals(KeyboardThemeMode.SYSTEM, KeyboardThemeMode.fromPersistedValue("bogus"))
        assertEquals(KeyboardThemeMode.SYSTEM, KeyboardThemeMode.fromPersistedValue(null))
    }

    @Test fun stalePaidEntitlementCannotBypassRewardedThemeExpiry() {
        val context = RuntimeEnvironment.getApplication()
        val now = 1_000L
        context.getSharedPreferences("ad_removal_entitlement", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("owned", true).commit()
        assertEquals(0L, KeyboardThemeSettings.seoulUnlockRemainingMillis(context, now))
        assertEquals(false, KeyboardThemeSettings.write(context, KeyboardThemeMode.SEOUL_DAY, now))
        KeyboardThemeSettings.grantSeoulUnlock(context, now)
        assertEquals(KeyboardThemeSettings.SEOUL_UNLOCK_DURATION_MILLIS,
            KeyboardThemeSettings.seoulUnlockRemainingMillis(context, now))
        assertEquals(true, KeyboardThemeSettings.write(context, KeyboardThemeMode.SEOUL_DAY, now))
        assertEquals(KeyboardThemeMode.LIGHT,
            KeyboardThemeSettings.read(context, now + KeyboardThemeSettings.SEOUL_UNLOCK_DURATION_MILLIS))
    }

    @Test fun unavailableRegionalSelectionFallsBackToSystem() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyboard_layout", android.content.Context.MODE_PRIVATE)
            .edit().putString("theme", "busan_ocean").commit()
        assertEquals(KeyboardThemeMode.SYSTEM, KeyboardThemeSettings.read(context))
        assertEquals(false, ThemeCatalog.load(context)?.firstOrNull { it.id == "busan_ocean" }?.available)
    }
}
