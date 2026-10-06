package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RainGameTest {
    private val item = LearningItem(
        "ko_1", "greeting", 1, "ko", "안녕", "ko", listOf("안녕"), null,
        setOf(PracticeMode.KOREAN_TYPING.persistedValue), setOf("rain"),
    )

    @Test fun correctInputRemovesWordAndScoresCharacters() {
        val game = RainGame(PracticeMode.KOREAN_TYPING, listOf(item), 0L, randomFloat = { 0.5f })
        game.tick(0)
        assertEquals(1, game.targets.size)

        val result = game.submit("안녕")

        assertTrue(result.correct)
        assertEquals(20, game.score)
        assertEquals(1, game.combo)
        assertTrue(game.targets.isEmpty())
    }

    @Test fun wrongInputResetsComboWithoutDirectScorePenalty() {
        val game = RainGame(PracticeMode.KOREAN_TYPING, listOf(item), 0L)
        game.tick(0)
        game.submit("안녕")
        repeat(10) { game.tick(240) }
        val score = game.score

        assertFalse(game.submit("아녕").correct)
        assertEquals(score, game.score)
        assertEquals(0, game.combo)
        assertEquals(1, game.errorCount)
    }

    @Test fun reachingFloorCostsLifeAndEventuallyEndsGame() {
        val config = RainGameConfig(spawnIntervalMs = 100_000, fallPerSecond = 10f, startingLives = 1)
        val game = RainGame(PracticeMode.KOREAN_TYPING, listOf(item), 0L, config)
        game.tick(0)
        val missed = game.tick(100)

        assertEquals(0, game.lives)
        assertTrue(game.isFinished)
        assertEquals(listOf(1L), missed.missedTargetIds)
    }

    @Test fun japanesePromptIsAnsweredWithHangulPronunciation() {
        val japanese = item.copy(
            id = "ja_1",
            sourceLanguage = "ja",
            sourceText = "ありがとう",
            targetLanguage = "ja",
            acceptedAnswers = listOf("아리가토", "아리가또"),
            enabledModes = setOf(PracticeMode.JAPANESE_TO_HANGUL.persistedValue),
        )
        val game = RainGame(PracticeMode.JAPANESE_TO_HANGUL, listOf(japanese), 0L) { 0.5f }
        game.tick(0)

        assertEquals("ありがとう", game.targets.single().item.sourceText)
        assertTrue(game.submit("아리가또").correct)
        assertEquals(40, game.score)
    }

    @Test fun submissionReportsThePointsEarnedForHitEffects() {
        val game = RainGame(PracticeMode.KOREAN_TYPING, listOf(item), 0L, randomFloat = { 0.5f })
        game.tick(0)

        val submission = game.submit("안녕")

        assertEquals(20, submission.scoreGained)
        assertEquals(1L, submission.removedTargetId)
    }

    @Test fun resultDurationUsesActiveTickTime() {
        val game = RainGame(PracticeMode.KOREAN_TYPING, listOf(item), 50_000L)
        game.tick(0)
        game.tick(200)

        assertEquals(200L, game.result(90_000L).durationMs)
    }
}
