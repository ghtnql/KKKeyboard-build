package com.ghtnql.kkkeyboard

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JapaneseCandidateLearningTest {
    private lateinit var context: Context
    private lateinit var preferencesName: String
    private lateinit var learning: JapaneseCandidateLearning

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferencesName = "candidate-learning-test-${System.nanoTime()}"
        learning = JapaneseCandidateLearning(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun unseenReadingKeepsDictionaryOrder() {
        val original = listOf("にほん", "日本", "二本")
        assertEquals(original, learning.rank("니혼", original))
    }

    @Test
    fun selectedCandidateMovesAheadOfUnselectedCandidates() {
        val original = listOf("にほん", "日本", "二本")
        learning.recordSelection("니혼", "日本")

        assertEquals(listOf("日本", "にほん", "二本"), learning.rank("니혼", original))
    }

    @Test
    fun frequencyWinsAndRecencyBreaksTies() {
        val original = listOf("橋", "箸", "端")

        learning.recordSelection("하시", "箸")
        learning.recordSelection("하시", "橋")
        assertEquals(listOf("橋", "箸", "端"), learning.rank("하시", original))

        learning.recordSelection("하시", "箸")
        assertEquals(listOf("箸", "橋", "端"), learning.rank("하시", original))
    }

    @Test
    fun learningIsScopedToExactReading() {
        learning.recordSelection("니혼", "日本")

        assertEquals(
            listOf("にっぽん", "日本"),
            learning.rank("닛폰", listOf("にっぽん", "日本")),
        )
    }
}
