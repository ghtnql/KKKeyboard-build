package com.ghtnql.kkkeyboard

import kotlin.math.max

data class TypingSessionResult(
    val modeId: String,
    val totalExpectedCharacters: Int,
    val totalTypedCharacters: Int,
    val errorCount: Int,
    val accuracy: Int,
    val durationMs: Long,
    val cpm: Int,
    val maxCombo: Int,
    val score: Int,
    val completed: Boolean,
)

data class PracticeAttempt(val correct: Boolean, val completed: Boolean)

enum class PracticeStyle {
    BASIC,
    SHORT_SENTENCE,
}

class PracticeSession(
    val mode: PracticeMode,
    val items: List<LearningItem>,
    private val startedAtMs: Long,
    val style: PracticeStyle = PracticeStyle.BASIC,
) {
    init { require(items.isNotEmpty()) }

    var currentIndex: Int = 0
        private set
    var errorCount: Int = 0
        private set
    var totalTypedCharacters: Int = 0
        private set
    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set
    var score: Int = 0
        private set

    val currentItem: LearningItem?
        get() = items.getOrNull(currentIndex)

    fun submit(rawAnswer: String): PracticeAttempt {
        val item = currentItem ?: return PracticeAttempt(correct = false, completed = true)
        val answer = LearningContent.normalizeAnswer(rawAnswer)
        if (style == PracticeStyle.SHORT_SENTENCE && answer.isBlank()) {
            return PracticeAttempt(correct = false, completed = false)
        }
        totalTypedCharacters += answer.length
        val correct = LearningContent.matchesAnswer(item, mode, answer, allowPronunciation = style == PracticeStyle.BASIC)
        if (correct) {
            combo++
            maxCombo = max(maxCombo, combo)
            score += answer.length * 10 + (combo - 1) * 2
            currentIndex++
        } else {
            errorCount += characterDistance(answer, item.acceptedAnswers.first())
            combo = 0
            if (style == PracticeStyle.SHORT_SENTENCE) currentIndex++
        }
        return PracticeAttempt(correct, currentIndex == items.size)
    }

    fun result(finishedAtMs: Long): TypingSessionResult {
        val duration = max(1L, finishedAtMs - startedAtMs)
        val accurateCharacters = max(0, totalTypedCharacters - errorCount)
        return TypingSessionResult(
            modeId = if (style == PracticeStyle.SHORT_SENTENCE) {
                "sentence_${mode.persistedValue}"
            } else {
                mode.persistedValue
            },
            totalExpectedCharacters = items.sumOf { it.acceptedAnswers.first().length },
            totalTypedCharacters = totalTypedCharacters,
            errorCount = errorCount,
            accuracy = if (totalTypedCharacters == 0) 0 else accurateCharacters * 100 / totalTypedCharacters,
            durationMs = duration,
            cpm = (accurateCharacters * 60_000L / duration).toInt(),
            maxCombo = maxCombo,
            score = score,
            completed = currentIndex == items.size,
        )
    }

    companion object {
        internal fun characterDistance(first: String, second: String): Int {
            if (first.isEmpty()) return second.length.coerceAtLeast(1)
            if (second.isEmpty()) return first.length.coerceAtLeast(1)
            val previous = IntArray(second.length + 1) { it }
            for (i in first.indices) {
                var diagonal = previous[0]
                previous[0] = i + 1
                for (j in second.indices) {
                    val above = previous[j + 1]
                    previous[j + 1] = minOf(
                        above + 1,
                        previous[j] + 1,
                        diagonal + if (first[i] == second[j]) 0 else 1,
                    )
                    diagonal = above
                }
            }
            return previous[second.length].coerceAtLeast(1)
        }
    }
}
