package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningProgressTest {
    @Test fun keepsBestAccuracyAndAccumulatesProgress() {
        val context = RuntimeEnvironment.getApplication()
        LearningProgressStore.recordPractice(context, result(80, 100))
        LearningProgressStore.recordPractice(context, result(70, 50))

        val progress = LearningProgressStore.read(context)
        assertEquals(2, progress.totalSessions)
        assertEquals(80, progress.bestAccuracy)
        assertEquals(55, progress.totalXp)
    }

    private fun result(accuracy: Int, score: Int) = TypingSessionResult(
        "test", 5, 5, 0, accuracy, 1_000, 300, 1, score, true,
    )
}
