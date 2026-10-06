package com.ghtnql.kkkeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import android.content.ContentProvider
import android.content.pm.ProviderInfo
import android.graphics.Insets
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ghtnql.kkkeyboard.sharedui.forMode
import com.ghtnql.kkkeyboard.sharedui.hasSentenceTriad
import com.ghtnql.kkkeyboard.sharedui.PracticeMode
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the actual launcher and shared Compose input action, not just session models. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeGameInputTest {
    @get:Rule val ui = createAndroidComposeRule<ComposeMainActivity>()

    @Before fun initializeComposeResources() {
        // Robolectric does not launch manifest ContentProviders like a real Android process.
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as ContentProvider
        provider.attachInfo(ui.activity, ProviderInfo().apply {
            authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
        })
    }

    private fun openGame(name: String) {
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText(name).performScrollTo().performClick()
        ui.onNodeWithText(PracticeMode.JAPANESE.title).performClick()
        ui.mainClock.autoAdvance = false
        ui.onNodeWithText("시작").performClick()
        ui.mainClock.advanceTimeBy(160)
        assertTriadVisible()
    }

    private fun assertTriadVisible() {
        listOf("한국어 뜻", "한글 발음", "일본어").forEach { label ->
            ui.onAllNodesWithText(label).onFirst().assertIsDisplayed()
        }
    }

    @Test fun everyPlayableBundledItemHasAllThreeDisplayLines() {
        val platform = org.robolectric.util.ReflectionHelpers.getField<com.ghtnql.kkkeyboard.sharedui.AppPlatform>(ui.activity, "platform")
        val catalogue = com.ghtnql.kkkeyboard.sharedui.PracticeTranslationCatalog(platform.learningItems)
        val playable = platform.learningItems.filter { item ->
            item.gameTypes.any { it != "sentence" } || item.hasSentenceTriad
        }
        org.junit.Assert.assertEquals(564, playable.size)
        playable.forEach { item ->
            org.junit.Assert.assertTrue("Incomplete translations for ${item.id}", catalogue.resolve(item).isComplete)
        }
    }

    private fun openPractice(name: String, mode: PracticeMode = PracticeMode.JAPANESE) {
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText(if (name == "기본 연습") "단어 연습" else name).performScrollTo().performClick()
        if (name != "단문 연습") ui.onNodeWithText(mode.title).performClick()
        ui.onNodeWithText("시작").performClick()
        assertTriadVisible()
        ui.onNode(hasSetTextAction()).assertExists()
        snapshot("trilingual-${name}-${mode.name}")
    }

    @Test fun sentenceSelectionIncludesEveryImportedSentenceAndContinuesAfterFirst() {
        val platform = org.robolectric.util.ReflectionHelpers.getField<com.ghtnql.kkkeyboard.sharedui.AppPlatform>(ui.activity, "platform")
        val sourceSentences = platform.learningItems.filter { "sentence" in it.gameTypes }
        org.junit.Assert.assertEquals(211, sourceSentences.size)
        PracticeMode.entries.forEach { mode ->
            val actual = platform.learningItems.forMode(mode, "sentence")
            org.junit.Assert.assertEquals(sourceSentences.map { it.id }.toSet(), actual.map { it.id }.toSet())
        }
        sourceSentences.forEach { org.junit.Assert.assertTrue("Sentence excluded: ${it.id}", it.hasSentenceTriad) }
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText("단문 연습").performScrollTo().performClick()
        ui.onNodeWithText("사용 가능한 문장 211개").assertIsDisplayed()
        snapshot("sentence-selection-211")
        ui.onNodeWithText("시작").performClick()
        ui.onNode(hasText("1 / 10", substring = true)).assertExists()
        ui.onNode(hasSetTextAction()).performTextInput("테스트 오답")
        ui.onNodeWithText("확인").performClick()
        ui.onNode(hasText("2 / 10", substring = true)).assertExists()
        assertTriadVisible()
        snapshot("sentence-restored-second-of-ten")
    }

    @Test fun japaneseBasicShowsAllThreeLines() = openPractice("기본 연습")
    @Test fun koreanBasicAlsoShowsJapaneseAndItsHangulPronunciation() = openPractice("기본 연습", PracticeMode.KOREAN)
    @Test fun convertShowsAllThreeLines() = openPractice("변환 연습")
    @Test fun sentenceShowsAllThreeLines() = openPractice("단문 연습")


    private class AdProbe {
        var calls = 0
        var prepared = 0
        var accepted = false
        var throws = false
        var savedSessions = 0
        var savedBeforeAd = false
        var finished: (() -> Unit)? = null
    }

    private fun installAdProbe(probe: AdProbe) {
        val delegate = org.robolectric.util.ReflectionHelpers.getField<com.ghtnql.kkkeyboard.sharedui.AppPlatform>(ui.activity, "platform")
        val fixture = delegate.learningItems.first().copy(
            sourceLanguage = "ko", sourceText = "테스트", acceptedAnswers = listOf("테스트"),
            gameTypes = listOf("typing", "sentence", "convert", "rain", "cafe"),
            enabledModes = com.ghtnql.kkkeyboard.sharedui.PracticeMode.entries.map { it.id },
            japaneseText = "テスト", japaneseHangulPronunciation = "테스토", koreanText = "테스트",
        )
        val platform = object : com.ghtnql.kkkeyboard.sharedui.AppPlatform by delegate {
            override val keyboardStatus = com.ghtnql.kkkeyboard.sharedui.KeyboardStatus.ACTIVE
            override val learningItems = listOf(fixture)
            override val learningProgress = com.ghtnql.kkkeyboard.sharedui.LearningProgress()
            override fun saveLearningProgress(progress: com.ghtnql.kkkeyboard.sharedui.LearningProgress) {
                probe.savedSessions = progress.totalSessions
            }
            override fun prepareGameEndAd() { probe.prepared++ }
            override fun requestGameEndAd(onFinished: () -> Unit): Boolean {
                probe.calls++
                probe.savedBeforeAd = probe.savedSessions == 1
                if (probe.throws) error("Presentation failed")
                if (probe.accepted) probe.finished = onFinished
                return probe.accepted
            }
        }
        ui.runOnUiThread {
            ui.activity.setContentView(androidx.compose.ui.platform.ComposeView(ui.activity).apply {
                setContent { com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp(platform) }
            })
        }
    }

    private fun startProbedPractice(name: String) {
        ui.onNodeWithText(if (name == "기본 연습") "단어 연습" else name).performScrollTo().performClick()
        ui.onNodeWithText("시작").performClick()
    }

    private fun submitProbedAnswer() {
        val field = ui.onNode(hasSetTextAction())
        field.performTextInput("테스트")
        field.performImeAction()
        ui.mainClock.advanceTimeBy(100)
        ui.waitForIdle()
    }

    @Test fun naturalPracticeSavesBeforeAdAndContinuesOnceOnDismissal() {
        val probe = AdProbe().apply { accepted = true }
        installAdProbe(probe)
        startProbedPractice("기본 연습")
        submitProbedAnswer()
        org.junit.Assert.assertEquals(1, probe.prepared)
        org.junit.Assert.assertEquals(1, probe.calls)
        org.junit.Assert.assertTrue(probe.savedBeforeAd)
        ui.onNodeWithText("연습을 마쳤어요.").assertExists()
        ui.onNodeWithText("다시 하기").assertDoesNotExist()
        ui.runOnUiThread { requireNotNull(probe.finished).invoke() }
        ui.onNodeWithText("다시 하기").assertExists()
        snapshot("game-end-result-after-ad")
        ui.onNodeWithText("다시 하기").assertExists().performClick()
        ui.runOnUiThread { requireNotNull(probe.finished).invoke() }
        ui.onNodeWithText("시작").assertExists()
        org.junit.Assert.assertEquals(1, probe.savedSessions)
    }

    @Test fun everyPracticeStyleFinishesThroughAdHookAndMissingAdDoesNotWait() {
        val probe = AdProbe()
        installAdProbe(probe)
        listOf("기본 연습", "단문 연습", "변환 연습").forEachIndexed { index, name ->
            if (index > 0) ui.onNodeWithContentDescription("뒤로").performClick()
            startProbedPractice(name)
            submitProbedAnswer()
            ui.onNodeWithText("다시 하기").assertExists()
            org.junit.Assert.assertEquals(index + 1, probe.calls)
            ui.onNodeWithContentDescription("뒤로").performClick()
        }
    }

    @Test fun cafeNaturalCompletionUsesAdHookOnlyOnce() {
        val probe = AdProbe()
        installAdProbe(probe)
        startProbedPractice("한글카페")
        repeat(5) { submitProbedAnswer() }
        ui.onNodeWithText("다시 하기").assertExists()
        org.junit.Assert.assertEquals(1, probe.calls)
        org.junit.Assert.assertEquals(1, probe.savedSessions)
    }

    @Test fun rainNaturalGameOverUsesAdHookOnlyOnce() {
        val probe = AdProbe()
        installAdProbe(probe)
        startProbedPractice("한글비")
        ui.mainClock.advanceTimeBy(65_000)
        ui.onNodeWithText("다시 하기").assertExists()
        org.junit.Assert.assertEquals(1, probe.calls)
        org.junit.Assert.assertEquals(1, probe.savedSessions)
    }

    @Test fun allEarlyFinishButtonsAndBackSkipAdHook() {
        val probe = AdProbe()
        installAdProbe(probe)
        listOf("기본 연습" to "연습 끝내기", "단문 연습" to "연습 끝내기",
            "변환 연습" to "연습 끝내기", "한글비" to "게임 끝내기", "한글카페" to "카페 나가기")
            .forEachIndexed { index, (name, finishLabel) ->
                if (index > 0) ui.onNodeWithContentDescription("뒤로").performClick()
                startProbedPractice(name)
                ui.onNodeWithText(finishLabel).performClick()
                ui.onNodeWithText("다시 하기").assertExists()
                ui.onNodeWithContentDescription("뒤로").performClick()
            }
        org.junit.Assert.assertEquals(0, probe.calls)
        ui.onNodeWithContentDescription("뒤로").performClick()
        startProbedPractice("기본 연습")
        ui.onNodeWithContentDescription("뒤로").performClick()
        org.junit.Assert.assertEquals(0, probe.calls)
    }

    @Test fun presentationExceptionStillShowsSavedResult() {
        val probe = AdProbe().apply { throws = true }
        installAdProbe(probe)
        startProbedPractice("기본 연습")
        submitProbedAnswer()
        ui.onNodeWithText("다시 하기").assertExists()
        org.junit.Assert.assertTrue(probe.savedBeforeAd)
        org.junit.Assert.assertEquals(1, probe.savedSessions)
    }

    @Test fun backDuringAdTransitionIgnoresLateDismissal() {
        val probe = AdProbe().apply { accepted = true }
        installAdProbe(probe)
        startProbedPractice("기본 연습")
        submitProbedAnswer()
        val oldFinished = requireNotNull(probe.finished)
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.runOnUiThread { oldFinished.invoke() }
        ui.onNodeWithText("시작").assertExists()
        ui.onNodeWithText("다시 하기").assertDoesNotExist()
        ui.onNodeWithText("시작").performClick()
        submitProbedAnswer()
        ui.runOnUiThread { oldFinished.invoke() }
        ui.onNodeWithText("연습을 마쳤어요.").assertExists()
        ui.runOnUiThread { requireNotNull(probe.finished).invoke() }
        ui.onNodeWithText("다시 하기").assertExists()
    }

    private fun snapshot(name: String) {
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/game-input/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun rainImeActionAcceptsJapaneseAndKeepsAnswerFocused() {
        openGame("한글비")
        val prompt = LearningContent.forMode(LearningContent.load(ui.activity), com.ghtnql.kkkeyboard.PracticeMode.JAPANESE_TO_HANGUL, "rain")
            .map { it.sourceText }.first {
            ui.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
        }
        val field = ui.onNode(hasSetTextAction())
        field.performClick().performTextInput(prompt)
        ui.mainClock.advanceTimeByFrame()
        field.performImeAction()
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("적중!").assertExists()
        field.assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        snapshot("rain-after-send")
        // A second empty Send must not turn the successful submission into a miss.
        field.performImeAction()
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("적중!").assertExists()
    }

    @Test fun cafeImeActionAcceptsJapaneseAndKeepsAnswerFocused() {
        openGame("한글카페")
        val answers = LearningContent.forMode(LearningContent.load(ui.activity), com.ghtnql.kkkeyboard.PracticeMode.JAPANESE_TO_HANGUL, "cafe")
            .map { it.sourceText }
        val prompt = requireNotNull(answers.firstOrNull { ui.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }) {
            ui.onRoot().printToString()
        }
        val field = ui.onNode(hasSetTextAction())
        field.performClick().performTextInput(prompt)
        ui.mainClock.advanceTimeByFrame()
        field.performImeAction()
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("주문 성공!").assertExists()
        field.assertIsFocused()
        snapshot("cafe-after-send")
    }

    @Test fun cafeHeaderAndAnswerStayVisibleWhenKeyboardOpens() {
        openGame("한글카페")
        ui.runOnUiThread {
            val decor = ui.activity.window.decorView
            val imeHeight = (420 * ui.activity.resources.displayMetrics.density).toInt()
            decor.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, imeHeight))
                .setVisible(WindowInsets.Type.ime(), true)
                .build())
        }
        ui.mainClock.advanceTimeByFrame()
        ui.onAllNodesWithText("한글카페").onLast().assertIsDisplayed()
        ui.onNode(hasSetTextAction()).assertIsDisplayed()
        snapshot("cafe-keyboard-open")
    }

    @Test fun rainHudAndFallingWordStayVisibleWhenKeyboardOpens() {
        openGame("한글비")
        val title = ui.onAllNodesWithText("한글비").onFirst()
        val topBefore = title.fetchSemanticsNode().boundsInRoot.top
        val prompt = LearningContent.forMode(LearningContent.load(ui.activity),
            com.ghtnql.kkkeyboard.PracticeMode.JAPANESE_TO_HANGUL, "rain")
            .map { it.sourceText }.first { ui.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        snapshot("rain-before-keyboard")
        ui.runOnUiThread {
            val imeHeight = (420 * ui.activity.resources.displayMetrics.density).toInt()
            ui.activity.window.decorView.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, imeHeight))
                .setVisible(WindowInsets.Type.ime(), true).build())
        }
        val field = ui.onNode(hasSetTextAction())
        field.performClick()
        ui.mainClock.advanceTimeByFrame()
        title.assertIsDisplayed()
        org.junit.Assert.assertEquals(topBefore, title.fetchSemanticsNode().boundsInRoot.top, 1f)
        ui.onNodeWithText(prompt).assertIsDisplayed()
        ui.onNode(hasText("점수", substring = true)).assertIsDisplayed()
        field.assertIsDisplayed().assertIsFocused()
        snapshot("rain-keyboard-open")
    }

    @Test fun convertDraftsAreAvailableAsAPlayablePractice() {
        ui.onNodeWithText("연습 시작").performClick()
        ui.onNodeWithText("변환 연습").performScrollTo().performClick()
        ui.onNodeWithText("사용 가능한 문장 22개").assertExists()
        ui.onNodeWithText("시작").performClick()
        ui.onNodeWithText("변환 연습").assertExists()
        ui.onNode(hasSetTextAction()).assertExists()
    }
}
