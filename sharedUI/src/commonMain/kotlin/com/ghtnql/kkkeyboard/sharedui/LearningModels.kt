package com.ghtnql.kkkeyboard.sharedui

import com.ghtnql.kkkeyboard.sharedcore.JapanesePronunciationMatcher
import com.ghtnql.kkkeyboard.sharedcore.PracticeEngine
import com.ghtnql.kkkeyboard.sharedcore.PracticeQuestion
import com.ghtnql.kkkeyboard.sharedcore.PracticeResult
import com.ghtnql.kkkeyboard.sharedcore.characterDistance
import com.ghtnql.kkkeyboard.sharedcore.normalizeAnswer
import com.ghtnql.kkkeyboard.sharedcore.practiceResult
import kotlin.math.max

/** Plain Kotlin types keep the platform bridge usable from Android and Swift. */
enum class KeyboardStatus { UNKNOWN, DISABLED, ENABLED, ACTIVE }

enum class LayoutOrientation { PORTRAIT, LANDSCAPE }

enum class KeyboardThemeChoice(val title: String) {
    SYSTEM("시스템"), LIGHT("라이트"), DARK("다크"),
    SEOUL_DAY("서울 낮"), SEOUL_NIGHT("서울 밤"),
}

enum class LayoutHeight(val iosPoints: Int) {
    COMPACT(220), NORMAL(260), TALL(300);

    companion object {
        fun fromIosPoints(points: Int): LayoutHeight = entries.firstOrNull { it.iosPoints == points } ?: NORMAL
    }
}

data class LayoutOptions(val height: LayoutHeight, val numberRowEnabled: Boolean)

enum class KeyboardInputLayout(val title: String, val persistedValue: String) {
    CHEONJIIN("천지인", "cheonjiin"), CHEONJIIN_PLUS("천지인 플러스", "cheonjiin_plus"), QWERTY("QWERTY", "qwerty"), HANGUL_FLICK("한글 플릭", "hangul_flick");

    companion object {
        fun fromPersistedValue(value: String?): KeyboardInputLayout =
            entries.firstOrNull { it.persistedValue == value } ?: CHEONJIIN
    }
}

interface AppPlatform {
    fun canEditLongPressSymbols(): Boolean = true
    fun readInputLayout(): KeyboardInputLayout = KeyboardInputLayout.CHEONJIIN
    fun setInputLayout(layout: KeyboardInputLayout) {}
    fun readLongPressSlots(layout: KeyboardInputLayout, keyId: String): List<String> =
        com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog.slots(layout.persistedValue, keyId, null)
    fun setLongPressSlots(layout: KeyboardInputLayout, keyId: String, slots: List<String>) {}
    fun resetLongPressSlots(layout: KeyboardInputLayout, keyId: String) {}
    fun readFlickDistance(): Int = 20
    fun setFlickDistance(distance: Int) {}
    fun readCheonjiinCycleTimeout(): Int = 900
    fun setCheonjiinCycleTimeout(timeout: Int) {}
    fun readHapticFeedbackEnabled(): Boolean = true
    fun setHapticFeedbackEnabled(enabled: Boolean) {}

    val keyboardStatus: KeyboardStatus
    val keyboardTestText: String
    val learningItems: List<LearningItem>
    val learningProgress: LearningProgress
    fun refreshKeyboardStatus()
    fun openKeyboardSettings()
    fun showKeyboardPicker()
    fun onKeyboardTestTextChanged(text: String)
    fun saveLearningProgress(progress: LearningProgress)
    fun readHangulLearnedIds(): List<String> = emptyList()
    fun saveHangulLearnedIds(ids: List<String>) {}
    fun readHiraganaGuideSeen(): Boolean = false
    fun saveHiraganaGuideSeen() {}
    fun readUserPhrases(): List<UserPhraseEntry>
    fun createUserPhrase(title: String, content: String): Boolean
    fun updateUserPhrase(id: String, title: String, content: String): Boolean
    fun deleteUserPhrase(id: String): Boolean
    fun readLayoutOptions(orientation: LayoutOrientation): LayoutOptions
    fun setLayoutHeight(orientation: LayoutOrientation, height: LayoutHeight)
    fun setNumberRowEnabled(orientation: LayoutOrientation, enabled: Boolean)
    fun readAdRemovalState(): AdRemovalState = AdRemovalState()
    fun refreshAdRemoval() {}
    fun purchaseAdRemoval() {}
    fun restoreAdRemoval() {}
    fun openRefundInformation() {}
    fun openPrivacyPolicy() {}
    fun isAdPrivacyOptionsRequired(): Boolean = false
    fun showAdPrivacyOptions() {}
    fun readUiLanguage(): String = "ko"
    fun setUiLanguage(language: String) {}
    fun readKeyboardTheme(): KeyboardThemeChoice
    fun setKeyboardTheme(theme: KeyboardThemeChoice)
    fun seoulThemeUnlockRemainingMillis(): Long
    fun requestSeoulThemeAd(theme: KeyboardThemeChoice): Boolean
    fun isSeoulThemeAdPending(): Boolean
    fun seoulThemeAdMessage(): String?
    fun readThemes(): List<SharedTheme>
    fun readGameAudioSettings(): GameAudioSettings = GameAudioSettings()
    fun setGameMusicEnabled(enabled: Boolean) {}
    fun setGameEffectsEnabled(enabled: Boolean) {}
    fun startGameAudio(track: GameMusicTrack) {}
    fun stopGameAudio() {}
    fun finishGameAudio() { stopGameAudio() }
    fun playGameSound(effect: GameSoundEffect) {}
    fun prepareGameEndAd() {}
    /** False: continue immediately. True: callback once after dismissal or presentation failure. */
    fun requestGameEndAd(onFinished: () -> Unit): Boolean = false
}

