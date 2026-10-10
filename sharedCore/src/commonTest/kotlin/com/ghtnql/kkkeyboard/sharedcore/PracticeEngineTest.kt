package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeEngineTest {
    @Test fun basicRetriesAndAcceptsAlternateDecomposedAnswer() {
        val engine = PracticeEngine(listOf(PracticeQuestion("한글", listOf("한글", "대안")), PracticeQuestion("한글")), "ko", false)
        assertFalse(engine.submit("실패"))
        assertEquals(0, engine.currentIndex)
        assertEquals(2, engine.errorCount)
        assertTrue(engine.submit(" 한글 "))
        assertTrue(engine.submit("한글"))
        val result = engine.result(1_000)
        assertEquals(42, result.score)
        assertEquals(2, result.maxCombo)
        assertEquals(4, result.totalExpectedCharacters)
        assertEquals(4, result.totalTypedCharacters - 2)
        assertTrue(result.completed)
    }

    @Test fun sentenceWrongAnswerAdvancesAndCountsDistance() {
        val engine = PracticeEngine(listOf(PracticeQuestion("こんにちは")), "sentence_mixed", true)
        assertFalse(engine.submit("さようなら"))
        assertEquals(1, engine.currentIndex)
        assertTrue(engine.result(1_000).completed)
        assertEquals(5, engine.result(1_000).totalExpectedCharacters)
        assertFalse(engine.submit("こんにちは"))
    }

    @Test fun emptySubmissionDoesNotMutateAndResultClampsDuration() {
        val engine = PracticeEngine(listOf(PracticeQuestion("한글")), "ko", false)
        assertFalse(engine.submit("   "))
        assertEquals(0, engine.typedCharacters)
        val result = engine.result(0)
        assertEquals(0, result.accuracy)
        assertEquals(1, result.durationMs)
        assertEquals(0, result.cpm)
        assertFalse(result.completed)
    }
}
