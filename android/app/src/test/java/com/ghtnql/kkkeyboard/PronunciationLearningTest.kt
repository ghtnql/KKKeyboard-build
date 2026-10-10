package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationLearningTest {
    private val japaneseMode = PracticeMode.JAPANESE_TO_HANGUL
    private val variants = listOf("도우쿄우", "토우교우", "토쿄", "도교")

    @Test fun basicPracticeAcceptsBothOnsetPairsAndLongVowelOmission() {
        variants.forEach { input ->
            val session = PracticeSession(japaneseMode, listOf(item()), 0L)
            val attempt = session.submit(input)
            assertTrue(input, attempt.correct)
            assertTrue(input, attempt.completed)
            assertEquals(input, 0, session.errorCount)
        }
    }

    @Test fun rainAcceptsBothOnsetPairsAndLongVowelOmission() {
        variants.forEach { input ->
            val game = RainGame(japaneseMode, listOf(item()), 0L, randomFloat = { 0.5f })
            game.tick(0L)
            val submission = game.submit(input)
            assertTrue(input, submission.correct)
            assertEquals(input, 1L, submission.removedTargetId)
            assertTrue(input, game.targets.isEmpty())
            assertEquals(input, 0, game.errorCount)
        }
    }

    @Test fun cafeAcceptsBothOnsetPairsAndLongVowelOmission() {
        variants.forEach { input ->
            val game = CafeGame(japaneseMode, listOf(item()), 0L, orderCount = 1)
            val submission = game.submit(input)
            assertTrue(input, submission.correct)
            assertTrue(input, submission.completed)
            assertEquals(input, 1, game.successfulOrderCount)
            assertEquals(input, 0, game.errorCount)
        }
    }

    @Test fun koreanModeAndKoreanSourceRejectLeniencyInEveryGame() {
        for ((mode, content) in listOf(
            PracticeMode.KOREAN_TYPING to item(),
            japaneseMode to item(sourceLanguage = "ko"),
            PracticeMode.KOREAN_TYPING to item(sourceLanguage = "ko"),
        )) {
            variants.forEach { input ->
                assertFalse("practice $mode/$input", PracticeSession(mode, listOf(content), 0L).submit(input).correct)
                val rain = RainGame(mode, listOf(content), 0L, randomFloat = { 0.5f })
                rain.tick(0L)
                assertFalse("rain $mode/$input", rain.submit(input).correct)
                assertFalse("cafe $mode/$input", CafeGame(mode, listOf(content), 0L, orderCount = 1).submit(input).correct)
            }
        }
    }

    @Test fun shortSentenceRetainsExactMatchingForJapaneseSource() {
        variants.forEach { input ->
            val session = PracticeSession(japaneseMode, listOf(item()), 0L, PracticeStyle.SHORT_SENTENCE)
            assertFalse(input, session.submit(input).correct)
        }
        val exact = PracticeSession(japaneseMode, listOf(item()), 0L, PracticeStyle.SHORT_SENTENCE)
        assertTrue(exact.submit("토우쿄우").correct)
    }

    @Test fun internalSpacesStaySignificantInEveryGame() {
        val content = item(answer = "토우쿄우 카메라")
        val incorrect = "도교가메라"
        assertFalse(PracticeSession(japaneseMode, listOf(content), 0L).submit(incorrect).correct)
        val rain = RainGame(japaneseMode, listOf(content), 0L, randomFloat = { 0.5f })
        rain.tick(0L)
        assertFalse(rain.submit(incorrect).correct)
        assertFalse(CafeGame(japaneseMode, listOf(content), 0L, orderCount = 1).submit(incorrect).correct)
        assertTrue(PracticeSession(japaneseMode, listOf(content), 0L).submit(" 도교 가메라 ").correct)
    }

    @Test fun learningPrefixAcceptsShortenedReadingsOnlyForJapanesePronunciation() {
        val content = item(answer = "토우쿄우카메라")
        assertTrue(LearningContent.matchesPrefix(content, japaneseMode, "도교가"))
        assertFalse(LearningContent.matchesPrefix(content, PracticeMode.KOREAN_TYPING, "도교가"))
        assertFalse(LearningContent.matchesPrefix(content.copy(sourceLanguage = "ko"), japaneseMode, "도교가"))
        assertFalse(LearningContent.matchesPrefix(content, japaneseMode, "도 교가"))
    }

    private fun item(sourceLanguage: String = "ja", answer: String = "토우쿄우") = LearningItem(
        id = "pronunciation_fixture", category = "word", difficulty = 1,
        sourceLanguage = sourceLanguage, sourceText = "東京", targetLanguage = "ko",
        acceptedAnswers = listOf(answer), meaningHint = null,
        enabledModes = PracticeMode.entries.map { it.persistedValue }.toSet(),
        gameTypes = setOf("typing", "rain", "cafe"),
    )
}
