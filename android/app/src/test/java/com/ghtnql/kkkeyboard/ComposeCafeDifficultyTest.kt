package com.ghtnql.kkkeyboard

import android.content.ContentProvider
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.*
import com.ghtnql.kkkeyboard.sharedui.PracticeMode as SharedPracticeMode
import com.ghtnql.kkkeyboard.sharedui.CafeDifficulty as SharedCafeDifficulty
import java.io.File
import java.text.Normalizer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCafeDifficultyTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()
    private val platform get() = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")

    @Before fun resources() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
    }

    private fun length(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC).count { !it.isWhitespace() }
    private fun capture(name: String) = ui.runOnUiThread {
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/cafe-difficulty/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun everyBundledCafePoolHasNoOverlengthQuestionAndPreservesSentencePractice() {
        val inventory = mutableListOf<String>()
        SharedPracticeMode.entries.forEach { mode ->
            SharedCafeDifficulty.entries.zip(listOf(4, 8, 12)).forEach { (difficulty, cap) ->
                val selected = platform.learningItems.forCafe(mode, difficulty)
                assertTrue("Empty $mode/$difficulty", selected.isNotEmpty())
                selected.forEach { item ->
                    val texts = listOfNotNull(item.sourceText, item.japaneseText,
                        item.japaneseHangulPronunciation, item.koreanText) + item.acceptedAnswers
                    texts.forEach { assertTrue("Overlength ${item.id}: $mode/$difficulty", length(it) <= cap) }
                }
                inventory += "$mode/$difficulty: ${selected.size}, max $cap"
            }
        }
        val sentences = platform.learningItems.forMode(com.ghtnql.kkkeyboard.sharedui.PracticeMode.KOREAN, "sentence")
        assertTrue(sentences.any { length(it.sourceText) > 12 })
        val file = File("build/reports/cafe-difficulty/inventory.txt")
        file.parentFile.mkdirs(); file.writeText(inventory.joinToString("\n"))
    }

    @Test fun actualDifficultyButtonsAreDistinctAndCountsFollowSelection() {
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText("한글카페").performScrollTo().performClick()
        listOf("쉬움", "보통", "어려움").forEach { ui.onNodeWithText(it).assertExists() }
        ui.onAllNodesWithText("보통").assertCountEquals(1)
        com.ghtnql.kkkeyboard.sharedui.PracticeMode.entries.forEach { mode ->
            ui.onNodeWithText(mode.title).performClick()
            SharedCafeDifficulty.entries.zip(listOf("쉬움", "보통", "어려움")).forEach { (difficulty, label) ->
                ui.onNodeWithText(label).performClick().assertIsSelected()
                val count = platform.learningItems.forCafe(mode, difficulty).size
                ui.onNodeWithText("사용 가능한 주문 ${count}개").assertIsDisplayed()
                capture("selection-${mode.name}-${difficulty.name}")
            }
        }
    }

    @Test fun normalStartRejectsLevelOnePoemAndUsesSamePoolAsDisplayedCount() {
        val original = platform
        val safe = original.learningItems.first().copy(
            id = "safe", difficulty = 1, sourceLanguage = "ko", sourceText = "커피",
            acceptedAnswers = listOf("커피"), japaneseText = "コーヒー",
            japaneseHangulPronunciation = "코오히이", koreanText = "커피",
            enabledModes = listOf(com.ghtnql.kkkeyboard.sharedui.PracticeMode.KOREAN.id), gameTypes = listOf("cafe"),
        )
        val poem = safe.copy(id = "poem", sourceText = "가".repeat(24), acceptedAnswers = listOf("가".repeat(24)))
        val fixture = object : AppPlatform by original {
            override val keyboardStatus = KeyboardStatus.ACTIVE
            override val learningItems = listOf(poem, safe)
        }
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply { setContent { KKKeyboardApp(fixture) } })
        }
        ui.onNodeWithText("한글카페").performScrollTo().performClick()
        ui.onNodeWithText("보통").assertIsSelected()
        ui.onNodeWithText("사용 가능한 주문 1개").assertExists()
        ui.mainClock.autoAdvance = false
        ui.onNodeWithText("시작").performClick()
        ui.mainClock.advanceTimeBy(160)
        ui.onAllNodesWithText("커피").onFirst().assertIsDisplayed()
        ui.onNodeWithText(poem.sourceText).assertDoesNotExist()
        capture("normal-short-order")
    }
}
