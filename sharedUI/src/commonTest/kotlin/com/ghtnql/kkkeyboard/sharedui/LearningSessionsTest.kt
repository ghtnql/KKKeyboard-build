package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningSessionsTest {
    private fun item(answer: String = "한글", game: String = "typing") = LearningItem(
        id = "1", category = "test", difficulty = 1, sourceLanguage = "ko", sourceText = answer,
        targetLanguage = "ko", acceptedAnswers = listOf(answer), meaningHint = null,
        enabledModes = listOf(PracticeMode.KOREAN.id), gameTypes = listOf(game),
    )

    @Test fun practiceScoresComboAndAcceptsDecomposedHangul() {
        val session = PracticeSession(PracticeMode.KOREAN, PracticeStyle.BASIC, listOf(item(), item()))
        assertTrue(session.submit(" 한글 "))
        assertTrue(session.submit("한글"))
        val result = session.result(1_000)
        assertEquals(42, result.score)
        assertEquals(100, result.accuracy)
        assertEquals(2, result.maxCombo)
        assertTrue(result.completed)
    }

    @Test fun sentenceAdvancesAfterWrongAnswerWhileBasicRetries() {
        val basic = PracticeSession(PracticeMode.KOREAN, PracticeStyle.BASIC, listOf(item()))
        val sentence = PracticeSession(PracticeMode.KOREAN, PracticeStyle.SENTENCE, listOf(sentenceItem()),
            randomTarget = { SentenceTarget.KOREAN })
        assertFalse(basic.submit("실패"))
        assertFalse(sentence.submit("실패"))
        assertEquals(0, basic.currentIndex)
        assertEquals(1, sentence.currentIndex)
        assertTrue(sentence.result(1_000).completed)
    }

    private fun sentenceItem() = item(answer = "안녕하세요", game = "sentence").copy(
        enabledModes = emptyList(), japaneseText = "こんにちは",
        japaneseHangulPronunciation = "곤니치와", koreanText = "안녕하세요",
    )

    @Test fun sentenceShowsThreeLinesAndAcceptsOnlyChosenCopyText() {
        val item = sentenceItem()
        assertEquals(1, listOf(item).forMode(PracticeMode.JAPANESE, "sentence").size)
        assertEquals(1, listOf(item).forMode(PracticeMode.KOREAN, "sentence").size)
        val japanese = PracticeSession(PracticeMode.KOREAN, PracticeStyle.SENTENCE, listOf(item),
            randomTarget = { SentenceTarget.JAPANESE })
        assertEquals("こんにちは", japanese.currentSentencePrompt?.copyText)
        assertEquals("こんにちは", japanese.expectedAnswer)
        assertFalse(japanese.submit("곤니치와"))
        val korean = PracticeSession(PracticeMode.JAPANESE, PracticeStyle.SENTENCE, listOf(item),
            randomTarget = { SentenceTarget.KOREAN })
        assertEquals("안녕하세요", korean.currentSentencePrompt?.copyText)
        assertTrue(korean.submit("안녕하세요"))
        assertEquals("sentence_mixed", korean.result(1_000).modeId)
    }

    @Test fun sentenceChoosesTargetOncePerItemAndScoresChosenLength() {
        var selections = 0
        val session = PracticeSession(PracticeMode.KOREAN, PracticeStyle.SENTENCE,
            listOf(sentenceItem(), sentenceItem()), randomTarget = {
                if (selections++ == 0) SentenceTarget.JAPANESE else SentenceTarget.KOREAN
            })
        assertEquals(2, selections)
        assertEquals("こんにちは", session.expectedAnswer)
        assertTrue(session.submit("こんにちは"))
        assertEquals("안녕하세요", session.expectedAnswer)
        assertTrue(session.submit("안녕하세요"))
        assertEquals(10, session.result(1_000).totalExpectedCharacters)
        assertEquals(2, selections)
    }

    @Test fun incompleteSentenceCannotEnterThreeLinePractice() {
        assertTrue(listOf(item(answer = "원문", game = "sentence")).forMode(PracticeMode.KOREAN, "sentence").isEmpty())
    }

    @Test fun rainHitAndMissUpdateLivesAndScore() {
        val rain = RainSession(PracticeMode.KOREAN, listOf(item(game = "rain")), randomX = { 0.5f })
        rain.tick(100)
        assertEquals(1, rain.targets.size)
        assertTrue(rain.submit("한글"))
        assertEquals(20, rain.score)
        rain.tick(100)
        repeat(50) { rain.tick(250) }
        assertTrue(rain.lives < 3)
    }

    @Test fun cafeOrderTimesOutAndAwardsRemainingTimeBonus() {
        val cafe = CafeSession(PracticeMode.KOREAN, listOf(item(game = "cafe")), CafeDifficulty.NORMAL, orderCount = 2)
        assertTrue(cafe.submit("한글"))
        assertEquals(33, cafe.score)
        assertTrue(cafe.tick(20_000))
        assertTrue(cafe.finished)
        assertEquals(1, cafe.successes)
        assertEquals(1, cafe.errorCount)
    }

    @Test fun progressRecordsRainHighScoreAndXp() {
        val result = resultOf("rain_ko_same_hangul", 2, 2, 0, 1_000, 1, 30, true)
        val progress = LearningProgress().record(result)
        assertEquals(30, progress.rainHighScore)
        assertEquals(3, progress.totalXp)
        assertEquals(1, progress.totalSessions)
    }

    private fun japaneseItem(game: String) = LearningItem(
        id = "ja1", category = "test", difficulty = 1, sourceLanguage = "ja", sourceText = "ありがとう",
        targetLanguage = "ja", acceptedAnswers = listOf("아리가토", "아리가또"), meaningHint = "고마워요",
        enabledModes = listOf(PracticeMode.JAPANESE.id), gameTypes = listOf(game),
    )

    @Test fun basicAcceptsJapaneseSourceAndHangulAliasesButRejectsHintAndWrong() {
        assertTrue(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.BASIC, listOf(japaneseItem("typing"))).submit("ありがとう"))
        assertTrue(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.BASIC, listOf(japaneseItem("typing"))).submit("아리가토"))
        assertTrue(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.BASIC, listOf(japaneseItem("typing"))).submit("아리가또"))
        assertFalse(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.BASIC, listOf(japaneseItem("typing"))).submit("고마워요"))
        assertFalse(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.BASIC, listOf(japaneseItem("typing"))).submit("오답"))
    }

    @Test fun rainAcceptsJapaneseSourceAndHangulAlias() {
        val rainJa = RainSession(PracticeMode.JAPANESE, listOf(japaneseItem("rain")), randomX = { 0.5f })
        rainJa.tick(100)
        assertTrue(rainJa.submit("ありがとう"))
        val rainHangul = RainSession(PracticeMode.JAPANESE, listOf(japaneseItem("rain")), randomX = { 0.5f })
        rainHangul.tick(100)
        assertTrue(rainHangul.submit("아리가토"))
        val rainWrong = RainSession(PracticeMode.JAPANESE, listOf(japaneseItem("rain")), randomX = { 0.5f })
        rainWrong.tick(100)
        assertFalse(rainWrong.submit("고마워요"))
        assertFalse(rainWrong.submit("오답"))
    }

    @Test fun cafeAcceptsJapaneseSourceAndHangulAlias() {
        assertTrue(CafeSession(PracticeMode.JAPANESE, listOf(japaneseItem("cafe")), CafeDifficulty.NORMAL, orderCount = 1).submit("ありがとう"))
        assertTrue(CafeSession(PracticeMode.JAPANESE, listOf(japaneseItem("cafe")), CafeDifficulty.NORMAL, orderCount = 1).submit("아리가또"))
        assertFalse(CafeSession(PracticeMode.JAPANESE, listOf(japaneseItem("cafe")), CafeDifficulty.NORMAL, orderCount = 1).submit("고마워요"))
    }

    @Test fun koreanSourceTextIsNotAutomaticallyAccepted() {
        val ko = item().copy(sourceText = "표시문구", acceptedAnswers = listOf("한글"))
        assertFalse(PracticeSession(PracticeMode.KOREAN, PracticeStyle.BASIC, listOf(ko)).submit("표시문구"))
        assertTrue(PracticeSession(PracticeMode.KOREAN, PracticeStyle.BASIC, listOf(ko)).submit("한글"))
    }

    @Test fun convertPracticeAcceptsBundledJapaneseAndHangulAnswers() {
        assertTrue(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.CONVERT, listOf(japaneseItem("convert"))).submit("ありがとう"))
        assertTrue(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.CONVERT, listOf(japaneseItem("convert"))).submit("아리가토"))
    }

    @Test fun sentenceStillRequiresExactCopyText() {
        val sentenceJa = PracticeSession(PracticeMode.JAPANESE, PracticeStyle.SENTENCE, listOf(sentenceItem(), sentenceItem()),
            randomTarget = { SentenceTarget.JAPANESE })
        assertFalse(sentenceJa.submit("곤니치와"))
        assertTrue(sentenceJa.submit("こんにちは"))
    }
}
