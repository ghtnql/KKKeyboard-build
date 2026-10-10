package com.ghtnql.kkkeyboard

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import android.content.ContentProvider
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp
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
class ComposeRecoveryRenderingTest {
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

    @Test fun keyboardSettingsIntentOpensSharedSettingsOnReusedActivity() {
        ui.runOnUiThread {
            ReflectionHelpers.callInstanceMethod<Unit>(ui.activity, "onNewIntent", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent(ui.activity.intent).putExtra("open_shared_settings", true)))
        }
        ui.onNodeWithText("시스템 키보드 설정").assertExists()
        capture("shared-settings-from-keyboard")
        ui.onNodeWithText("앱 + 키보드 테마").performScrollTo().performClick()
        ui.onNodeWithText("라이트").assertExists()
        ui.runOnUiThread {
            ReflectionHelpers.callInstanceMethod<Unit>(ui.activity, "onNewIntent", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent(ui.activity.intent).putExtra("open_shared_settings", true)))
        }
        ui.onNodeWithText("시스템 키보드 설정").assertExists()
        ui.onNodeWithText("입력 세부 설정").performScrollTo().performClick()
        capture("shared-advanced-settings")
    }

    @Test fun sharedPhraseManagerDisplaysTenBundledDefaults() {
        ui.runOnUiThread {
            UserPhraseStore.clear(ui.activity)
            ReflectionHelpers.callInstanceMethod<Unit>(ui.activity, "onNewIntent", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent(ui.activity.intent).putExtra("open_shared_settings", true)))
        }
        ui.onNodeWithText("상용구 관리").performScrollTo().performClick()
        ui.onNodeWithText("こんにちは").assertExists()
        capture("shared-default-phrases-top")
        ui.onNodeWithText("また明日。").performScrollTo().assertExists()
        capture("shared-default-phrases-bottom")
        org.junit.Assert.assertEquals(10, UserPhraseStore.read(ui.activity).size)
    }

    @Test fun sharedThemePanelDisplaysUpcomingThemesAndRestoredSeoulDay() {
        ui.runOnUiThread {
            val platform = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
            ui.activity.setContentView(ComposeView(ui.activity).apply {
                setContent { KKKeyboardApp(platform, settingsNavigationRequest = 1) }
            })
        }
        ui.onNodeWithText("앱 + 키보드 테마").performScrollTo().performClick()
        ui.onNodeWithText("라이트").assertExists()
        ui.onNodeWithText("다크").assertExists()
        capture("shared-themes-top")
        ui.onNodeWithText("태극").performScrollTo().assertExists()
        ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 400f) }
        capture("shared-themes-taegeuk")
        ui.onNodeWithText("서울의 낮").performScrollTo().assertExists()
        ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 400f) }
        capture("shared-themes-seoul-day")
        ui.onNodeWithText("부산의 밤").performScrollTo().assertExists()
        ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 250f) }
        capture("shared-themes-upcoming")
    }
}
