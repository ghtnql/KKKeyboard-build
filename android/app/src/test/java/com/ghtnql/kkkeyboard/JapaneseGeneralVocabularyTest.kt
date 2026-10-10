package com.ghtnql.kkkeyboard

import com.ghtnql.kkkeyboard.sharedcore.JapaneseKanaLexicon
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JapaneseGeneralVocabularyTest {
    private fun lexicon() = JapaneseKanaLexicon(requireNotNull(javaClass.getResourceAsStream("/ja_lexicon.tsv"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() })

    @Test fun everyJouyouCharacterHasASelectableConversion() {
        val lexicon = lexicon()
        val fixture = JSONObject(requireNotNull(javaClass.getResourceAsStream("/ja_joyo_coverage.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() })
        val samples = fixture.getJSONObject("samples")
        assertEquals(2136, samples.length())
        samples.keys().forEach { kanji ->
            val pair = samples.getJSONArray(kanji)
            assertTrue("$kanji: ${pair.getString(0)} -> ${pair.getString(1)}",
                pair.getString(1) in lexicon.candidates(pair.getString(0)))
        }
    }

    @Test fun dailyVocabularyAndSentencesIndependentOfGames() {
        val rows = org.json.JSONArray(requireNotNull(javaClass.getResourceAsStream("/ja_daily_vocabulary.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() })
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val input = row.getString("input")
            val surface = row.getString("surface")
            val automatic = JapaneseTransliterator.candidatesExact(input)
            val suggestions = JapaneseTransliterator.suggestionsExact(input)
            println("daily $input automatic=$automatic suggestions=$suggestions")
            if (row.optBoolean("requireAutomatic", true)) assertTrue("$input -> $surface: $automatic", surface in automatic)
            assertTrue("suggestions $input -> $surface: $suggestions", surface in suggestions)
        }
    }
}