data class UserPhraseEntry(val id: String, val title: String, val content: String)

data class LearningItem(
    val id: String,
    val category: String,
    val difficulty: Int,
    val sourceLanguage: String,
    val sourceText: String,
    val targetLanguage: String,
    val acceptedAnswers: List<String>,
    val meaningHint: String?,
    val enabledModes: List<String>,
    val gameTypes: List<String>,
    val japaneseText: String? = null,
    val japaneseHangulPronunciation: String? = null,
    val koreanText: String? = null,
)

/**
 * Answers accepted in word games (BASIC typing, Rain, Cafe).
 * Japanese items accept the exact displayed Japanese source (or [LearningItem.japaneseText]
 * when present) plus the approved Hangul aliases in [LearningItem.acceptedAnswers].
 * All other items accept only [LearningItem.acceptedAnswers]; the meaning hint is never
 * accepted and Korean-mode comparison is unchanged. Sentence mode does not use this:
 * it requires the exact selected [SentencePrompt.copyText].
 */
fun LearningItem.gameAcceptedAnswers(): List<String> {
    if (sourceLanguage != "ja") return acceptedAnswers
    val exact = listOfNotNull(
        sourceText.takeIf { it.isNotBlank() },
        japaneseText?.takeIf { it.isNotBlank() },
    )
    return (exact + acceptedAnswers).distinct()
}

/** A sentence is usable only when all three reviewed lines are present. */
val LearningItem.hasSentenceTriad: Boolean
    get() = !japaneseText.isNullOrBlank() && !japaneseHangulPronunciation.isNullOrBlank() && !koreanText.isNullOrBlank()

enum class SentenceTarget { JAPANESE, KOREAN }

data class SentencePrompt(val item: LearningItem, val target: SentenceTarget) {
    init { require(item.hasSentenceTriad) }
    val copyText: String = if (target == SentenceTarget.JAPANESE) item.japaneseText!! else item.koreanText!!
    val expectedAnswer: String = copyText
}

data class LearningProgress(
    val totalSessions: Int = 0,
    val bestAccuracy: Int = 0,
    val rainHighScore: Int = 0,
    val totalXp: Int = 0,
) {
    fun record(result: TypingSessionResult): LearningProgress = copy(
        totalSessions = totalSessions + 1,
        bestAccuracy = max(bestAccuracy, result.accuracy),
        rainHighScore = if (result.modeId.startsWith("rain_")) max(rainHighScore, result.score) else rainHighScore,
        totalXp = totalXp + result.score / 10 + if (result.completed && !result.modeId.startsWith("rain_")) 20 else 0,
    )
}

enum class PracticeMode(val id: String, val title: String) {
    KOREAN("ko_same_hangul", "한국어 입력"),
    JAPANESE("ja_to_hangul_pronunciation", "일본어·한글 발음"),
}

enum class PracticeStyle(val gameType: String, val title: String) {
    BASIC("typing", "기본 연습"), SENTENCE("sentence", "단문 연습"), CONVERT("convert", "변환 연습")
}

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

fun List<LearningItem>.forMode(mode: PracticeMode, gameType: String): List<LearningItem> =
    if (gameType == PracticeStyle.SENTENCE.gameType) filter { gameType in it.gameTypes && it.hasSentenceTriad }
    else filter { mode.id in it.enabledModes && gameType in it.gameTypes && it.acceptedAnswers.isNotEmpty() }

internal fun normalized(value: String): String = normalizeAnswer(value)

private val pronunciationMatcher = JapanesePronunciationMatcher()

