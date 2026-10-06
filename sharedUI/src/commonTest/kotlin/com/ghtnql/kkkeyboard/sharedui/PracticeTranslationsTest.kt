package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeTranslationsTest {
    private fun item(id: String, language: String, source: String, hint: String? = null,
                     answers: List<String> = emptyList()) = LearningItem(
        id = id, category = "test", difficulty = 1, sourceLanguage = language,
        sourceText = source, targetLanguage = language, acceptedAnswers = answers,
        meaningHint = hint, enabledModes = emptyList(), gameTypes = listOf("typing"),
    )

    @Test fun japaneseItemUsesCuratedHangulAndMeaningHint() {
        val japanese = item("ja", "ja", "ありがとう", " 뜻: 고마워요 ", listOf("", " 아리가토 ", "아리가또"))
        assertEquals(PracticeTranslations("고마워요", "아리가토", "ありがとう"),
            PracticeTranslationCatalog(listOf(japanese)).resolve(japanese))
    }

    @Test fun koreanItemUsesRawOrPrefixedJapaneseHintAndExactJapanesePairing() {
        val japanese = item("ja", "ja", "ありがとう", "고마워요", listOf("아리가토"))
        val raw = item("ko-raw", "ko", "고마워요", "ありがとう", listOf("고마워요"))
        val prefixed = item("ko-prefix", "ko", "고마워요", " 일본어: ありがとう ", listOf("고마워요"))
        val catalog = PracticeTranslationCatalog(listOf(raw, prefixed, japanese))
        assertEquals(PracticeTranslations("고마워요", "아리가토", "ありがとう"), catalog.resolve(raw))
        assertEquals(catalog.resolve(raw), catalog.resolve(prefixed))
    }

    @Test fun sharedMeaningDoesNotCauseWrongJapanesePronunciation() {
        val first = item("ja-a", "ja", "ありがとう", "고마워요", listOf("아리가토"))
        val other = item("ja-b", "ja", "どうも", "고마워요", listOf("도모"))
        val korean = item("ko", "ko", "고마워요", "どうも")
        assertEquals("도모", PracticeTranslationCatalog(listOf(first, other, korean)).resolve(korean).hangulPronunciation)
        assertNull(PracticeTranslationCatalog(listOf(first)).resolve(korean).hangulPronunciation)
    }

    @Test fun explicitFieldsOverrideLegacySourcesWithoutChangingLearningItem() {
        val original = item("explicit", "ko", "옛 한국어", "옛 일본어", listOf("정답"))
        val explicit = original.copy(japaneseText = "新しい", japaneseHangulPronunciation = "아타라시이", koreanText = "새로운")
        val triad = PracticeTranslationCatalog(listOf(explicit)).resolve(explicit)
        assertEquals(PracticeTranslations("새로운", "아타라시이", "新しい"), triad)
        assertTrue(triad.isComplete)
        assertEquals(original.acceptedAnswers, explicit.acceptedAnswers)
    }
}
