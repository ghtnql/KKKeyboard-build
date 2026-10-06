package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTest {
    private val items = listOf(
        item("one", "ありがとう", "아리가토", "아리가또"),
        item("two", "こんにちは", "곤니치와", "콘니치와"),
    )

    @Test fun acceptsAlternativeAnswersAndCompletes() {
        val session = PracticeSession(PracticeMode.JAPANESE_TO_HANGUL, items, 1_000L)

        assertTrue(session.submit(" 아리가또 ").correct)
        val last = session.submit("콘니치와")
        assertTrue(last.correct)
        assertTrue(last.completed)

        val result = session.result(61_000L)
        assertEquals(100, result.accuracy)
        assertEquals(2, result.maxCombo)
        assertEquals(8, result.cpm)
        assertTrue(result.completed)
    }

    @Test fun wrongAnswerStaysOnItemAndResetsCombo() {
        val session = PracticeSession(PracticeMode.JAPANESE_TO_HANGUL, items, 0L)
        session.submit("아리가토")
        val wrong = session.submit("곤니치")

        assertFalse(wrong.correct)
        assertEquals(1, session.currentIndex)
        assertEquals(0, session.combo)
        assertEquals(1, session.errorCount)
    }

    @Test fun basicStyleRemainsTheDefaultAndKeepsExistingModeId() {
        val session = PracticeSession(PracticeMode.JAPANESE_TO_HANGUL, items, 0L)

        assertEquals(PracticeStyle.BASIC, session.style)
        assertEquals("ja_to_hangul_pronunciation", session.result(1L).modeId)
        session.submit("")
        assertEquals(0, session.currentIndex)
    }

    @Test fun shortSentenceAdvancesAfterNonblankMistakeAndUsesSentenceModeId() {
        val sentenceItems = listOf(
            koreanItem("first", "별빛이 조용한 창가에 내려앉아"),
            koreanItem("second", "오늘의 마음을 천천히 노래해"),
        )
        val session = PracticeSession(
            PracticeMode.KOREAN_TYPING,
            sentenceItems,
            0L,
            PracticeStyle.SHORT_SENTENCE,
        )

        val attempt = session.submit("별빛이 조용한 창가에 내려앉았어")

        assertFalse(attempt.correct)
        assertFalse(attempt.completed)
        assertEquals(1, session.currentIndex)
        assertEquals(2, session.errorCount)
        assertEquals("sentence_ko_same_hangul", session.result(1_000L).modeId)
    }

    @Test fun shortSentenceIgnoresBlankSubmission() {
        val session = PracticeSession(
            PracticeMode.KOREAN_TYPING,
            listOf(koreanItem("first", "별빛이 조용한 창가에 내려앉아")),
            0L,
            PracticeStyle.SHORT_SENTENCE,
        )

        val attempt = session.submit("   ")

        assertFalse(attempt.correct)
        assertFalse(attempt.completed)
        assertEquals(0, session.currentIndex)
        assertEquals(0, session.totalTypedCharacters)
        assertEquals(0, session.errorCount)
    }

    private fun item(id: String, prompt: String, vararg answers: String) = LearningItem(
        id, "greeting", 1, "ja", prompt, "ja", answers.toList(), null,
        setOf(PracticeMode.JAPANESE_TO_HANGUL.persistedValue), setOf("typing", "rain"),
    )

    private fun koreanItem(id: String, text: String) = LearningItem(
        id, "lyric_original", 2, "ko", text, "ko", listOf(text), null,
        setOf(PracticeMode.KOREAN_TYPING.persistedValue), setOf("sentence"),
    )
}
