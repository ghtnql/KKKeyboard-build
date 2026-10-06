package com.ghtnql.kkkeyboard

import kotlin.math.max

enum class CafeDifficulty(
    val baseOrderTimeMs: Long,
    val memoryRevealMs: Long?,
) {
    RELAXED(baseOrderTimeMs = 20_000L, memoryRevealMs = null),
    NORMAL(baseOrderTimeMs = 13_000L, memoryRevealMs = null),
    RUSH(baseOrderTimeMs = 9_000L, memoryRevealMs = 2_500L),
}

data class CafeSubmission(
    val correct: Boolean,
    val completed: Boolean,
    val scoreGained: Int,
)

data class CafeTickResult(
    val timedOut: Boolean,
    val completed: Boolean,
)

class CafeGame(
    val mode: PracticeMode,
    private val items: List<LearningItem>,
    private val startedAtMs: Long,
    private val orderCount: Int = 5,
    val difficulty: CafeDifficulty = CafeDifficulty.NORMAL,
) {
    init {
        require(items.isNotEmpty()) { "Cafe requires at least one item" }
        require(orderCount > 0) { "orderCount must be positive" }
    }

    var currentIndex: Int = 0
        private set
    var score: Int = 0
        private set
    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set
    var errorCount: Int = 0
        private set
    var totalTypedCharacters: Int = 0
        private set
    var successfulOrderCount: Int = 0
        private set
    var failedOrderCount: Int = 0
        private set
    var currentOrderTotalTimeMs: Long = orderDurationMs(0)
        private set
    var currentOrderRemainingTimeMs: Long = currentOrderTotalTimeMs
        private set

    private var orderTimerRunning = false

    val isFinished: Boolean
        get() = currentIndex >= orderCount

    val currentOrder: LearningItem?
        get() = if (isFinished) null else items[currentIndex % items.size]

    val shouldHideCurrentOrder: Boolean
        get() = difficulty.memoryRevealMs?.let { revealMs ->
            orderTimerRunning && currentOrderTotalTimeMs - currentOrderRemainingTimeMs >= revealMs
        } == true

    fun beginOrder() {
        if (isFinished || orderTimerRunning) return
        currentOrderTotalTimeMs = orderDurationMs(currentIndex)
        currentOrderRemainingTimeMs = currentOrderTotalTimeMs
        orderTimerRunning = true
    }

    fun tick(deltaMs: Long): CafeTickResult? {
        if (isFinished || !orderTimerRunning) return null
        currentOrderRemainingTimeMs = (
            currentOrderRemainingTimeMs - deltaMs.coerceAtLeast(0L)
        ).coerceAtLeast(0L)
        if (currentOrderRemainingTimeMs > 0L) return null

        orderTimerRunning = false
        errorCount++
        failedOrderCount++
        combo = 0
        advanceOrder()
        return CafeTickResult(timedOut = true, completed = isFinished)
    }

    fun submit(rawAnswer: String): CafeSubmission {
        val order = currentOrder ?: return CafeSubmission(
            correct = false,
            completed = true,
            scoreGained = 0,
        )
        val answer = LearningContent.normalizeAnswer(rawAnswer)
        if (answer.isEmpty()) {
            return CafeSubmission(correct = false, completed = false, scoreGained = 0)
        }

        orderTimerRunning = false
        totalTypedCharacters += answer.length
        val correct = LearningContent.matchesAnswer(order, mode, answer)

        if (!correct) {
            errorCount += PracticeSession.characterDistance(answer, order.acceptedAnswers.first())
            combo = 0
            failedOrderCount++
            advanceOrder()
            return CafeSubmission(correct = false, completed = isFinished, scoreGained = 0)
        }

        combo++
        maxCombo = max(maxCombo, combo)
        val remainingTimeBonus = (currentOrderRemainingTimeMs / 1_000L).toInt()
        val scoreGained = answer.length * 10 + (combo - 1) * 2 + remainingTimeBonus
        score += scoreGained
        successfulOrderCount++
        advanceOrder()
        return CafeSubmission(correct = true, completed = isFinished, scoreGained = scoreGained)
    }

    fun result(finishedAtMs: Long): TypingSessionResult {
        val duration = max(1L, finishedAtMs - startedAtMs)
        val accurateCharacters = max(0, totalTypedCharacters - errorCount)
        return TypingSessionResult(
            modeId = "cafe_${difficulty.name.lowercase()}_${mode.persistedValue}",
            totalExpectedCharacters = (0 until orderCount).sumOf {
                items[it % items.size].acceptedAnswers.first().length
            },
            totalTypedCharacters = totalTypedCharacters,
            errorCount = errorCount,
            accuracy = if (totalTypedCharacters == 0) 0 else accurateCharacters * 100 / totalTypedCharacters,
            durationMs = duration,
            cpm = (accurateCharacters * 60_000L / duration).toInt(),
            maxCombo = maxCombo,
            score = score,
            completed = isFinished,
        )
    }

    private fun advanceOrder() {
        currentIndex++
        if (isFinished) {
            currentOrderTotalTimeMs = 0L
            currentOrderRemainingTimeMs = 0L
        } else {
            currentOrderTotalTimeMs = orderDurationMs(currentIndex)
            currentOrderRemainingTimeMs = currentOrderTotalTimeMs
        }
    }

    private fun orderDurationMs(completedOrders: Int): Long {
        val durationPercent = (100 - completedOrders * 15).coerceAtLeast(55)
        return difficulty.baseOrderTimeMs * durationPercent / 100L
    }
}
