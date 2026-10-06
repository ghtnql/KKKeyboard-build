package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseDictionaryLeniencyTest {
    @Test fun exactAcceptsOnsetVariantsAndOmittedLongVowels() {
        val dictionary = dictionary(
            row("tokyo", 10, "토우쿄우", "東京"),
            row("milk", 20, "규우뉴우", "牛乳"),
        )

        for (input in listOf("토우쿄우", "도우쿄우", "토쿄", "도쿄", "도교")) {
            assertEquals(input, listOf("東京"), dictionary.exact(input))
        }
        assertEquals(listOf("牛乳"), dictionary.exact("규뉴"))
    }

    @Test fun literalExactEntriesPrecedeHigherPriorityTolerantCollisions() {
        val dictionary = dictionary(
            row("tolerant", 1, "토우쿄우", "TOLERANT"),
            row("literal_b", 20, "도쿄", "LITERAL_B"),
            row("literal_a", 10, "도쿄", "LITERAL_A"),
        )

        assertEquals(listOf("LITERAL_A", "LITERAL_B"), dictionary.exact("도쿄"))
    }

    @Test fun completionAcceptsShortenedOnsetsAndComposingLongVowel() {
        val dictionary = dictionary(
            row("camera", 10, "토우쿄우카메라", "東京カメラ"),
            row("milk", 20, "규우뉴우", "牛乳"),
        )

        assertEquals(listOf("東京カメラ"), dictionary.completions("도쿄카"))
        assertEquals(listOf("東京カメラ"), dictionary.completions("도교가"))
        assertEquals(listOf("牛乳"), dictionary.completions("규늉"))
    }

    @Test fun composingOnsetVariantsKeepPredictionsWithoutChangingExactCodas() {
        val dict = dictionary(row("camera", 10, "가토쿄카메라", "東京カメラ"),
            row("next", 20, "가타테", "fixture"))
        assertTrue(dict.completions("가돜").contains("東京カメラ"))
        assertTrue(dict.completions("가탙").contains("fixture"))
        assertTrue(dict.exact("가돜").isEmpty())
        assertTrue(dict.exact("가탙").isEmpty())
    }

    @Test fun literalCompletionEntriesPrecedeHigherPriorityTolerantCollisions() {
        val dictionary = dictionary(
            row("tolerant", 1, "토우쿄우카메라", "TOLERANT"),
            row("literal_b", 20, "도쿄카메라", "LITERAL_B"),
            row("literal_a", 10, "도쿄카페", "LITERAL_A"),
        )

        assertEquals(listOf("LITERAL_A", "LITERAL_B", "TOLERANT"), dictionary.completions("도쿄카"))
    }

    @Test fun longestMatchUsesOriginalInputLengthForExpandedAndOmittedReadings() {
        val shortAlias = dictionary(row("tokyo", 10, "도쿄", "東京"))
        val explicitAlias = dictionary(row("tokyo", 10, "토우쿄우", "東京"))

        assertEquals(JapaneseDictionary.Match(4, listOf("東京")), shortAlias.longestMatch("앞토우쿄우뒤", 1))
        assertEquals(JapaneseDictionary.Match(2, listOf("東京")), explicitAlias.longestMatch("앞도쿄뒤", 1))
    }

    @Test fun differentVowelsAndCodasAreNotErased() {
        val dictionary = dictionary(row("love", 10, "아이", "愛"))

        assertEquals(listOf("愛"), dictionary.exact("아이"))
        assertTrue(dictionary.exact("아").isEmpty())
        assertTrue(dictionary.exact("아인").isEmpty())
        assertTrue(dictionary.exact("아 이").isEmpty())
    }

    private fun dictionary(vararg rows: String) = JapaneseDictionary.parse(
        """{"version":1,"entries":[${rows.joinToString(",")}]}""",
    )

    private fun row(id: String, priority: Int, alias: String, surface: String) =
        """{"id":"$id","priority":$priority,"aliases":["$alias"],"surfaces":["$surface"]}"""
}
