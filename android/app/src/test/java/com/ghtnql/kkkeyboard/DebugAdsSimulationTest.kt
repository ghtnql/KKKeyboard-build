package com.ghtnql.kkkeyboard

import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.KeyboardThemeChoice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugAdsSimulationTest {
    private fun withPlatform(check: (ComposeMainActivity, AppPlatform) -> Unit) {
        val controller = Robolectric.buildActivity(ComposeMainActivity::class.java).setup()
        val activity = controller.get()
        val platform = ReflectionHelpers.getField<AppPlatform>(activity, "platform")
        try {
            assertTrue(BuildConfig.MOCK_ADS)
            check(activity, platform)
            val consent = ReflectionHelpers.getField<Lazy<*>>(platform, "consent\$delegate")
            assertFalse("Debug ad operations must not initialize UMP", consent.isInitialized())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun rewardButtonGrantsAndSelectsDayThemeWithoutAds() = verifyTheme(KeyboardThemeChoice.SEOUL_DAY, KeyboardThemeMode.SEOUL_DAY)
    @Test fun rewardButtonGrantsAndSelectsNightThemeWithoutAds() = verifyTheme(KeyboardThemeChoice.SEOUL_NIGHT, KeyboardThemeMode.SEOUL_NIGHT)

    private fun verifyTheme(choice: KeyboardThemeChoice, expected: KeyboardThemeMode) = withPlatform { activity, platform ->
        assertEquals(0L, platform.seoulThemeUnlockRemainingMillis())
        assertTrue(platform.requestSeoulThemeAd(choice))
        assertEquals(expected, KeyboardThemeSettings.read(activity))
        assertTrue(platform.seoulThemeUnlockRemainingMillis() in 86_390_000L..86_400_000L)
        assertFalse(platform.isSeoulThemeAdPending())
        assertNotNull(platform.seoulThemeAdMessage())
    }

    @Test fun invalidThemeDoesNotUnlockOrChangeSelection() = withPlatform { activity, platform ->
        val before = KeyboardThemeSettings.read(activity)
        assertFalse(platform.requestSeoulThemeAd(KeyboardThemeChoice.LIGHT))
        assertEquals(0L, platform.seoulThemeUnlockRemainingMillis())
        assertEquals(before, KeyboardThemeSettings.read(activity))
    }

    @Test fun gameFinishingUsesNoAdContinuationWithoutDuplicateCallback() = withPlatform { _, platform ->
        platform.prepareGameEndAd()
        var callbacks = 0
        repeat(6) { assertFalse(platform.requestGameEndAd { callbacks++ }) }
        assertEquals(0, callbacks)
        assertFalse(platform.isAdPrivacyOptionsRequired())
        platform.showAdPrivacyOptions()
    }
}
