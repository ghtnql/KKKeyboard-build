package com.ghtnql.kkkeyboard

import android.content.ContentProvider
import android.content.Context
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.*
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
class ComposeJamoLearningTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()
    private val platform get() = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
    private val catalog get() = JamoWordCatalog(platform.learningItems)

    private fun questionWord(): JamoWord {
        val japanese = ui.onNodeWithTag("jamo-word-question").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        return catalog.words.single { word -> word.japanese == japanese &&
            ui.onAllNodesWithTag("jamo-word-choice-${word.sourceId}").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun chooseCorrect(): JamoWord {
        val word = questionWord()
        ui.onNodeWithTag("jamo-word-choice-${word.sourceId}").performScrollTo().performClick()
        return word
    }
    private fun assertHiddenStudyCard() {
        ui.onNodeWithTag("jamo-reading").assertDoesNotExist()
        ui.onNodeWithTag("jamo-word-reveal").assertDoesNotExist()
        ui.onNodeWithText("기역").assertDoesNotExist()
        ui.onNodeWithText("ㄱ").assertDoesNotExist()
        val word = questionWord()
        ui.onAllNodesWithText(word.hangul).assertCountEquals(1) // The choice only; no answer cue above it.
        val choices = catalog.words.filter { ui.onAllNodesWithTag("jamo-word-choice-${it.sourceId}")
            .fetchSemanticsNodes().isNotEmpty() }
        assertEquals(4, choices.size)
        assertEquals(4, choices.map { it.hangul }.toSet().size)
    }

    @Before fun setup() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
        ui.activity.getSharedPreferences("hangul_learning", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openLearning() {
        ui.onNodeWithText("연습 시작").performClick()
        capture("home")
        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.onAllNodesWithTag("hiragana-guide-close").fetchSemanticsNodes().firstOrNull()?.let {
            ui.onNodeWithTag("hiragana-guide-close").performClick()
        }
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/jamo-learning/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun quizOnlyMarksCorrectAnswersAndNextActuallyOpensNextLetter() {
        openLearning()
        ui.onNodeWithText("24개 중 0개 학습").assertExists()
        capture("consonants")
        ui.onNodeWithText("ㄱ").performClick()
        ui.onNodeWithText("기역").assertExists()
        ui.onNodeWithText("牛乳 → 규우뉴우 (우유)").assertExists()
        ui.onNodeWithTag("jamo-reading").assertExists()
        capture("letter-card")
        assertEquals(emptyList<String>(), platform.readHangulLearnedIds())
        ui.onNodeWithText("확인 문제").performScrollTo().performClick()
        assertHiddenStudyCard()
        val first = questionWord()
        assertEquals(JamoWordKind.JAPANESE_PRONUNCIATION, first.kind)
        assertEquals(false, catalog.examples(basicConsonants.first()).any { it.sourceId == first.sourceId })
        val wrong = catalog.words.first { it.sourceId != first.sourceId &&
            ui.onAllNodesWithTag("jamo-word-choice-${it.sourceId}").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("jamo-word-choice-${wrong.sourceId}").performScrollTo().performClick()
        ui.onNodeWithText("다시 골라 보세요").assertExists()
        assertEquals(emptyList<String>(), platform.readHangulLearnedIds())
        assertHiddenStudyCard()
        capture("quiz")
        chooseCorrect()
        ui.onNodeWithText("정답!").assertExists()
        ui.onNodeWithTag("jamo-word-reveal").assertExists()
        assertEquals(listOf("c_ㄱ"), platform.readHangulLearnedIds())
        ui.onNodeWithText("다시 풀기").performScrollTo().performClick()
        assertHiddenStudyCard()
        assertEquals(false, first.sourceId == questionWord().sourceId)
        chooseCorrect()
        assertEquals(listOf("c_ㄱ"), platform.readHangulLearnedIds())
        ui.onNodeWithText("다음 글자").performScrollTo().performClick()
        ui.onNodeWithText("니은").assertExists()
        ui.onNodeWithText("ㄴ").assertExists()
        ui.onAllNodesWithText("닫기").onFirst().performScrollTo().performClick()
        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.onNodeWithText("24개 중 1개 학습").assertExists()
        ui.onNodeWithText("ㄱ ✓").assertExists()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.onNodeWithText("24개 중 1개 학습").assertExists()
        assertEquals(listOf("c_ㄱ"), platform.readHangulLearnedIds())
        val completed = ui.onNodeWithText("ㄱ ✓").fetchSemanticsNode().boundsInRoot
        val uncompleted = ui.onNodeWithText("ㄴ").fetchSemanticsNode().boundsInRoot
        assertEquals("Completion mark must not enlarge a grid row", uncompleted.height, completed.height, 0.1f)
    }

    @Test fun vowelsAndCombinationWorkAndVisibleBackReturnsHome() {
        openLearning()
        ui.onNodeWithText("모음").performClick()
        capture("vowels")
        ui.onNodeWithText("ㅏ").performClick()
        ui.onNodeWithTag("jamo-reading").assertExists()
        ui.onNodeWithTag("jamo-sound-hangul").assertExists()
        ui.onNodeWithText("글자 만들기").performScrollTo().performClick()
        ui.onNodeWithText("ㄱ + ㅏ = 가").assertExists()
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("カ", substring = true)
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("か", substring = true)
        ui.onNodeWithText("ㄴ").performScrollTo().performClick()
        ui.onNodeWithText("ㅓ").performScrollTo().performClick()
        ui.onNodeWithText("ㄴ + ㅓ = 너").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("ノ", substring = true)
        ui.onNodeWithTag("jamo-compose-pronunciation").assertTextContains("の", substring = true)
        capture("combination")
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithText("한글학습").assertExists()
        ui.onNodeWithText("한글카페").assertExists()
    }

    @Test fun basicLettersRemainAccessibleWithoutWordPracticeContent() {
        val emptyPlatform = object : AppPlatform by platform {
            override val learningItems: List<com.ghtnql.kkkeyboard.sharedui.LearningItem> = emptyList()
        }
        ui.runOnUiThread {
            ui.activity.setContentView(ComposeView(ui.activity).apply { setContent { KKKeyboardApp(emptyPlatform) } })
        }
        openLearning()
        ui.onNodeWithText("ㄱ").assertExists()
        ui.onNodeWithText("ㅎ").assertExists()
        ui.onNodeWithText("ㄱ").performClick()
        ui.onNodeWithTag("jamo-reading").assertExists()
        ui.onNodeWithText("확인 문제").performScrollTo().performClick()
        ui.onNodeWithTag("jamo-word-question").assertDoesNotExist()
        assertEquals(emptyList<String>(), platform.readHangulLearnedIds())
    }

    @Test fun standaloneQuizUsesRealWordsAndNextQuestionDoesNotRepeatOrMarkUnrelatedLetters() {
        openLearning()
        ui.onNodeWithText("단어 퀴즈").performClick()
        assertHiddenStudyCard()
        val first = chooseCorrect()
        ui.onNodeWithTag("jamo-word-reveal").assertExists()
        capture("word-quiz-answer")
        assertEquals(emptyList<String>(), platform.readHangulLearnedIds())
        ui.onNodeWithText("다음 문제").performScrollTo().performClick()
        assertHiddenStudyCard()
        assertEquals(false, first.sourceId == questionWord().sourceId)
        capture("word-quiz")
    }

    @Test fun koreanFallbackForEoAndYeoUsesRealGameAnswersAndClearTaskLabels() {
        openLearning()
        ui.onNodeWithText("모음").performClick()
        listOf("ㅓ", "ㅕ").forEach { glyph ->
            ui.onNodeWithText(glyph).performScrollTo().performClick()
            ui.onNodeWithTag("jamo-reading").assertExists()
            ui.onNodeWithText("확인 문제").performScrollTo().performClick()
            assertHiddenStudyCard()
            val word = questionWord()
            assertEquals(JamoWordKind.KOREAN_MEANING, word.kind)
            assertEquals(true, startsWithJamo(word.hangul, basicVowels.single { it.glyph == glyph }))
            ui.onNodeWithText("한국어 단어: 일본어에 대한 한국어 답을 고르세요").assertExists()
            chooseCorrect()
            ui.onNodeWithText("학습 목록").performScrollTo().performClick()
        }
        assertEquals(setOf("v_ㅓ", "v_ㅕ"), platform.readHangulLearnedIds().toSet())
    }

    @Test fun everyQuestionComesFromBundledRainCafeAndAllBasicLettersHaveUsableQuestions() {
        val source = platform.learningItems.associateBy { it.id }
        catalog.words.forEach { word ->
            val item = source.getValue(word.sourceId)
            assertEquals(true, item.gameTypes.any { it == "rain" || it == "cafe" })
            assertEquals(true, item.acceptedAnswers.contains(word.hangul))
        }
        val allLetters = basicConsonants + basicVowels
        val allExamples = allLetters.flatMap { letter ->
            val examples = catalog.examples(letter)
            assertEquals("Two examples for ${letter.glyph}: $examples", 2, examples.size)
            assertEquals(examples.take(1), catalog.examples(letter, 1))
            assertEquals(true, examples.all { com.ghtnql.kkkeyboard.sharedui.startsWithJamo(it.hangul, letter) })
            examples
        }
        val norms = allExamples.map { com.ghtnql.kkkeyboard.sharedcore.normalizeAnswer(it.hangul) }
        assertEquals(norms.size, norms.toSet().size)
        assertEquals(listOf("ㄱ"), allLetters.filter { letter -> catalog.examples(letter).any { it.japanese == "牛乳" } }.map { it.glyph })
        assertEquals(false, catalog.words.any { it.japanese.contains("今日も頑張ろう") })
        allLetters.forEach { letter ->
            repeat(100) { seed ->
                val q = catalog.question(letter, random = kotlin.random.Random(seed))
                org.junit.Assert.assertNotNull("No usable game question for ${letter.glyph}, seed $seed", q)
                assertEquals(true, startsWithJamo(q!!.word.hangul, letter))
                assertEquals(4, q.options.map { it.hangul }.toSet().size)
                val lengths = q.options.map { w -> w.hangul.count { !it.isWhitespace() } }
                assertEquals("Balanced choices: ${q.options.map { it.hangul }}", 1, lengths.toSet().size)
                assertEquals(true, lengths.all { it in 1..5 })
                assertEquals("Only the correct option may start with ${letter.glyph}: ${q.options.map { it.hangul }}",
                    1, q.options.count { startsWithJamo(it.hangul, letter) })
            }
        }
        val milk = catalog.words.single { it.japanese == "牛乳" && it.kind == JamoWordKind.JAPANESE_PRONUNCIATION }
        assertEquals("규우뉴우", milk.hangul)
        assertEquals("우유", milk.koreanMeaning)
    }
    @Test fun chartShowsOnceCanReopenAndPersistsIndependentlyOfLearning() {
        assertEquals(false, platform.readHiraganaGuideSeen())
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.onNodeWithTag("hiragana-guide").assertExists()
        ui.onNodeWithText("あ").assertExists()
        ui.onNodeWithText("ら").performScrollTo().assertIsDisplayed()
        capture("hiragana-chart")
        ui.onNodeWithTag("hiragana-guide-close").performClick()
        assertEquals(true, platform.readHiraganaGuideSeen())
        assertEquals(emptyList<String>(), platform.readHangulLearnedIds())
        ui.onNodeWithTag("hiragana-help").performClick()
        ui.onNodeWithTag("hiragana-guide").assertExists()
        ui.onNodeWithTag("hiragana-guide-close").performClick()
        ui.onNodeWithText("닫기").performClick()
        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.onNodeWithTag("hiragana-guide").assertDoesNotExist()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.onNodeWithTag("hiragana-guide").assertDoesNotExist()
        assertEquals(true, platform.readHiraganaGuideSeen())
        ui.onNodeWithTag("hiragana-help").performClick()
        ui.onNodeWithTag("hiragana-guide").assertExists()
        ui.onNodeWithTag("hiragana-guide-close").performClick()
    }

    @Test fun consonantGuidesShowFullHiraganaSoundsAndNasalDistinctions() {
        openLearning()
        ui.onNodeWithText("ㄱ").performClick()
        ui.onNodeWithTag("jamo-reading").assertTextContains("か", substring = true)
        ui.onNodeWithTag("jamo-reading").assertTextContains("こ", substring = true)
        ui.onNodeWithTag("jamo-sound-hangul").assertTextContains("가", substring = true)
        ui.onNodeWithTag("jamo-sound-hangul").assertTextContains("고", substring = true)
        capture("full-kana-card")
        listOf("ㄴ", "ㅁ", "ㅇ").forEach { glyph ->
            ui.onNodeWithText("학습 목록").performScrollTo().performClick()
            ui.onNodeWithText(glyph).performScrollTo().performClick()
            ui.onNodeWithTag("jamo-sound-note").assertTextContains("ん", substring = true)
        }
    }

}
