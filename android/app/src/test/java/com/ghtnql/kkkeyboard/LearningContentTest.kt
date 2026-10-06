package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningContentTest {
    @Test fun parsesAndFiltersExportedDriveRows() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val items = LearningContent.load(RuntimeEnvironment.getApplication())

        assertEquals(564, items.size)
        assertEquals(80, items.count { it.id.startsWith("game_ja_") })
        assertEquals(
            listOf("ありがとう", "こんにちは"),
            LearningContent.forMode(items, PracticeMode.JAPANESE_TO_HANGUL, "typing").take(2).map { it.sourceText },
        )
        assertEquals(32, LearningContent.forMode(items, PracticeMode.JAPANESE_TO_HANGUL, "typing").size)
        assertEquals(63, LearningContent.forMode(items, PracticeMode.JAPANESE_TO_HANGUL, "rain").size)
        assertEquals(22, LearningContent.forMode(items, PracticeMode.JAPANESE_TO_HANGUL, "convert").size)
        assertEquals(64, LearningContent.forMode(items, PracticeMode.KOREAN_TYPING, "rain").size)
        assertEquals(
            listOf(
                "커피", "우유", "딸기우유", "커피", "카페라테", "카푸치노", "에스프레소", "아메리카노",
                "카페모카", "말차라테", "홍차", "밀크티", "레몬티", "코코아", "우유", "오렌지주스",
                "사과주스", "레모네이드", "스무디", "탄산수", "물", "아이스티", "뜨거운 커피", "크루아상",
                "베이글", "머핀", "도넛", "샌드위치", "토스트", "케이크", "치즈케이크", "초코케이크",
                "쿠키", "스콘", "푸딩", "와플", "빵", "잼", "버터", "꿀", "생크림", "초콜릿",
                "딸기", "바닐라", "시럽", "얼음", "설탕", "우유 추가", "샷 추가", "얼음 적게",
                "덜 달게", "포장", "매장", "주문", "계산", "영수증", "번호표", "빨대", "숟가락",
                "포크", "냅킨", "기다리셨습니다", "감사합니다",
            ),
            LearningContent.forMode(items, PracticeMode.KOREAN_TYPING, "cafe").map { it.sourceText },
        )
        assertEquals(
            listOf("コーヒー", "ミルク", "いちごミルク"),
            LearningContent.forMode(items, PracticeMode.JAPANESE_TO_HANGUL, "cafe").take(3).map { it.sourceText },
        )
        assertEquals(274, LearningContent.forMode(items, PracticeMode.JAPANESE_TO_HANGUL, "cafe").size)
        val vegetables = items.single { it.id == "jamo_ja_word_vegetables" }
        assertEquals("野菜", vegetables.sourceText)
        assertEquals(listOf("야사이"), vegetables.acceptedAnswers)
        assertEquals("뜻: 채소", vegetables.meaningHint)
        val sentences = LearningContent.forMode(items, PracticeMode.KOREAN_TYPING, "sentence")
        assertEquals(110, sentences.size)
        assertTrue(sentences.all { it.category.isNotBlank() })
        assertTrue(sentences.all { it.difficulty in 1..3 })
        assertTrue(sentences.all { it.acceptedAnswers == listOf(it.sourceText) })
        assertEquals(sentences.size, sentences.map { it.id }.distinct().size)
        assertEquals(sentences.size, sentences.map { it.sourceText }.distinct().size)
        assertTrue(items.all { it.acceptedAnswers.isNotEmpty() })
    }

    @Test fun rejectsDuplicateIds() {
        val row = """{"id":"same","category":"x","difficulty":1,"sourceLanguage":"ko","sourceText":"가","targetLanguage":"ko","acceptedAnswers":["가"],"enabledModes":["ko_same_hangul"],"gameTypes":["typing"]}"""
        assertFails { LearningContent.parse("[$row,$row]") }
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); throw AssertionError("Expected failure") } catch (_: IllegalArgumentException) { }
    }
}
