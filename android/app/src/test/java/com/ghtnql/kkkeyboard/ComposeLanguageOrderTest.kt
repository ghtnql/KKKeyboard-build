package com.ghtnql.kkkeyboard

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class ComposeLanguageOrderTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()

    @Test fun longPressDragPersistsOrderThroughActualLauncherAdapter() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as android.content.ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
        val platform = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        platform.setUiLanguage("ko")
        platform.setKeyboardLanguageOrder(listOf("korean", "japanese", "english"))
        fun install() {
            ui.runOnUiThread {
                ui.activity.setContentView(ComposeView(ui.activity).apply {
                    setContent { KKKeyboardApp(platform, settingsNavigationRequest = 1) }
                })
            }
            ui.waitForIdle()
        }
        install()
        ui.onNodeWithTag("keyboard-language-order-korean").performScrollTo()
        ui.onNodeWithTag("keyboard-language-order-korean").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, height.toFloat() * 2))
            advanceEventTime(100)
            up()
        }
        ui.waitForIdle()
        assertEquals(listOf("japanese", "english", "korean"), platform.readKeyboardLanguageOrder())
        install()
        ui.onNodeWithTag("keyboard-language-order").performScrollTo().assertIsDisplayed()
        val japaneseTop = ui.onNodeWithTag("keyboard-language-order-japanese").fetchSemanticsNode().boundsInRoot.top
        val englishTop = ui.onNodeWithTag("keyboard-language-order-english").fetchSemanticsNode().boundsInRoot.top
        val koreanTop = ui.onNodeWithTag("keyboard-language-order-korean").fetchSemanticsNode().boundsInRoot.top
        assertTrue(japaneseTop < englishTop && englishTop < koreanTop)
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/language-order/settings-reordered.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
