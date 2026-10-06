package com.ghtnql.kkkeyboard

import kotlin.math.max

data class RainGameConfig(
    val durationMs: Long = 60_000L,
    val spawnIntervalMs: Long = 2_400L,
    val fallPerSecond: Float = 0.115f,
    val startingLives: Int = 3,
    val scorePerCharacter: Int = 10,
    val comboStep: Int = 2,
)

data class RainTarget(
    val instanceId: Long,
    val item: LearningItem,
    val x: Float,
    val y: Float,
)

data class RainTickResult(val missedTargetIds: List<Long> = emptyList())

data class RainSubmission(
    val correct: Boolean,
    val removedTargetId: Long? = null,
    val scoreGained: Int = 0,
)

class RainGame(
    val mode: PracticeMode,
    private val items: List<LearningItem>,
    private val startedAtMs: Long,
    private val config: RainGameConfig = RainGameConfig(),
    private val randomFloat: () -> Float = { Math.random().toFloat() },
) {
    init { require(items.isNotEmpty()) }

    private val mutableTargets = mutableListOf<RainTarget>()
    private var spawnAccumulatorMs = config.spawnIntervalMs
    private var nextItem = 0
    private var nextInstanceId = 1L
    private var elapsedMs = 0L
    private var correctCharacters = 0

    val targets: List<RainTarget> get() = mutableTargets
    var lives: Int = config.startingLives
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
    var isFinished: Boolean = false
        private set

    val remainingMs: Long
        get() = (config.durationMs - elapsedMs).coerceAtLeast(0L)

    fun tick(deltaMs: Long): RainTickResult {
        if (isFinished) return RainTickResult()
        val safeDelta = deltaMs.coerceIn(0L, 250L)
        elapsedMs += safeDelta
        val distance = config.fallPerSecond * safeDelta / 1000f
        val moved = mutableTargets.map { it.copy(y = it.y + distance) }
        val missedTargetIds = moved.filter { it.y >= 1f }.map(RainTarget::instanceId)
        val missed = missedTargetIds.size
        mutableTargets.clear()
        mutableTargets += moved.filter { it.y < 1f }
        if (missed > 0) {
            lives = max(0, lives - missed)
            combo = 0
        }

        spawnAccumulatorMs += safeDelta
        val maxConcurrent = (1 + elapsedMs / 20_000L).toInt().coerceAtMost(3)
        if (spawnAccumulatorMs >= config.spawnIntervalMs && mutableTargets.size < maxConcurrent) {
            spawnAccumulatorMs -= config.spawnIntervalMs
            spawn()
        }
        if (lives == 0 || elapsedMs >= config.durationMs) isFinished = true
        return RainTickResult(missedTargetIds)
    }

    fun submit(rawAnswer: String): RainSubmission {
        if (isFinished) return RainSubmission(false)
        val answer = LearningContent.normalizeAnswer(rawAnswer)
        if (answer.isEmpty()) return RainSubmission(false)
        totalTypedCharacters += answer.length
        val target = mutableTargets.filter { target ->
            LearningContent.matchesAnswer(target.item, mode, answer)
        }.maxByOrNull(RainTarget::y)
        if (target == null) {
            val expected = mutableTargets.maxByOrNull(RainTarget::y)?.item?.acceptedAnswers?.first().orEmpty()
            errorCount += PracticeSession.characterDistance(answer, expected)
            combo = 0
            return RainSubmission(false)
        }
        mutableTargets.remove(target)
        combo++
        maxCombo = max(maxCombo, combo)
        correctCharacters += answer.length
        val scoreGained = answer.length * config.scorePerCharacter + (combo - 1) * config.comboStep
        score += scoreGained
        return RainSubmission(true, target.instanceId, scoreGained)
    }

    fun finish() { isFinished = true }

    fun result(finishedAtMs: Long): TypingSessionResult {
        val duration = max(1L, elapsedMs.takeIf { it > 0L } ?: (finishedAtMs - startedAtMs))
        return TypingSessionResult(
            modeId = "rain_${mode.persistedValue}",
            totalExpectedCharacters = correctCharacters,
            totalTypedCharacters = totalTypedCharacters,
            errorCount = errorCount,
            accuracy = if (totalTypedCharacters == 0) 0 else max(0, totalTypedCharacters - errorCount) * 100 / totalTypedCharacters,
            durationMs = duration,
            cpm = (correctCharacters * 60_000L / duration).toInt(),
            maxCombo = maxCombo,
            score = score,
            completed = true,
        )
    }

    private fun spawn() {
        mutableTargets += RainTarget(
            instanceId = nextInstanceId++,
            item = items[nextItem++ % items.size],
            x = randomFloat().coerceIn(0.05f, 0.85f),
            y = 0f,
        )
    }
}
