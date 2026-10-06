package com.ghtnql.kkkeyboard

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class JapanesePredictionTest {
    @Test
    fun sharedPredictionFixtures() {
        val json = requireNotNull(javaClass.getResourceAsStream("/ja_predictions.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val fixtures = JSONArray(json)
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            val id = fixture.getString("id")
            val input = fixture.getString("input")
            val candidates = JapaneseTransliterator.suggestionsExact(input)
            assertTrue(id, candidates.size <= JapaneseDictionary.MAX_CANDIDATES)
            assertEquals(id, candidates.distinct(), candidates)
            if (fixture.optBoolean("empty")) assertTrue(id, candidates.isEmpty())
            if (fixture.has("first")) assertEquals(id, fixture.getString("first"), candidates.firstOrNull())
            fixture.optJSONArray("contains")?.let { expected ->
                for (i in 0 until expected.length()) assertTrue("$id: ${expected.getString(i)}", expected.getString(i) in candidates)
            }
            fixture.optJSONArray("forbidden")?.let { forbidden ->
                for (i in 0 until forbidden.length()) assertFalse(id, forbidden.getString(i) in candidates)
            }
            if (fixture.has("automaticFirst")) assertEquals(
                id, fixture.getString("automaticFirst"), JapaneseTransliterator.candidatesExact(input).firstOrNull(),
            )
            if (fixture.optBoolean("automaticEmpty")) assertTrue(id, JapaneseTransliterator.candidatesExact(input).isEmpty())
        }
    }

    @Test
    fun everyBundledAliasFindsItsVocabularyEntry() {
        val dictionary = JapaneseDictionary.bundled()
        assertEquals(300, dictionary.entries.size)
        assertEquals(200, dictionary.entries.count { it.id.startsWith("dict_ja_") })
        dictionary.entries.forEach { entry ->
            entry.aliases.forEach { alias ->
                assertTrue("${entry.id}: $alias", JapaneseTransliterator.candidatesExact(alias).containsAll(entry.surfaces))
            }
        }
    }

    @Test
    fun draftMergeKeepsLegacyCandidatesFirstAndAcceptsSingleSyllables() {
        val dictionary = JapaneseDictionary.bundled()
        assertEquals(listOf("すし", "寿司"), dictionary.exact("스시"))
        assertEquals(listOf("げんき", "元気", "元気？", "げんき？"), dictionary.exact("겐키"))
        assertEquals(listOf("うん"), dictionary.exact("운"))
        assertEquals(listOf("えっ"), dictionary.exact("엣"))
        assertEquals(listOf("パン", "ぱん"), dictionary.exact("판"))
        assertEquals(null, dictionary.longestMatch("운가", 0))
    }

    @Test
    fun priorityIsStableRegardlessOfFileOrder() {
        val dictionary = JapaneseDictionary.parse("""{
            "version": 1, "entries": [
                {"id":"b","priority":20,"aliases":["아리타"],"surfaces":["B"]},
                {"id":"c","priority":10,"aliases":["아리마"],"surfaces":["C"]},
                {"id":"a","priority":10,"aliases":["아리가"],"surfaces":["A"]}
            ]
        }""")
        assertEquals(listOf("A", "C", "B"), dictionary.completions("아리"))
    }

    @Test
    fun predictionDoesNotChangeReadingOrAutomaticCommit() {
        val word = JapaneseWordComposer()
        "ㅇㅗㅎㅏ".forEach { word.input(it) }
        assertEquals("おはよう", word.suggestions().first())
        assertEquals("오하", word.text)
        assertEquals("おは", word.candidates().first())
        word.input('ㅇ')
        assertEquals("おはよう", word.suggestions().first())
        word.input('ㅛ')
        assertEquals("おはよう", word.candidates().first())
        word.backspace()
        assertEquals("오항", word.text)
        assertEquals("おはよう", word.suggestions().first())
    }

    @Test
    fun keyboardSelectionCanCommitEveryAutomaticCandidate() {
        listOf("아리", "오항", "오하요", "아리가토나", "니혼칸코쿠", "가나다").forEach { input ->
            assertTrue(input, JapaneseTransliterator.candidatesExact(input).first() in JapaneseTransliterator.suggestionsExact(input))
        }
    }
}
