package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CafeDifficultyTest {
    private fun item(text: String, level: Int = 1) = LearningItem(
        text, "cafe", level, "ko", text, "ko", listOf(text), null,
        listOf(PracticeMode.KOREAN.id), listOf("cafe"),
    )

    @Test fun actualEnumEntriesHaveThreeDistinctLocalizedLabels() {
        val labels = mapOf(
            UiLanguage.KO to listOf("쉬움", "보통", "어려움"),
            UiLanguage.JA to listOf("かんたん", "普通", "むずかしい"),
            UiLanguage.EN to listOf("Easy", "Normal", "Hard"),
        )
        labels.forEach { (language, expected) ->
            assertEquals(expected, CafeDifficulty.entries.map { UiStrings(language).difficulty(it.name) })
        }
    }

    @Test fun lowMetadataDifficultyCannotSneakLongSentencesIntoCafe() {
        val pool = listOf(item("커피"), item("뜨거운 라테", 2), item("아".repeat(9)), item("가".repeat(13)))
        assertEquals(listOf("커피"), pool.forCafe(PracticeMode.KOREAN, CafeDifficulty.RELAXED).map { it.id })
        assertEquals(listOf("커피", "뜨거운 라테"), pool.forCafe(PracticeMode.KOREAN, CafeDifficulty.NORMAL).map { it.id })
        assertEquals(listOf("커피", "뜨거운 라테", "아".repeat(9)), pool.forCafe(PracticeMode.KOREAN, CafeDifficulty.RUSH).map { it.id })
    }

    @Test fun everyDisplayedLineAndAnswerAliasMustRespectNormalLengthLimit() {
        val short = item("커피")
        val long = "가".repeat(9)
        listOf(short.copy(sourceText = long), short.copy(japaneseText = long),
            short.copy(japaneseHangulPronunciation = long), short.copy(koreanText = long),
            short.copy(acceptedAnswers = listOf("커피", long))).forEach {
            assertTrue(listOf(it).forCafe(PracticeMode.KOREAN, CafeDifficulty.NORMAL).isEmpty())
        }
        assertEquals(listOf(short.copy(meaningHint = long.repeat(3))),
            listOf(short.copy(meaningHint = long.repeat(3))).forCafe(PracticeMode.KOREAN, CafeDifficulty.NORMAL))
    }

    @Test fun contentLevelsModesAndEmptyAnswersAreEnforced() {
        val candidates = listOf(item("커피", 2), item("물", 0), item("홍차", 4),
            item("우유").copy(acceptedAnswers = listOf(" ")),
            item("차").copy(enabledModes = listOf(PracticeMode.JAPANESE.id)))
        assertTrue(candidates.forCafe(PracticeMode.KOREAN, CafeDifficulty.RELAXED).isEmpty())
        assertEquals(listOf("커피"), candidates.forCafe(PracticeMode.KOREAN, CafeDifficulty.NORMAL).map { it.id })
    }

    @Test fun sessionFiltersBeforeRandomizedOrdersAndNeverFallsBackToLongPool() {
        val pool = listOf(item("가".repeat(20)), item("커피"))
        val cafe = CafeSession(PracticeMode.KOREAN, pool, CafeDifficulty.NORMAL, 5)
        repeat(5) {
            assertEquals("커피", cafe.currentItem?.sourceText)
            assertTrue(cafe.submit("커피"))
        }
        assertTrue(cafe.finished)
        assertFailsWith<IllegalArgumentException> {
            CafeSession(PracticeMode.KOREAN, listOf(item("가".repeat(20))), CafeDifficulty.NORMAL)
        }
    }

    @Test fun decomposedHangulAndSpacesDoNotInflateDifficulty() {
        val decomposed = "가".repeat(4)
        assertEquals(1, listOf(item(decomposed)).forCafe(PracticeMode.KOREAN, CafeDifficulty.RELAXED).size)
        assertEquals(1, listOf(item(" 가 가 가 가 ")).forCafe(PracticeMode.KOREAN, CafeDifficulty.RELAXED).size)
    }
}
