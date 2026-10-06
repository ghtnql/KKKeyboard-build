package com.ghtnql.kkkeyboard.sharedui

import kotlin.random.Random
import kotlin.test.*

class JamoVocabularyTest {
    private fun ja(id: String, original: String, target: String, meaning: String,
                   games: List<String> = listOf("cafe"), aliases: List<String> = listOf(target)) = LearningItem(
        id, "food", 1, "ja", original, "ja", aliases, "뜻: $meaning",
        listOf(PracticeMode.JAPANESE.id), games)
    private val milk = ja("milk", "牛乳", "규우뉴우", "우유", aliases = listOf("규우뉴우", "규뉴"))
    private val items = listOf(milk,
        ja("rice", "ご飯", "고한", "밥"), ja("thanks", "ありがとう", "아리가토", "고마워", listOf("rain")),
        ja("bread", "パン", "판", "빵"), ja("water", "水", "미즈", "물"),
        ja("coffee", "コーヒー", "코히", "커피"), ja("tea", "お茶", "오차", "차"),
        ja("alias", "牛", "규뉴", "소"),
        ja("america", "アメリカ", "아메리카", "미국"),
        ja("goods", "品物", "시나모노", "물건"),
        ja("katakana", "カタカナ", "카타카나", "가타카나"),
        ja("hanamichi", "花道", "하나미치", "꽃길"),
        ja("sentence", "文", "문", "문장", listOf("sentence")))
    private fun letter(glyph: String) = (basicConsonants + basicVowels).single { it.glyph == glyph }

    @Test fun milkExampleUsesActualSourceTriadAndNonGameWordsStayOut() {
        val catalog = JamoWordCatalog(items)
        val example = catalog.examples(letter("ㄱ")).first()
        assertEquals("milk", example.sourceId)
        assertEquals("牛乳", example.japanese)
        assertEquals("규우뉴우", example.hangul)
        assertEquals("우유", example.koreanMeaning)
        assertFalse(catalog.words.any { it.sourceId == "sentence" })
    }

    @Test fun questionAvoidsStudyExamplesAndUsesFourUnambiguousSourceAnswers() {
        val catalog = JamoWordCatalog(items)
        val excluded = catalog.examples(letter("ㄱ")).map { it.sourceId }.toSet()
        val q = assertNotNull(catalog.question(letter("ㄱ"), excluded, Random(17)))
        assertFalse(q.word.sourceId in excluded)
        assertTrue(startsWithJamo(q.word.hangul, letter("ㄱ")))
        assertEquals(4, q.options.size)
        assertEquals(1, q.options.map { it.hangul.count { ch -> !ch.isWhitespace() } }.toSet().size)
        assertEquals(4, q.options.map { it.hangul }.toSet().size)
        assertEquals(1, q.options.count { it.sourceId == q.word.sourceId })
        q.options.forEach { assertTrue(it in catalog.words) }
        assertTrue(q.options.all { it.kind == q.word.kind })
        assertTrue(q.options.filter { it.sourceId != q.word.sourceId }.none {
            it.hangul in q.word.aliases || it.japanese == q.word.japanese
        })
        val milkQuestion = assertNotNull(catalog.question(letter("ㅠ"), excludedSourceIds = setOf("alias"), random = Random(4)))
        assertEquals("milk", milkQuestion.word.sourceId)
        assertFalse(milkQuestion.options.any { it.sourceId == "alias" })
    }

    @Test fun seededSelectionIsRepeatableAndSparseContentDoesNotInventAnswers() {
        val catalog = JamoWordCatalog(items)
        assertEquals(catalog.question(random = Random(9)), catalog.question(random = Random(9)))
        assertEquals(
            catalog.question(letter("ㄱ"), random = Random(3)),
            catalog.question(letter("ㄱ"), random = Random(3))
        )
        assertNull(JamoWordCatalog(listOf(milk)).question())
        assertNull(JamoWordCatalog(listOf(milk)).question(letter("ㄱ"), random = Random(1)))
        assertNull(JamoWordCatalog(emptyList()).question())
        assertTrue(JamoWordCatalog(emptyList()).examples(letter("ㄱ")).isEmpty())
    }

    @Test fun beginnerWordQuizDoesNotIncludeLongPoetrySentencesFromTheGames() {
        val long = ja("poetry", "朝焼けの向こうに夢を探した", "아사야케노 무코오니 유메오 사가시타", "아침놀을 넘어 꿈을 찾았다", listOf("rain"))
        val chat = ja("chat", "今日も頑張ろう。", "쿄오모 간바로오", "오늘도 힘내자")
        val catalog = JamoWordCatalog(items + long + chat)
        assertFalse(catalog.words.any { it.sourceId == "chat" })
        assertTrue(catalog.words.all { it.hangul.count { ch -> !ch.isWhitespace() } <= 5 })
        assertFalse(catalog.words.any { it.sourceId == "poetry" })
        assertTrue(catalog.words.any { it.sourceId == "milk" })
    }

