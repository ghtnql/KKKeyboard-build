package com.ghtnql.kkkeyboard.sharedcore

/** One practice prompt. The first accepted answer is used for hints and error distance. */
data class PracticeQuestion(
    val expectedAnswer: String,
    val acceptedAnswers: List<String> = listOf(expectedAnswer),
    val japanesePronunciation: Boolean = false,
)

data class PracticeResult(
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

/** Compose canonical Hangul syllables used by learning answers. */
fun normalizeAnswer(value: String): String {
    val trimmed = value.trim()
    val result = StringBuilder(trimmed.length)
    var index = 0
    while (index < trimmed.length) {
        val current = trimmed[index].code
        val next = trimmed.getOrNull(index + 1)?.code
        if (current in 0x1100..0x1112 && next != null && next in 0x1161..0x1175) {
            var syllable = 0xAC00 + (current - 0x1100) * 21 * 28 + (next - 0x1161) * 28
            index += 2
            val tail = trimmed.getOrNull(index)?.code
            if (tail != null && tail in 0x11A8..0x11C2) {
                syllable += tail - 0x11A7
                index++
            }
            result.append(syllable.toChar())
            continue
        }
        if (current in 0xAC00..0xD7A3 && (current - 0xAC00) % 28 == 0 && next != null && next in 0x11A8..0x11C2) {
            result.append((current + next - 0x11A7).toChar())
            index += 2
            continue
        }
        result.append(trimmed[index])
        index++
    }
    return result.toString()
}

fun characterDistance(first: String, second: String): Int {
    if (first.isEmpty()) return second.length.coerceAtLeast(1)
    if (second.isEmpty()) return first.length.coerceAtLeast(1)
    val previous = IntArray(second.length + 1) { it }
    for (i in first.indices) {
        var diagonal = previous[0]
        previous[0] = i + 1
        for (j in second.indices) {
            val above = previous[j + 1]
            previous[j + 1] = minOf(above + 1, previous[j] + 1, diagonal + if (first[i] == second[j]) 0 else 1)
            diagonal = above
        }
    }
    return previous[second.length].coerceAtLeast(1)
}

fun practiceResult(
    modeId: String, expected: Int, typed: Int, errors: Int, durationMs: Long,
    maxCombo: Int, score: Int, completed: Boolean, cpmCharacters: Int = (typed - errors).coerceAtLeast(0),
): PracticeResult {
    val duration = durationMs.coerceAtLeast(1)
    return PracticeResult(modeId, expected, typed, errors,
        if (typed == 0) 0 else ((typed - errors).coerceAtLeast(0) * 100 / typed),
        duration, (cpmCharacters * 60_000L / duration).toInt(), maxCombo, score, completed)
}

class PracticeEngine(
    val questions: List<PracticeQuestion>,
    private val modeId: String,
    private val advanceOnError: Boolean,
) {
    init { require(questions.isNotEmpty()) }

    private val pronunciationMatcher = JapanesePronunciationMatcher()

    var currentIndex = 0; private set
    var errorCount = 0; private set
    var typedCharacters = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    var score = 0; private set

    fun submit(rawAnswer: String): Boolean {
        val question = questions.getOrNull(currentIndex) ?: return false
        val answer = normalizeAnswer(rawAnswer)
        if (answer.isBlank()) return false
        typedCharacters += answer.length
        val correct = question.acceptedAnswers.any {
            val expected = normalizeAnswer(it)
            expected == answer || (question.japanesePronunciation && pronunciationMatcher.matches(answer, expected))
        }
        if (correct) {
            combo++
            maxCombo = maxOf(maxCombo, combo)
            score += answer.length * 10 + (combo - 1) * 2
            currentIndex++
        } else {
            errorCount += characterDistance(answer, question.expectedAnswer)
            combo = 0
            if (advanceOnError) currentIndex++
        }
        return correct
    }

    fun result(durationMs: Long): PracticeResult = practiceResult(
        modeId, questions.sumOf { it.expectedAnswer.length }, typedCharacters,
        errorCount, durationMs, maxCombo, score, currentIndex == questions.size,
    )
}
