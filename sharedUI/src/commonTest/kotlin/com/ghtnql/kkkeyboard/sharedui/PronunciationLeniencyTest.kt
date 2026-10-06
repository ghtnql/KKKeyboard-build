package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.*

class PronunciationLeniencyTest {
    private fun item(answer: String, language: String = "ja") = LearningItem(
        "test", "test", 1, language, "日本語", language, listOf(answer), "hint",
        listOf(PracticeMode.JAPANESE.id, PracticeMode.KOREAN.id), listOf("typing", "convert", "rain", "cafe", "sentence"),
        japaneseText = "日本語", japaneseHangulPronunciation = answer, koreanText = answer,
    )

    @Test fun allJapaneseWordGamesAcceptVariantsWithoutChangingContent() {
        for ((expected, answer) in listOf("규우뉴우" to "큐뉴", "쿄오" to "교", "토우쿄우" to "도교",
            "벤쿄오" to "벤교", "큐슈" to "규슈", "도쿄" to "토쿄")) {
            val item = item(expected)
            for (style in listOf(PracticeStyle.BASIC, PracticeStyle.CONVERT)) {
                val practice = PracticeSession(PracticeMode.JAPANESE, style, listOf(item))
                assertTrue(practice.submit(answer), "$style: $expected/$answer")
                assertEquals(0, practice.errorCount)
            }
            val rain = RainSession(PracticeMode.JAPANESE, listOf(item))
            rain.tick(0)
            assertTrue(rain.submit(answer), expected)
            assertEquals(0, rain.errorCount)
            val cafe = CafeSession(PracticeMode.JAPANESE, listOf(item), CafeDifficulty.NORMAL)
            assertTrue(cafe.submit(answer), expected)
            assertEquals(0, cafe.errorCount)
            assertTrue(item.matchesGamePrefix(answer, PracticeMode.JAPANESE))
            assertEquals(listOf(expected), item.acceptedAnswers)
            assertEquals(expected, item.japaneseHangulPronunciation)
            assertEquals("日本語", item.sourceText)
        }
    }

    @Test fun wrongVowelsKoreanModesAndSentenceCopiesStayStrict() {
        for ((expected, answer) in listOf("아이" to "아", "규우뉴우" to "큐뉴", "도쿄" to "토쿄")) {
            val japanese = item(expected)
            if (expected == "아이") {
                assertFalse(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.BASIC, listOf(japanese)).submit(answer))
                val rain = RainSession(PracticeMode.JAPANESE, listOf(japanese)); rain.tick(0)
                assertFalse(rain.submit(answer))
                assertFalse(CafeSession(PracticeMode.JAPANESE, listOf(japanese), CafeDifficulty.NORMAL).submit(answer))
            }
            val korean = item(expected, "ko")
            assertFalse(PracticeSession(PracticeMode.KOREAN, PracticeStyle.BASIC, listOf(korean)).submit(answer))
            val rain = RainSession(PracticeMode.KOREAN, listOf(korean)); rain.tick(0)
            assertFalse(rain.submit(answer))
            assertFalse(CafeSession(PracticeMode.KOREAN, listOf(korean), CafeDifficulty.NORMAL).submit(answer))
            assertFalse(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.SENTENCE, listOf(japanese),
                randomTarget = { SentenceTarget.KOREAN }).submit(answer))
            assertFalse(japanese.matchesGameAnswer(answer, PracticeMode.KOREAN))
        }
        assertTrue(PracticeSession(PracticeMode.JAPANESE, PracticeStyle.SENTENCE, listOf(item("규우뉴우")),
            randomTarget = { SentenceTarget.KOREAN }).submit("규우뉴우"))
    }
}