internal fun LearningItem.usesJapanesePronunciation(mode: PracticeMode): Boolean =
    mode == PracticeMode.JAPANESE && sourceLanguage == "ja"

internal fun LearningItem.matchesGameAnswer(answer: String, mode: PracticeMode): Boolean =
    gameAcceptedAnswers().any {
        val expected = normalized(it)
        expected == answer || (usesJapanesePronunciation(mode) && pronunciationMatcher.matches(answer, expected))
    }

internal fun LearningItem.matchesGamePrefix(input: String, mode: PracticeMode): Boolean {
    val prefix = normalized(input)
    return gameAcceptedAnswers().any {
        val expected = normalized(it)
        expected.startsWith(prefix) || (usesJapanesePronunciation(mode) &&
            pronunciationMatcher.matchesPrefix(prefix, expected))
    }
}

class PracticeSession(
    val mode: PracticeMode,
    val style: PracticeStyle,
    val items: List<LearningItem>,
    randomTarget: () -> SentenceTarget = { if (kotlin.random.Random.nextBoolean()) SentenceTarget.JAPANESE else SentenceTarget.KOREAN },
) {
    init { require(items.isNotEmpty()); if (style == PracticeStyle.SENTENCE) require(items.all { it.hasSentenceTriad }) }
    private val sentencePrompts = if (style == PracticeStyle.SENTENCE) items.map { SentencePrompt(it, randomTarget()) } else emptyList()
    private val engine = PracticeEngine(
        if (style == PracticeStyle.SENTENCE) sentencePrompts.map { PracticeQuestion(it.expectedAnswer) }
        else items.map { PracticeQuestion(it.gameAcceptedAnswers().firstOrNull().orEmpty(), it.gameAcceptedAnswers(),
            japanesePronunciation = it.usesJapanesePronunciation(mode)) },
        if (style == PracticeStyle.SENTENCE) "sentence_mixed" else mode.id,
        advanceOnError = style == PracticeStyle.SENTENCE,
    )
    val currentIndex get() = engine.currentIndex
    val errorCount get() = engine.errorCount
    val typedCharacters get() = engine.typedCharacters
    val combo get() = engine.combo
    val maxCombo get() = engine.maxCombo
    val score get() = engine.score
    val currentItem get() = items.getOrNull(currentIndex)
    val currentSentencePrompt get() = sentencePrompts.getOrNull(currentIndex)
    val expectedAnswer get() = currentSentencePrompt?.expectedAnswer ?: currentItem?.gameAcceptedAnswers()?.firstOrNull().orEmpty()

    fun submit(rawAnswer: String): Boolean = engine.submit(rawAnswer)

    fun result(durationMs: Long): TypingSessionResult = engine.result(durationMs).toTypingSessionResult()
}

internal fun resultOf(
    modeId: String, expected: Int, typed: Int, errors: Int, durationMs: Long,
    maxCombo: Int, score: Int, completed: Boolean, cpmCharacters: Int = (typed - errors).coerceAtLeast(0),
): TypingSessionResult {
    return practiceResult(modeId, expected, typed, errors, durationMs, maxCombo, score, completed, cpmCharacters)
        .toTypingSessionResult()
}

private fun PracticeResult.toTypingSessionResult() = TypingSessionResult(
    modeId, totalExpectedCharacters, totalTypedCharacters, errorCount, accuracy,
    durationMs, cpm, maxCombo, score, completed,
)

data class RainTarget(val id: Long, val item: LearningItem, val x: Float, val y: Float)

class RainSession(val mode: PracticeMode, private val items: List<LearningItem>, private val randomX: () -> Float = { kotlin.random.Random.nextFloat() }) {
    init { require(items.isNotEmpty()) }
    private val mutableTargets = mutableListOf<RainTarget>()
    val targets: List<RainTarget> get() = mutableTargets.toList()
    var remainingMs = 60_000L; private set
    var lives = 3; private set
    var score = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    var errorCount = 0; private set
    var typedCharacters = 0; private set
    var finished = false; private set
    private var spawnMs = 2_400L
    private var nextItem = 0
    private var nextId = 1L
    private var correctCharacters = 0

    fun tick(deltaMs: Long) {
        if (finished) return
        val delta = deltaMs.coerceIn(0L, 250L)
        remainingMs = (remainingMs - delta).coerceAtLeast(0L)
        val moved = mutableTargets.map { it.copy(y = it.y + 0.115f * delta / 1000f) }
        val missed = moved.count { it.y >= 1f }
        mutableTargets.clear(); mutableTargets.addAll(moved.filter { it.y < 1f })
        if (missed > 0) { lives = (lives - missed).coerceAtLeast(0); combo = 0 }
        spawnMs += delta
        val maximum = (1 + (60_000L - remainingMs) / 20_000L).toInt().coerceAtMost(3)
        if (spawnMs >= 2_400L && mutableTargets.size < maximum) {
            spawnMs -= 2_400L
            mutableTargets += RainTarget(nextId++, items[nextItem++ % items.size], randomX().coerceIn(0.05f, 0.85f), 0f)
        }
        finished = lives == 0 || remainingMs == 0L
    }