    @Test fun initialAndMedialMatchingDoesNotTreatFinalConsonantsAsInitials() {
        assertTrue(containsJamo("규우뉴우", letter("ㄱ")))
        assertTrue(containsJamo("규우뉴우", letter("ㅠ")))
        assertFalse(containsJamo("악", letter("ㄱ")))
        assertFalse(containsJamo("강", letter("ㅇ")))
        assertTrue(containsJamo("악", letter("ㅇ")))
        assertTrue(containsJamo("커피", letter("ㅓ")))
        assertFalse(containsJamo("코히", letter("ㅓ")))
    }

    @Test fun vowelsAbsentFromJapanesePhoneticsUseLabelledRealKoreanGameWords() {
        val jaWords = items + ja("winter-ja", "冬", "후유", "겨울") + ja("bakery-ja", "パン屋", "판야", "빵집")
        val koWords = listOf("커피" to "コーヒー", "겨울" to "冬", "우유" to "牛乳", "빵집" to "パン屋").mapIndexed { i, (ko, jp) ->
            LearningItem("ko$i", "food", 1, "ko", ko, "ko", listOf(ko), "일본어: $jp",
                listOf(PracticeMode.KOREAN.id), listOf("rain"))
        }
        val catalog = JamoWordCatalog(jaWords + koWords)
        val q = assertNotNull(catalog.question(letter("ㅓ"), random = Random(1)))
        assertEquals(JamoWordKind.KOREAN_MEANING, q.word.kind)
        assertEquals("커피", q.word.hangul)
        assertTrue(q.options.all { it.kind == JamoWordKind.KOREAN_MEANING })
        assertNotEquals(jamoJapaneseReading(letter("ㅓ")), jamoJapaneseReading(letter("ㅗ")))
        assertNotEquals(jamoJapaneseReading(letter("ㅡ")), jamoJapaneseReading(letter("ㅜ")))
    }

    @Test fun focusedQuizMakesTargetLetterTheOnlyDistinguisher() {
        val catalog = JamoWordCatalog(items)
        val g = letter("ㄱ")
        repeat(10) { seed ->
            val q = assertNotNull(catalog.question(g, random = Random(seed)), "seed $seed must yield a question")
            assertTrue(startsWithJamo(q.word.hangul, g), "correct ${q.word.hangul} must start with ㄱ")
            val distractors = q.options.filter { it.sourceId != q.word.sourceId }
            assertEquals(3, distractors.size)
            assertTrue(distractors.none { startsWithJamo(it.hangul, g) },
                "distractors ${distractors.map { it.hangul }} must not start with ㄱ")
            assertEquals(1, q.options.count { startsWithJamo(it.hangul, g) })
        }
    }

    @Test fun focusedQuizUsesFirstSyllableEvenWhenLaterSyllablesContainTarget() {
        // 반고한/마루고한-style words may contain ㄱ later, but they are valid
        // non-ㄱ choices because focused learning is about the first syllable.
        val gohanItems = listOf(
            ja("g-target", "目標", "고한", "목표"),
            ja("d1", "一", "반고한", "반"),
            ja("d2", "丸", "마루고한", "원"),
            ja("ok1", "パン", "판", "빵"),
            ja("ok2", "水", "미즈", "물"),
            ja("ok3", "茶", "오차", "차"),
            ja("ok4", "飯", "밥한", "밥")
        )
        val catalog = JamoWordCatalog(gohanItems)
        val g = letter("ㄱ")
        val q = assertNotNull(catalog.question(g, random = Random(0)))
        assertTrue(startsWithJamo(q.word.hangul, g))
        val distractors = q.options.filter { it.sourceId != q.word.sourceId }
        assertTrue(distractors.none { startsWithJamo(it.hangul, g) })
        assertEquals(1, q.options.count { startsWithJamo(it.hangul, g) })
    }

    @Test fun ieungQuizNeverUsesLongVowelTailAsTheTarget() {
        val catalog = JamoWordCatalog(listOf(
            ja("orange", "オレンジ", "오렌지", "오렌지"),
            ja("ice", "アイス", "아이스", "아이스"),
            ja("cookie", "クッキー", "쿠키이", "쿠키"),
            ja("vanilla", "バニラ", "바니라", "바닐라"),
            ja("syrup", "シロップ", "시롭푸", "시럽"),
            ja("napkin", "ナプキン", "나푸킨", "냅킨"),
        ))
        val ieung = letter("ㅇ")
        repeat(20) { seed ->
            val q = assertNotNull(catalog.question(ieung, random = Random(seed)))
            assertTrue(startsWithJamo(q.word.hangul, ieung),
                "ㅇ target must start with ㅇ, not merely contain a later vowel syllable: ${q.word.hangul}")
            assertNotEquals("cookie", q.word.sourceId)
            assertNotEquals("쿠키이", q.word.hangul)
        }
        assertTrue(containsJamo("쿠키이", ieung))
        assertFalse(startsWithJamo("쿠키이", ieung))
    }

