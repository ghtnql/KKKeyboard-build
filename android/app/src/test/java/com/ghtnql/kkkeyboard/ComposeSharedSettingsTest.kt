package com.ghtnql.kkkeyboard

import android.content.ContentProvider
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp
import com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout
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
class ComposeSharedSettingsTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()

    @Before fun initializeResources() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/recovery/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun hapticSwitchPersistsThroughTheRealPlatformAdapter() {
        val platform = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        assertEquals(true, platform.readHapticFeedbackEnabled())
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, settingsNavigationRequest = 1) }
            })
        }
        ui.onNodeWithContentDescription("키 입력 햅틱").performScrollTo().performClick()
        assertEquals(false, KeyboardLayoutSettings.readHapticFeedbackEnabled(ui.activity))
        assertEquals(false, platform.readHapticFeedbackEnabled())
        capture("shared-settings-haptic-off")
        ui.onNodeWithContentDescription("키 입력 햅틱").performClick()
        assertEquals(true, KeyboardLayoutSettings.readHapticFeedbackEnabled(ui.activity))
    }

    @Test fun dropdownPersistsAllLayoutsAndBackStaysVisibleAfterScrolling() {
        val delegate = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        var selected = KeyboardInputLayout.CHEONJIIN
        val platform = object : AppPlatform by delegate {
            override fun readInputLayout() = selected
            override fun setInputLayout(layout: KeyboardInputLayout) { selected = layout }
        }
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, settingsNavigationRequest = 1) }
            })
        }

        ui.onNodeWithContentDescription("한글 입력 배열 선택").performScrollTo().performClick()
        capture("shared-layout-dropdown-four-options")
        listOf("천지인", "천지인 플러스", "QWERTY", "한글 플릭").forEach {
            ui.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        ui.onNodeWithText("천지인", useUnmergedTree = true).performClick()

        listOf(KeyboardInputLayout.CHEONJIIN_PLUS, KeyboardInputLayout.QWERTY, KeyboardInputLayout.HANGUL_FLICK, KeyboardInputLayout.CHEONJIIN).forEach { choice ->
            ui.onNodeWithContentDescription("한글 입력 배열 선택").performScrollTo().performClick()
            ui.onNodeWithText(choice.title, useUnmergedTree = true).performClick()
            assertEquals(choice, selected)
        }
        ui.onNodeWithText("상용구 관리").performScrollTo()
        ui.onNodeWithContentDescription("뒤로").assertIsDisplayed()
        capture("shared-settings-scrolled-pinned-back")
    }

    @Test fun extensionSettingsBackReturnsFromThemesThenCloses() {
        val platform = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        var closeCount = 0
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, keyboardExtensionMode = true, onCloseSettings = { closeCount++ }) }
            })
        }
        ui.onAllNodesWithText("시스템 키보드 설정").assertCountEquals(0)
        ui.onAllNodesWithText("상용구 관리").assertCountEquals(0)
        ui.onNodeWithText("앱 + 키보드 테마").performScrollTo().performClick()
        ui.onNodeWithContentDescription("뒤로").assertIsDisplayed().performClick()
        ui.onNodeWithText("한글 입력 배열").assertIsDisplayed()
        ui.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(1, closeCount)
    }

    @Test fun tappingOutsideTestFieldClearsInputFocus() {
        val platform = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, settingsNavigationRequest = 1) }
            })
        }
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithText("키보드 테스트").performScrollTo().performClick()
        val field = ui.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("abc")
        field.assertIsFocused()
        capture("shared-test-input-focused")
        ui.onNodeWithText("아래 칸에 입력해 보세요").performClick()
        field.assertIsNotFocused()
        field.assertTextContains("abc")
        capture("shared-test-outside-tap-dismissed")
    }
    @Test fun longPressSlotsPersistEmptyAndCustomValuesAndResetOnlySelectedKey() {
        val platform = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        platform.setInputLayout(KeyboardInputLayout.CHEONJIIN)
        platform.setLongPressSlots(KeyboardInputLayout.CHEONJIIN, "·", listOf("other", "", ""))
        val before = KeyboardLayoutSettings.readLongPressRevision(ui.activity)
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, settingsNavigationRequest = 1) }
            })
        }
        ui.onNodeWithContentDescription("기호 1").performScrollTo().performTextReplacement("")
        ui.onNodeWithContentDescription("기호 2").performTextReplacement("custom")
        assertEquals(listOf("", "custom", ""), platform.readLongPressSlots(KeyboardInputLayout.CHEONJIIN, "ㅣ"))
        assertEquals(true, KeyboardLayoutSettings.readLongPressRevision(ui.activity) > before)
        ui.onNodeWithContentDescription("기호를 편집할 배열").performScrollTo().performClick()
        listOf("천지인", "천지인 플러스", "QWERTY").forEach {
            ui.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        ui.onAllNodesWithText("한글 플릭", useUnmergedTree = true).assertCountEquals(0)
        ui.onNodeWithText("천지인 플러스", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("기호 1").performScrollTo().performTextReplacement("plus")
        assertEquals("plus", platform.readLongPressSlots(KeyboardInputLayout.CHEONJIIN_PLUS, "ㅣ")[0])
        assertEquals("", platform.readLongPressSlots(KeyboardInputLayout.CHEONJIIN, "ㅣ")[0])
        ui.onNodeWithText("이 키 초기화").performScrollTo().performClick()
        assertEquals(listOf("1", "!", ""), platform.readLongPressSlots(KeyboardInputLayout.CHEONJIIN_PLUS, "ㅣ"))
        assertEquals("other", platform.readLongPressSlots(KeyboardInputLayout.CHEONJIIN, "·")[0])
        val prefs = ui.activity.getSharedPreferences("keyboard_layout", 0)
        val base = com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog.storageKey("cheonjiin_plus", "ㅣ")
        repeat(3) { assertEquals(false, prefs.contains("${base}_$it")) }
        capture("shared-settings-longpress-slots")
    }

    @Test fun extensionLongPressEditorIsReadOnlyButCatalogDropdownsRemainUsable() {
        val delegate = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        var writes = 0
        var resets = 0
        val platform = object : AppPlatform by delegate {
            override fun canEditLongPressSymbols() = false
            override fun readInputLayout() = KeyboardInputLayout.CHEONJIIN
            override fun readLongPressSlots(layout: KeyboardInputLayout, keyId: String): List<String> =
                if (layout == KeyboardInputLayout.CHEONJIIN && keyId == "ㅣ") listOf("custom", "", "third")
                else com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog.slots(layout.persistedValue, keyId, null)
            override fun setLongPressSlots(layout: KeyboardInputLayout, keyId: String, slots: List<String>) { writes++ }
            override fun resetLongPressSlots(layout: KeyboardInputLayout, keyId: String) { resets++ }
        }
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, keyboardExtensionMode = true) }
            })
        }
        ui.onNodeWithContentDescription("기호 1").performScrollTo().assertTextContains("custom").assert(hasSetTextAction().not())
        ui.onNodeWithContentDescription("기호 3").assertTextContains("third").assert(hasSetTextAction().not())
        ui.onNodeWithText("이 키 초기화").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithContentDescription("기호를 편집할 배열").performScrollTo().performClick()
        ui.onNodeWithText("천지인 플러스", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("기호를 편집할 키").performClick()
        ui.onNodeWithText("ㅋ", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("기호 1").performScrollTo().assertTextContains(",").assert(hasSetTextAction().not())
        ui.onNodeWithText("이 키 초기화").performScrollTo().assertIsNotEnabled()
        assertEquals(0, writes)
        assertEquals(0, resets)
        capture("shared-settings-extension-longpress-readonly")
    }

}