    fun submit(rawAnswer: String): Boolean {
        if (finished) return false
        val answer = normalized(rawAnswer)
        if (answer.isEmpty()) return false
        typedCharacters += answer.length
        val hit = mutableTargets.filter { target -> target.item.matchesGameAnswer(answer, mode) }.maxByOrNull { it.y }
        if (hit == null) {
            errorCount += characterDistance(answer, mutableTargets.maxByOrNull { it.y }?.item?.gameAcceptedAnswers()?.first().orEmpty())
            combo = 0
            return false
        }
        mutableTargets.remove(hit); combo++; maxCombo = max(maxCombo, combo)
        correctCharacters += answer.length
        score += answer.length * 10 + (combo - 1) * 2
        return true
    }

    fun finish() { finished = true }
    fun result(): TypingSessionResult = resultOf("rain_${mode.id}", correctCharacters, typedCharacters,
        errorCount, 60_000L - remainingMs, maxCombo, score, true, correctCharacters)
}

enum class CafeDifficulty(val orderMs: Long, val revealMs: Long?, val maxPromptCharacters: Int, val maxContentDifficulty: Int) {
    RELAXED(20_000L, null, 4, 1), NORMAL(13_000L, null, 8, 2), RUSH(9_000L, 2_500L, 12, 3)
}

fun List<LearningItem>.forCafe(mode: PracticeMode, difficulty: CafeDifficulty): List<LearningItem> =
    forMode(mode, "cafe").filter { item ->
        if (item.difficulty !in 1..difficulty.maxContentDifficulty) return@filter false
        if (item.sourceText.none { !it.isWhitespace() }) return@filter false
        if (item.acceptedAnswers.isEmpty() || item.acceptedAnswers.any { answer -> answer.none { !it.isWhitespace() } }) return@filter false
        val candidates = listOfNotNull(
            item.sourceText,
            item.japaneseText,
            item.japaneseHangulPronunciation,
            item.koreanText,
        ) + item.acceptedAnswers
        candidates.filter { it.isNotBlank() }.all { text ->
            normalized(text).count { !it.isWhitespace() } <= difficulty.maxPromptCharacters
        }
    }

class CafeSession(val mode: PracticeMode, items: List<LearningItem>, val difficulty: CafeDifficulty, val orderCount: Int = 5) {
    private val items = items.forCafe(mode, difficulty)
    init { require(this.items.isNotEmpty() && orderCount > 0) }
    var currentIndex = 0; private set
    var remainingMs = orderDuration(0); private set
    var score = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    var errorCount = 0; private set
    var typedCharacters = 0; private set
    var successes = 0; private set
    val finished get() = currentIndex >= orderCount
    val currentItem get() = if (finished) null else items[currentIndex % items.size]
    val hideOrder get() = difficulty.revealMs?.let { orderDuration(currentIndex) - remainingMs >= it } == true

    fun tick(deltaMs: Long): Boolean {
        if (finished) return false
        remainingMs = (remainingMs - deltaMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        if (remainingMs > 0) return false
        errorCount++; combo = 0; advance()
        return true
    }

    fun submit(rawAnswer: String): Boolean {
        val item = currentItem ?: return false
        val answer = normalized(rawAnswer)
        if (answer.isEmpty()) return false
        typedCharacters += answer.length
        val correct = item.matchesGameAnswer(answer, mode)
        if (correct) {
            combo++; maxCombo = max(maxCombo, combo); successes++
            score += answer.length * 10 + (combo - 1) * 2 + (remainingMs / 1_000L).toInt()
        } else {
            errorCount += characterDistance(answer, item.gameAcceptedAnswers().first()); combo = 0
        }
        advance()
        return correct
    }

    private fun advance() { currentIndex++; remainingMs = if (finished) 0 else orderDuration(currentIndex) }
    private fun orderDuration(index: Int) = difficulty.orderMs * (100 - index * 15).coerceAtLeast(55) / 100L
    fun result(durationMs: Long): TypingSessionResult = resultOf("cafe_${difficulty.name.lowercase()}_${mode.id}",
        (0 until orderCount).sumOf { items[it % items.size].gameAcceptedAnswers().first().length }, typedCharacters,
        errorCount, durationMs, maxCombo, score, finished)
}
