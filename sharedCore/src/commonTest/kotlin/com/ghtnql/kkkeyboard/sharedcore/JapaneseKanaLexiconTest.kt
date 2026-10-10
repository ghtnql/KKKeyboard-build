package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JapaneseKanaLexiconTest {
    // Sorted by reading (Unicode order): あした < いきます < うみ < はし < びょう < びょういん.
    private val tsv = listOf(
        "あした\t明日\t1000",
        "あした\t葦田\t2000",
        "いきます\t行きます\t1000",
        "いきます\t活きます\t3000",
        "うみ\t海\t1000",
        "うみ\t膿\t2000",
        "はし\t橋\t1000",
        "はし\t箸\t1500",
        "はし\t端\t2000",
        "びょう\t病\t1000",
        "びょういん\t病院\t1000",
        "びょういん\t美容院\t2500"
    ).joinToString("\n") + "\n"

    private val lexicon = JapaneseKanaLexicon(tsv)

    @Test fun exactLookupRetainsSourceCostOrder() {
        assertEquals(listOf("橋", "箸", "端"), lexicon.candidates("はし"))
    }

    @Test fun exactWordsPrecedeSegmentedResults() {
        val results = lexicon.candidates("びょういん")
        assertEquals(listOf("病院", "美容院"), results.take(2))
    }

    @Test fun sentenceConversionPreservesParticles() {
        val results = lexicon.candidates("あしたびょういんにいきます")
        assertTrue(results.isNotEmpty())
        assertEquals("明日病院に行きます", results.first())
    }

    @Test fun homophonesKeepBothCandidates() {
        val results = lexicon.candidates("うみ")
        assertEquals(listOf("海", "膿"), results)
    }

    @Test fun unknownKanaNeverEmitsUnchangedReading() {
        val results = lexicon.candidates("ぬねの")
        assertTrue(results.isEmpty())
    }

    @Test fun partiallyKnownSentenceDoesNotManufactureConversion() {
        assertTrue(lexicon.candidates("あしたぬねの").isEmpty())
    }

    @Test fun unrelatedNounSplittingLosesToWholeWord() {
        // びょう (disease) + いん split must not outrank びょういん (hospital).
        val results = lexicon.candidates("びょういん")
        assertEquals("病院", results.first())
    }

    @Test fun candidateCountBoundedAtEight() {
        val rows = (0 until 9).map { "あああ\t候$it\t${1000 + it}" }.joinToString("\n") + "\n"
        val lex = JapaneseKanaLexicon(rows)
        val results = lex.candidates("あああ")
        assertEquals(8, results.size)
        assertTrue(results.none { it == "あああ" })
    }

    @Test fun segmentedResultsBoundedAtEight() {
        val results = lexicon.candidates("はしはしはしはしはしはし")
        assertTrue(results.size <= 8)
        assertEquals(results.size, results.distinct().size)
    }

    @Test fun rejectsInvalidReadings() {
        assertTrue(lexicon.candidates("").isEmpty())
        assertTrue(lexicon.candidates("明日").isEmpty())
        assertTrue(lexicon.candidates("abc").isEmpty())
        assertTrue(lexicon.candidates("あした123").isEmpty())
        assertTrue(lexicon.candidates("あした ").isEmpty())
    }

    @Test fun rejectsOver128CharReading() {
        val long = "あ".repeat(129)
        assertTrue(lexicon.candidates(long).isEmpty())
    }

    @Test fun emptyLexiconYieldsNoCandidates() {
        assertTrue(JapaneseKanaLexicon("").candidates("はし").isEmpty())
    }
    private fun fixture(vararg rows: String): JapaneseKanaLexicon =
        JapaneseKanaLexicon(rows.sortedBy { it.substringBefore('\t') }.joinToString("\n", postfix = "\n"))

    @Test fun grammaticalParticlesBeatLowCostNouns() {
        val lex = fixture(
            "あした\t明日\t1000", "びょういん\t病院\t1000",
            "に\t荷\t1", "に\tに\t10000", "は\t歯\t1",
            "を\t尾\t1", "です\t出巣\t1", "ます\t鱒\t1",
            "いき\t行き\t1000"
        )
        assertEquals("明日病院に行きます", lex.candidates("あしたびょういんにいきます").first())
        assertEquals("病院は明日です", lex.candidates("びょういんはあしたです").first())
        assertEquals("病院を", lex.candidates("びょういんを").first())
    }

    @Test fun pronouncedParticlesGetFunctionalSpellingAfterDictionaryWords() {
        val lex = fixture("わたし\t私\t1000", "ほん\t本\t1000", "よみ\t読み\t1000",
            "がっこ\t学校\t1000", "いき\t行き\t1000")
        assertEquals("私は本を読みます", lex.candidates("わたしわほんおよみます").first())
        assertEquals("学校へ行きます", lex.candidates("がっこえいきます").first())
        assertTrue(lex.candidates("わおえ").isEmpty())
    }

    @Test fun longVowelPronunciationUsesShortenedDictionaryAlias() {
        val lex = fixture("びょいん\t病院\t1000", "きょ\t今日\t1000", "がっこ\t学校\t1000")
        assertEquals("病院", lex.candidates("びょおいん").first())
        assertEquals("今日", lex.candidates("きょお").first())
        assertEquals("学校", lex.candidates("がっこ").first())
        assertTrue(lex.candidates("がこ").isEmpty())
        assertTrue(lex.candidates("びょおいんぬね").isEmpty())
    }

    @Test fun rawDictionaryKeyWinsOverLongVowelFallback() {
        val lex = fixture("おば\t叔母\t1000", "おばあ\tお婆\t1000")
        assertEquals(listOf("お婆"), lex.candidates("おばあ"))
    }

    @Test fun unchangedKanaDictionaryRowsAreUsableButNotCandidatesAlone() {
        val lex = fixture("しずか\tしずか\t0", "うみ\t海\t1000")
        assertTrue(lex.candidates("しずか").isEmpty())
        assertEquals("しずか海", lex.candidates("しずかうみ").first())
    }

    @Test fun grammaticalVerbEndingsBeatNounHomophones() {
        val lex = fixture("よやく\t予約\t1000", "へんこ\t変更\t1000",
            "かくにん\t確認\t1000", "し\t死\t1", "たい\t鯛\t1")
        assertEquals("予約を変更したいです", lex.candidates("よやくおへんこおしたいです").first())
        assertEquals("確認します", lex.candidates("かくにんします").first())
    }

    @Test fun singleKanaNounsCannotConvertUnknownSegments() {
        val lex = fixture("が\t我\t0", "な\t無\t0", "だ\t打\t0")
        assertTrue(lex.candidates("がなだ").isEmpty())
        assertEquals(listOf("無"), lex.candidates("な"))
    }

    @Test fun exactCandidatesNeverIncludeSegmentedGuesses() {
        assertEquals(listOf("橋", "箸", "端"), lexicon.exactCandidates("はし"))
        assertTrue(lexicon.exactCandidates("あしたびょういんにいきます").isEmpty())
        assertEquals("明日病院に行きます", lexicon.candidates("あしたびょういんにいきます").first())
        assertTrue(lexicon.exactCandidates("abc").isEmpty())
        assertTrue(lexicon.exactCandidates("あ".repeat(129)).isEmpty())
    }

    @Test fun knownGhanaSegmentationRemainsAvailableForManualSelection() {
        val lex = fixture("がな\tガーナ\t1000", "だ\tだ\t0")
        assertTrue(lex.exactCandidates("がなだ").isEmpty())
        assertEquals(listOf("ガーナだ"), lex.candidates("がなだ"))
    }

    @Test fun exactCandidatesRetainLongVowelFallbackAndExcludeUnchangedKana() {
        val lex = fixture("きょ\t今日\t1000", "ます\tます\t0")
        assertEquals(listOf("今日"), lex.exactCandidates("きょお"))
        assertTrue(lex.exactCandidates("ます").isEmpty())
    }

    @Test fun convertedWordOptionsBeatCheapUnchangedStemsInSentenceBeam() {
        val lex = fixture("あした\t明日\t3087", "あした\tあした\t4339",
            "びょいん\t病院\t4058", "びょいん\t病因\t6833",
            "いき\tいき\t0", "いき\t行き\t8", "に\tに\t0", "ます\tます\t16")
        assertEquals("明日病院に行きます", lex.candidates("あしたびょいんにいきます").first())
    }

    @Test fun wholeLexicalTokensOutrankCheapUnrelatedNounSplits() {
        val lex = fixture("やく\t約\t0", "そく\t即\t1960", "やくそく\tお約束\t3246",
            "やくそく\t約束\t3986", "じかん\t時間\t1", "かく\t各\t0", "にん\t人\t649",
            "かくにん\t確認\t2547")
        assertTrue("約束の時間を確認します" in lex.candidates("やくそくのじかんおかくにんします"))
        assertEquals("お約束の時間を確認します", lex.candidates("やくそくのじかんおかくにんします").first())
    }

}
