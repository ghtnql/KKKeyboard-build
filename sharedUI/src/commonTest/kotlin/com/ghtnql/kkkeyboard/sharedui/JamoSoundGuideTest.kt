package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.*

class JamoSoundGuideTest {
    @Test fun chartCoversModernHiraganaWithAlignedColumnsAndJapaneseReadings() {
        assertTrue(hiraganaGuideRows.all { it.kana.size == 5 && it.hangul.size == 5 })
        val cells = hiraganaGuideRows.flatMap { it.kana.zip(it.hangul) }.filter { it.first.isNotEmpty() }
        assertEquals(71, cells.size)
        assertEquals(cells.size, cells.map { it.first }.toSet().size)
        val readings = cells.toMap()
        assertEquals("치", readings["ち"])
        assertEquals("쓰", readings["つ"])
        assertEquals("지", readings["ぢ"])
        assertEquals("즈", readings["づ"])
        assertEquals("오", readings["を"])
        assertEquals(listOf("や", "", "ゆ", "", "よ"), hiraganaGuideRows.single { "や" in it.kana }.kana)
        assertEquals(listOf("わ", "", "", "", "を"), hiraganaGuideRows.single { "わ" in it.kana }.kana)
    }

    @Test fun allLettersHaveLocalizedSoundGuidesAndNasalsRemainDistinct() {
        (basicConsonants + basicVowels).forEach { letter ->
            val guide = jamoSoundGuide(letter)
            assertTrue(guide.kana.any { it in 'ぁ'..'ゖ' }, letter.glyph)
            assertTrue(guide.hangul.isNotBlank() && guide.noteKo.isNotBlank() && guide.noteJa.isNotBlank() && guide.noteEn.isNotBlank())
        }
        val nasals = listOf("ㄴ" to "(n)", "ㅁ" to "(m", "ㅇ" to "(ng")
        nasals.forEach { (glyph, sound) ->
            val guide = jamoSoundGuide(basicConsonants.single { it.glyph == glyph })
            assertTrue("ん" in guide.noteEn && sound in guide.noteEn, glyph)
        }
        assertTrue("silent" in jamoSoundGuide(basicConsonants.single { it.glyph == "ㅇ" }).noteEn)
        val eu = jamoSoundGuide(basicVowels.single { it.glyph == "ㅡ" })
        assertTrue("without lip rounding" in eu.noteEn)
        assertNotEquals(eu, jamoSoundGuide(basicVowels.single { it.glyph == "ㅜ" }))
    }
}