    @Test fun focusedQuizTriesRemainingCandidatesInsteadOfNull() {
        // All eligible nonexample ㄱ targets are alias-blocked (their aliases ban
        // every non-ㄱ distractor), so the quiz must fall back to a study example
        // (milk, 牛乳) as last resort rather than returning null.
        fun blocked(id: String, hangul: String) =
            ja(id, "阻$hangul", hangul, "막다", aliases = listOf(hangul, "아메리카", "시나모노", "카타카나", "하나미치"))
        val candItems = listOf(
            blocked("w-blocked1", "고한별빛"),
            blocked("w-blocked2", "고한단풍"),
            blocked("w-blocked3", "고한산책"),
            ja("milk-fallback", "牛乳", "규우뉴우", "우유", aliases = listOf("규우뉴우")),
            ja("n1", "アメリカ", "아메리카", "미국"),
            ja("n2", "品物", "시나모노", "물건"),
            ja("n3", "カタカナ", "카타카나", "가타카나"),
            ja("n4", "花道", "하나미치", "꽃길")
        )
        val catalog = JamoWordCatalog(candItems)
        val q = assertNotNull(catalog.question(letter("ㄱ"), random = Random(5)))
        assertEquals("milk-fallback", q.word.sourceId)
        assertEquals(4, q.options.size)
        assertEquals(1, q.options.map { it.hangul.count { ch -> !ch.isWhitespace() } }.toSet().size)
    }

    @Test fun focusedOptionsAreRealSourceWordsWithAliasExclusion() {
        val catalog = JamoWordCatalog(items)
        val q = assertNotNull(catalog.question(letter("ㄱ"), random = Random(11)))
        q.options.forEach { assertTrue(it in catalog.words, "${it.sourceId} must be a real catalog word") }
        val distractors = q.options.filter { it.sourceId != q.word.sourceId }
        assertTrue(distractors.none { it.hangul in q.word.aliases })
        assertTrue(distractors.none { it.japanese == q.word.japanese })
        assertTrue(q.options.all { it.kind == q.word.kind })
    }

    @Test fun examplesMatchFirstSyllableAreUniqueAndIndependentOfRequestOrder() {
        val forward = JamoWordCatalog(items)
        val reverse = JamoWordCatalog(items)
        val letters = basicConsonants + basicVowels
        val expected = letters.associate { it.id to forward.examples(it) }
        letters.reversed().forEach { l ->
            assertEquals(expected.getValue(l.id), reverse.examples(l))
            assertEquals(expected.getValue(l.id).take(1), reverse.examples(l, 1))
            assertTrue(reverse.examples(l).all { startsWithJamo(it.hangul, l) })
        }
        val norms = expected.values.flatten().map { com.ghtnql.kkkeyboard.sharedcore.normalizeAnswer(it.hangul) }
        assertEquals(norms.size, norms.toSet().size)
        assertEquals(listOf("ㄱ"), letters.filter { l -> forward.examples(l).any { it.japanese == "牛乳" } }.map { it.glyph })
        assertTrue(startsWithJamo("규우뉴우", letter("ㄱ")))
        assertFalse(startsWithJamo("규우뉴우", letter("ㄴ")))
        assertFalse(startsWithJamo("규우뉴우", letter("ㅇ")))
        assertTrue(containsJamo("규우뉴우", letter("ㄴ")))
    }

    @Test fun differentJapaneseSourcesCannotDuplicateTheSameExamplePronunciation() {
        val catalog = JamoWordCatalog(listOf(
            ja("wave", "波", "나미", "파도"),
            ja("ordinary", "並み", "나미", "보통"),
            ja("cry", "泣く", "나쿠", "울다")))
        val examples = (basicConsonants + basicVowels).flatMap { catalog.examples(it) }
        assertEquals(examples.size, examples.map { it.hangul }.toSet().size)
        assertEquals(2, catalog.examples(letter("ㄴ")).size)
    }

    @Test fun unavailableEqualLengthDistractorsNeverRelaxLengthRule() {
        val sparse = JamoWordCatalog(listOf(milk) + items.filter { it.id in setOf("bread", "water", "coffee", "tea") })
        assertNull(sparse.question(letter("ㄱ"), random = Random(1)))
    }

    @Test fun standaloneQuizUsesDiverseDistractors() {
        val catalog = JamoWordCatalog(items)
        val q = assertNotNull(catalog.question(random = Random(9)))
        assertEquals(4, q.options.size)
        assertEquals(1, q.options.map { it.hangul.count { ch -> !ch.isWhitespace() } }.toSet().size)
        val norms = q.options.map { it.hangul }
        assertEquals(4, norms.toSet().size)
        q.options.forEach { assertTrue(it in catalog.words) }
    }
}
