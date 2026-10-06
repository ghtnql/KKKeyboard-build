package com.ghtnql.kkkeyboard.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandidateInputTokenBufferTest {
    @Test
    fun tracksHangulCommitsAndComposingText() {
        val buffer = CandidateInputTokenBuffer()
        buffer.applyCommitted("아리")

        assertEquals("아리가", buffer.current("가"))
        assertTrue(buffer.hasCommittedToken())
    }

    @Test
    fun nonHangulCommitClearsTheTokenBoundary() {
        val buffer = CandidateInputTokenBuffer()
        buffer.applyCommitted("아리")
        buffer.applyCommitted(" ")

        assertEquals("", buffer.current(""))
        assertFalse(buffer.hasCommittedToken())
    }

    @Test
    fun removesOneCommittedHangulCharacter() {
        val buffer = CandidateInputTokenBuffer()
        buffer.applyCommitted("아리")
        buffer.removeLastCommittedCharacter()

        assertEquals("아", buffer.current(""))
    }

    @Test
    fun boundsLookupBeforeBuildingTheCombinedString() {
        val buffer = CandidateInputTokenBuffer()
        buffer.applyCommitted("가나다")

        assertNull(buffer.currentForLookup("라", 3))
        assertEquals("가나다라", buffer.currentForLookup("라", 4))
        assertEquals("가나다라", buffer.current("라"))
    }

    @Test
    fun replacementAndClearControlOnlyTheCommittedPrefix() {
        val buffer = CandidateInputTokenBuffer()
        buffer.applyCommitted("오하요")
        buffer.replaceCurrent("오하요 ")

        assertEquals("오하요 고", buffer.current("고"))
        buffer.clear()
        assertEquals("고", buffer.current("고"))
    }
}
