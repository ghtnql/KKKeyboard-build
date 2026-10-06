package com.ghtnql.kkkeyboard

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.AdRemovalState
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp
import com.ghtnql.kkkeyboard.sharedui.KeyboardThemeChoice
import com.ghtnql.kkkeyboard.sharedui.KeyboardStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeAdRemovalTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()
    private var state = AdRemovalState()
    private var reads = 0
    private var refreshes = 0
    private var purchases = 0
    private var restores = 0
    private var refundLinks = 0
    private var privacyLinks = 0
    private var privacyOptions = 0
    private var rewardedRequests = 0

    @Before fun resources() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as android.content.ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
    }

    private fun install(language: String = "ko", extension: Boolean = false, optionsRequired: Boolean = false) {
        val delegate = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        val probe = object : AppPlatform by delegate {
            override val keyboardStatus = KeyboardStatus.ACTIVE
            override fun readUiLanguage() = language
            override fun readAdRemovalState(): AdRemovalState { reads++; return state }
            override fun refreshAdRemoval() { refreshes++ }
            override fun purchaseAdRemoval() {
                purchases++
                state = state.copy(busy = true, messageCode = "pending")
            }
            override fun restoreAdRemoval() {
                restores++
                state = state.copy(owned = true, busy = false, messageCode = "restored")
            }
            override fun openRefundInformation() { refundLinks++ }
            override fun openPrivacyPolicy() { privacyLinks++ }
            override fun isAdPrivacyOptionsRequired() = optionsRequired
            override fun showAdPrivacyOptions() { privacyOptions++ }
            override fun seoulThemeUnlockRemainingMillis() = 0L
            override fun requestSeoulThemeAd(theme: KeyboardThemeChoice): Boolean { rewardedRequests++; return false }
            override fun stopGameAudio() = Unit
        }
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(probe, settingsNavigationRequest = 1, keyboardExtensionMode = extension) }
            })
        }
        ui.waitForIdle()

    }

    private fun assertNoPaidActions(vararg labels: String) {
        labels.forEach { ui.onNodeWithText(it, substring = true).assertDoesNotExist() }
        assertEquals(0, reads)
        assertEquals(0, refreshes)
        assertEquals(0, purchases)
        assertEquals(0, restores)
        assertEquals(0, refundLinks)
        assertEquals(0, rewardedRequests)
    }

    @Test fun koreanSettingsRemainPrivacyOnlyWithBuyableStore() {
        state = AdRemovalState(purchaseAvailable = true, price = "₩3,900", busy = true, messageCode = "pending")
        install(optionsRequired = true)
        ui.onNodeWithText("개인정보처리방침").performScrollTo().performClick()
        ui.onNodeWithText("개인정보 옵션").performScrollTo().performClick()
        assertNoPaidActions("광고 제거", "구매", "환불", "₩3,900", "처리 중", "가격")
        assertEquals(1, privacyLinks)
        assertEquals(1, privacyOptions)
        capture("korean-ads-only-privacy")
    }

    @Test fun japaneseSettingsRemainPrivacyOnlyWithOwnedStore() {
        state = AdRemovalState(owned = true, purchaseAvailable = true, price = "￥480", messageCode = "restored")
        install(language = "ja", optionsRequired = true)
        ui.onNodeWithText("プライバシーポリシー").performScrollTo().performClick()
        ui.onNodeWithText("プライバシーオプション").performScrollTo().performClick()
        assertNoPaidActions("広告を削除", "購入", "返金", "￥480", "処理中", "価格")
        assertEquals(1, privacyLinks)
        assertEquals(1, privacyOptions)
        capture("japanese-ads-only-privacy")
    }

    @Test fun englishSettingsRemainPrivacyOnlyWithoutRequiredConsentOptions() {
        state = AdRemovalState(owned = true, purchaseAvailable = true, price = "$3.99")
        install(language = "en")
        ui.onNodeWithText("Privacy policy").performScrollTo().performClick()
        ui.onNodeWithText("Privacy options").assertDoesNotExist()
        assertNoPaidActions("Remove ads", "Buy for", "Restore purchase", "Refund", "$3.99", "Working", "Price", "Premium")
        assertEquals(1, privacyLinks)
        assertEquals(0, privacyOptions)
    }

    @Test fun keyboardExtensionNeverExposesPurchaseOrAdConsentControls() {
        state = AdRemovalState(owned = true, purchaseAvailable = true, price = "₩3,900")
        install(extension = true, optionsRequired = true)
        assertNoPaidActions("광고 제거", "구매", "환불", "₩3,900")
        ui.onNodeWithText("개인정보 옵션").assertDoesNotExist()
        ui.onNodeWithText("개인정보처리방침").assertDoesNotExist()
    }

    @Test fun ownedStoreDoesNotReplaceRewardedThemeAccessWithPaidLabel() {
        state = AdRemovalState(owned = true, purchaseAvailable = true, price = "₩3,900")
        install()
        ui.onNodeWithText("앱 + 키보드 테마").performScrollTo().performClick()
        ui.onAllNodesWithText("광고 보고 24시간 쓰기").assertCountEquals(2)
        assertNoPaidActions("광고 제거", "구매", "환불", "₩3,900")
    }

    @Test fun keyboardExtensionThemesShowAppGuidanceWithoutRewardedAdControls() {
        state = AdRemovalState(owned = true, purchaseAvailable = true, price = "₩3,900")
        install(extension = true, optionsRequired = true)
        ui.onNodeWithText("앱 + 키보드 테마").performScrollTo().performClick()
        ui.onAllNodesWithText("본체 앱의 테마 화면에서 설정하세요.").assertCountEquals(2)
        ui.onAllNodesWithText("본체 앱의 테마 화면에서 설정하세요.")[0].performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("광고 보고 24시간 쓰기").assertDoesNotExist()
        ui.onNodeWithText("광고를 보면 24시간 이용").assertDoesNotExist()
        assertNoPaidActions("광고 제거", "구매", "환불", "₩3,900")
        capture("keyboard-extension-theme-guidance")
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/ad-removal/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
