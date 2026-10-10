package com.ghtnql.kkkeyboard

import android.content.ContentProvider
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import com.ghtnql.kkkeyboard.sharedui.PracticeTranslationCatalog
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
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

/** Runs the actual launcher and its native adapter with a Japanese system locale. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ja-rJP-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeJapaneseLocalizationTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()
    private val platform get() = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")

    @Before fun resources() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
    }
    private fun capture(name: String, popup: Boolean = false) {
        ui.waitForIdle()
        ui.runOnUiThread {
            val view = if (popup) android.view.inspector.WindowInspector.getGlobalWindowViews().last()
                else ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/japanese-localization/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun visibleText(): List<String> = ui.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true
    ).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text] }.map { it.text }
    private fun assertJapaneseChrome() {
        val unexpected = visibleText().filter { text -> text.any { it in '가'..'힣' } && text != "한국어" }
        assertTrue("Unexpected Korean app chrome: $unexpected", unexpected.isEmpty())
    }
    private fun home() { ui.onNodeWithText("練習スタート").performClick() }
    private fun settings() { home(); ui.onNodeWithContentDescription("設定").performClick() }

    @Test fun hangulLettersHaveJapaneseControlsAndCanBeClosedWithoutSystemBack() {
        home()
        ui.onNodeWithText("ハングル学習").performScrollTo().performClick()
        ui.onNodeWithTag("hiragana-guide-close").performClick()
        ui.onNodeWithText("ハングル文字学習").assertExists()
        ui.onNodeWithText("子音").assertExists()
        ui.onNodeWithText("母音").assertExists()
        ui.onNodeWithText("文字づくり").performClick()
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("発音の目安", substring = true)
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("カ", substring = true)
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("か", substring = true)
        ui.onNodeWithText("子音").performClick()
        capture("jamo-list")
        ui.onNodeWithText("ㄱ").performClick()
        ui.onNodeWithText("기역").assertExists()
        ui.onNodeWithTag("jamo-reading").assertExists()
        ui.onNodeWithText("確認問題").performScrollTo().performClick()
        ui.onNodeWithText("ハングル読みを選んでください").assertExists()
        ui.onNodeWithTag("jamo-reading").assertDoesNotExist()
        ui.onNodeWithText("기역").assertDoesNotExist()
        ui.onNodeWithTag("jamo-word-question").assertExists()
        capture("jamo-quiz")
        ui.onAllNodesWithText("閉じる").onFirst().performScrollTo().performClick()
        ui.onNodeWithText("ハングルカフェ").assertExists()
    }

    @Test fun systemJapaneseOnboardingAndHomeUseRealLocalizedUi() {
        assertEquals("ja", platform.readUiLanguage())
        ui.onNodeWithText("ハングルから始める入力と練習").assertExists()
        ui.onNodeWithText("키보드 설정 열기").assertDoesNotExist()
        assertJapaneseChrome()
        capture("onboarding")
        home()
        ui.onNodeWithText("ㅋㅋキーボード").assertExists()
        ui.onNodeWithText("タイピング練習").assertExists()
        ui.onNodeWithText("연습 기록").assertDoesNotExist()
        assertJapaneseChrome()
        capture("home")
    }

    @Test fun settingsFlickAndImmediateLanguageSelectionPersistThroughNativeAdapter() {
        settings()
        ui.onNodeWithText("アプリの表示言語").assertExists()
        ui.onNodeWithContentDescription("ハングル入力配列を選択").performScrollTo().performClick()
        capture("flick-dropdown", popup = true)
        ui.onNodeWithText("ハングルフリック").performClick()
        assertEquals("hangul_flick", platform.readInputLayout().persistedValue)
        ui.onNodeWithText("ハングルフリック ▾").assertExists()
        assertJapaneseChrome()
        capture("settings-flick")
        ui.onNodeWithText("English").performScrollTo().performClick()
        ui.onNodeWithText("App language").assertExists()
        assertEquals("en", platform.readUiLanguage())
        ui.onNodeWithText("日本語").performClick()
        ui.onNodeWithText("アプリの表示言語").assertExists()
        assertEquals("ja", platform.readUiLanguage())
    }

    @Test fun themeNamesAndPhraseControlsAreTranslatedWhilePhraseValuesStayUntouched() {
        settings()
        ui.onNodeWithText("アプリ＋キーボードテーマ").performScrollTo().performClick()
        ui.onNodeWithText("ソウルの昼").performScrollTo().assertExists()
        ui.onNodeWithText("太極").performScrollTo().assertExists()
        assertJapaneseChrome()
        capture("themes")
        ui.onNodeWithContentDescription("戻る").performClick()
        ui.onNodeWithText("定型文管理").performScrollTo().performClick()
        ui.onNodeWithText("こんにちは").assertExists()
        ui.onNodeWithText("新しい定型文").performClick()
        ui.onNodeWithText("タイトル").assertExists()
        ui.onNodeWithText("保存").performClick()
        ui.onNodeWithText("タイトルを入力してください。").assertExists()
        ui.onNodeWithText("제목을 입력하세요.").assertDoesNotExist()
        capture("phrase-editor")
    }

    @Test fun sentenceTriadValuesAndGameResultsKeepTheirMeaningInJapaneseUi() {
        home()
        ui.onNodeWithText("短文練習").performScrollTo().performClick()
        ui.onNodeWithText("スタート").performScrollTo().performClick()
        listOf("韓国語の意味", "ハングル発音", "日本語").forEach { ui.onNodeWithText(it).assertExists() }
        ui.onNodeWithText("한국어 뜻").assertDoesNotExist()
        val texts = visibleText()
        val catalog = PracticeTranslationCatalog(platform.learningItems)
        assertTrue("All three exact learning values must remain intact", platform.learningItems.any { item ->
            val triad = catalog.resolve(item)
            triad.isComplete && listOf(triad.koreanMeaning, triad.hangulPronunciation, triad.japaneseOriginal).all { it in texts }
        })
        capture("sentence")
        ui.onNodeWithText("練習を終える").performScrollTo().performClick()
        ui.onNodeWithText("お疲れさまでした！").assertExists()
        ui.onNodeWithText("もう一度").assertExists()
        capture("results")
        ui.onNodeWithContentDescription("戻る").performClick()
        ui.onNodeWithContentDescription("戻る").performClick()
        ui.onNodeWithText("ハングルカフェ").performScrollTo().performClick()
        ui.onNodeWithText("スタート").performScrollTo().performClick()
        ui.onNodeWithText("注文を入力").assertExists()
        ui.onNodeWithText("注文  1/5").assertExists()
        ui.onNodeWithText("カフェを出る").assertExists()
        capture("cafe")
    }
}
