package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CafeGameTest {
    private val items = listOf(
        item("one", "물", "물"),
        item("two", "우유", "우유"),
        item("three", "차", "차"),
    )

    @Test fun cyclesThreeItemsThroughFiveOrders() {
        val game = CafeGame(PracticeMode.KOREAN_TYPING, items, startedAtMs = 0L)
        val seen = mutableListOf<String>()

        repeat(5) {
            val order = game.currentOrder!!
            seen += order.id
            game.submit(order.acceptedAnswers.first())
        }

        assertEquals(listOf("one", "two", "three", "one", "two"), seen)
        assertEquals(5, game.currentIndex)
        assertTrue(game.isFinished)
        assertNull(game.currentOrder)
    }

    @Test fun wrongAnswerAdvancesOrderAndResetsCombo() {
        val game = CafeGame(PracticeMode.KOREAN_TYPING, items, startedAtMs = 0L)
        game.submit("물")

        val submission = game.submit("차")

        assertFalse(submission.correct)
        assertFalse(submission.completed)
        assertEquals(0, submission.scoreGained)
        assertEquals(2, game.currentIndex)
        assertEquals("three", game.currentOrder!!.id)
        assertEquals(0, game.combo)
        assertEquals(2, game.errorCount)
        assertEquals(1, game.successfulOrderCount)
        assertEquals(1, game.failedOrderCount)
    }

    @Test fun finalWrongAnswerCompletesRound() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 1,
        )

        val submission = game.submit("커피")

        assertFalse(submission.correct)
        assertTrue(submission.completed)
        assertEquals(1, game.currentIndex)
        assertTrue(game.isFinished)
        assertNull(game.currentOrder)
        assertEquals(0, game.successfulOrderCount)
        assertEquals(1, game.failedOrderCount)
    }

    @Test fun scoresByCharactersAndComboAndCompletesAtOrderCount() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 2,
        )

        game.beginOrder()
        val first = game.submit(" 물 ")
        game.beginOrder()
        val second = game.submit("우유")

        assertEquals(23, first.scoreGained)
        assertFalse(first.completed)
        assertEquals(33, second.scoreGained)
        assertTrue(second.completed)
        assertEquals(56, game.score)
        assertEquals(2, game.combo)
        assertEquals(2, game.maxCombo)
        assertEquals(2, game.successfulOrderCount)
        assertEquals(0, game.failedOrderCount)
    }

    @Test fun mixedFiveOrderRoundFinishesWithSuccessAndFailureCounts() {
        val game = CafeGame(PracticeMode.KOREAN_TYPING, items, startedAtMs = 0L)

        val submissions = listOf(
            game.submit("물"),
            game.submit("우유 아님"),
            game.submit("차"),
            game.submit("물 아님"),
            game.submit("우유"),
        )

        assertEquals(listOf(true, false, true, false, true), submissions.map { it.correct })
        assertEquals(listOf(false, false, false, false, true), submissions.map { it.completed })
        assertEquals(5, game.currentIndex)
        assertEquals(3, game.successfulOrderCount)
        assertEquals(2, game.failedOrderCount)
        assertTrue(game.isFinished)
        assertNull(game.currentOrder)
    }

    @Test fun emptyAnswerDoesNotMutateOrConsumeOrder() {
        val game = CafeGame(PracticeMode.KOREAN_TYPING, items, startedAtMs = 0L)

        val submission = game.submit(" \t\n ")

        assertFalse(submission.correct)
        assertFalse(submission.completed)
        assertEquals(0, submission.scoreGained)
        assertEquals(0, game.currentIndex)
        assertEquals("one", game.currentOrder!!.id)
        assertEquals(0, game.totalTypedCharacters)
        assertEquals(0, game.errorCount)
        assertEquals(0, game.combo)
        assertEquals(0, game.score)
        assertEquals(0, game.successfulOrderCount)
        assertEquals(0, game.failedOrderCount)
    }

    @Test fun submissionAfterFinishDoesNotMutateGame() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 1,
        )
        game.submit("물")

        val submission = game.submit("wrong")

        assertFalse(submission.correct)
        assertTrue(submission.completed)
        assertEquals(0, submission.scoreGained)
        assertEquals(1, game.currentIndex)
        assertEquals(23, game.score)
        assertEquals(1, game.combo)
        assertEquals(1, game.maxCombo)
        assertEquals(0, game.errorCount)
        assertEquals(1, game.totalTypedCharacters)
        assertEquals(1, game.successfulOrderCount)
        assertEquals(0, game.failedOrderCount)
    }

    @Test fun resultReportsFinishedCafeRoundWithMistakes() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 1_000L,
            orderCount = 2,
        )
        game.submit("무")
        game.submit("우유")

        val result = game.result(61_000L)

        assertEquals("cafe_normal_ko_same_hangul", result.modeId)
        assertEquals(3, result.totalExpectedCharacters)
        assertEquals(3, result.totalTypedCharacters)
        assertEquals(1, result.errorCount)
        assertEquals(66, result.accuracy)
        assertEquals(2, result.cpm)
        assertEquals(1, result.maxCombo)
        assertEquals(31, result.score)
        assertTrue(result.completed)
    }

    @Test fun difficultiesExposeOrderTimesAndRushMemoryReveal() {
        assertEquals(20_000L, CafeDifficulty.RELAXED.baseOrderTimeMs)
        assertNull(CafeDifficulty.RELAXED.memoryRevealMs)
        assertEquals(13_000L, CafeDifficulty.NORMAL.baseOrderTimeMs)
        assertNull(CafeDifficulty.NORMAL.memoryRevealMs)
        assertEquals(9_000L, CafeDifficulty.RUSH.baseOrderTimeMs)
        assertEquals(2_500L, CafeDifficulty.RUSH.memoryRevealMs)

        val defaultGame = CafeGame(PracticeMode.KOREAN_TYPING, items, startedAtMs = 0L)
        assertEquals(CafeDifficulty.NORMAL, defaultGame.difficulty)
        assertEquals(13_000L, defaultGame.currentOrderTotalTimeMs)
        assertEquals(13_000L, defaultGame.currentOrderRemainingTimeMs)
    }

    @Test fun timerOnlyRunsBetweenBeginOrderAndNonblankSubmission() {
        val game = CafeGame(PracticeMode.KOREAN_TYPING, items, startedAtMs = 0L)

        assertNull(game.tick(5_000L))
        assertEquals(13_000L, game.currentOrderRemainingTimeMs)
        game.beginOrder()
        assertNull(game.tick(3_000L))
        assertEquals(10_000L, game.currentOrderRemainingTimeMs)

        game.submit("  ")
        assertNull(game.tick(1_000L))
        assertEquals(9_000L, game.currentOrderRemainingTimeMs)

        game.submit("물")
        assertEquals(1, game.currentIndex)
        assertEquals(11_050L, game.currentOrderTotalTimeMs)
        assertNull(game.tick(5_000L))
        assertEquals(11_050L, game.currentOrderRemainingTimeMs)
    }

    @Test fun rushOrderHidesAtExactMemoryWindow() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            difficulty = CafeDifficulty.RUSH,
        )

        assertFalse(game.shouldHideCurrentOrder)
        game.beginOrder()
        game.tick(2_499L)
        assertFalse(game.shouldHideCurrentOrder)
        game.tick(1L)
        assertTrue(game.shouldHideCurrentOrder)
    }

    @Test fun timeoutFailsCurrentOrderAndReturnsCompletionEvent() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 2,
            difficulty = CafeDifficulty.RUSH,
        )
        game.beginOrder()

        val timeout = game.tick(9_000L)

        assertEquals(CafeTickResult(timedOut = true, completed = false), timeout)
        assertEquals(1, game.currentIndex)
        assertEquals(1, game.errorCount)
        assertEquals(1, game.failedOrderCount)
        assertEquals(0, game.combo)
        assertEquals(7_650L, game.currentOrderTotalTimeMs)
        assertNull(game.tick(20_000L))

        game.beginOrder()
        val finalTimeout = game.tick(7_650L)
        assertEquals(CafeTickResult(timedOut = true, completed = true), finalTimeout)
        assertTrue(game.isFinished)
        assertEquals(0L, game.currentOrderTotalTimeMs)
        assertEquals(0L, game.currentOrderRemainingTimeMs)
    }

    @Test fun orderDurationShortensToFiftyFivePercentFloor() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 6,
            difficulty = CafeDifficulty.RELAXED,
        )
        val durations = mutableListOf<Long>()

        repeat(6) {
            game.beginOrder()
            durations += game.currentOrderTotalTimeMs
            game.submit(game.currentOrder!!.acceptedAnswers.first())
        }

        assertEquals(listOf(20_000L, 17_000L, 14_000L, 11_000L, 11_000L, 11_000L), durations)
    }

    @Test fun scoreAddsWholeRemainingSecondsAsTimeBonus() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 1,
        )
        game.beginOrder()
        game.tick(3_400L)

        val submission = game.submit("물")

        assertEquals(19, submission.scoreGained)
        assertEquals(19, game.score)
    }

    @Test fun finishedGameIgnoresBeginTickAndSubmit() {
        val game = CafeGame(
            PracticeMode.KOREAN_TYPING,
            items,
            startedAtMs = 0L,
            orderCount = 1,
        )
        game.beginOrder()
        game.tick(1_000L)
        game.submit("물")
        val score = game.score

        game.beginOrder()
        assertNull(game.tick(50_000L))
        val submission = game.submit("우유")

        assertTrue(submission.completed)
        assertEquals(0, submission.scoreGained)
        assertEquals(1, game.currentIndex)
        assertEquals(score, game.score)
        assertEquals(0L, game.currentOrderTotalTimeMs)
        assertEquals(0L, game.currentOrderRemainingTimeMs)
    }

    private fun item(id: String, prompt: String, answer: String) = LearningItem(
        id = id,
        category = "cafe",
        difficulty = 1,
        sourceLanguage = "ko",
        sourceText = prompt,
        targetLanguage = "ko",
        acceptedAnswers = listOf(answer),
        meaningHint = null,
        enabledModes = setOf(PracticeMode.KOREAN_TYPING.persistedValue),
        gameTypes = setOf("cafe"),
    )
}
