package com.ghtnql.kkkeyboard.sharedui

import com.ghtnql.kkkeyboard.sharedcore.characterDistance
import com.ghtnql.kkkeyboard.sharedcore.normalizeAnswer
import kotlin.random.Random

// Real Rain/Cafe vocabulary-based Hangul teaching.

enum class JamoWordKind { JAPANESE_PRONUNCIATION, KOREAN_MEANING }

data class JamoWord(
    val sourceId: String,
    val japanese: String,
    val hangul: String,
    val koreanMeaning: String,
    val kind: JamoWordKind,
    val category: String,
    val aliases: List<String>
)

data class JamoWordQuestion(val word: JamoWord, val options: List<JamoWord>)

private fun tidy(value: String): String = value.trim()

// Visible length: whitespace is not counted, so phrases cannot slip in as short.
private fun visibleLength(hangul: String): Int = hangul.count { !it.isWhitespace() }

class JamoWordCatalog(items: List<LearningItem>) {
    val words: List<JamoWord> = buildWords(items)

    // Deterministic study-card allocation, precomputed once per catalogue so the
    // result never depends on the order cards are requested in. Each canonical
    // word (normalizeAnswer across kinds) appears on at most one card, max 2
    // per card; limit=1 is always a prefix of limit=2.
    private val allocation: Map<String, List<JamoWord>> by lazy { allocateExamples(words) }

    fun examples(letter: JamoLetter, limit: Int = 2): List<JamoWord> {
        if (limit <= 0) return emptyList()
        val allocated = allocation[letter.id]
        if (allocated != null) return allocated.take(limit.coerceAtMost(2))
        // Unknown letter id: same rules ad hoc (starts-with + milk reservation).
        val milks = milkNorms(words)
        return eligibleFor(letter, words)
            .filter { normalizeAnswer(tidy(it.hangul)) !in milks || letter.id == MILK_LETTER_ID }
            .sortedWith(milkFirstComparator)
            .take(limit.coerceAtMost(2))
    }

    fun question(
        letter: JamoLetter? = null,
        excludedSourceIds: Set<String> = emptySet(),
        random: Random = Random.Default
    ): JamoWordQuestion? {
        val pool: List<JamoWord> = if (letter != null) {
            val ja = words.filter { it.kind == JamoWordKind.JAPANESE_PRONUNCIATION && startsWithJamo(it.hangul, letter) }
            if (ja.isNotEmpty()) ja else words.filter { it.kind == JamoWordKind.KOREAN_MEANING && startsWithJamo(it.hangul, letter) }
        } else {
            val ja = words.filter { it.kind == JamoWordKind.JAPANESE_PRONUNCIATION }
            if (ja.isNotEmpty()) ja else words.filter { it.kind == JamoWordKind.KOREAN_MEANING }
        }
        if (pool.isEmpty()) return null
        var candidates = pool.filter { it.sourceId !in excludedSourceIds }
        if (candidates.isEmpty()) candidates = pool
        if (letter != null) {
            val exampleIds = examples(letter).map { it.sourceId }.toSet()
            val nonExample = candidates.filter { it.sourceId !in exampleIds }
            if (nonExample.isNotEmpty()) candidates = nonExample
        }
        if (candidates.isEmpty()) return null
        // Try remaining candidate targets if the first cannot form 4 valid options.
        // Last-resort: study examples / excluded targets are tried only after every
        // eligible nonexample alternative fails, so we never return null while a
        // valid fallback exists, but never repeat examples while alternatives exist.
        val primary = candidates.toMutableList()
        for (i in primary.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = primary[i]; primary[i] = primary[j]; primary[j] = tmp
        }
        for (picked in primary) {
            val options = buildOptions(picked, random, letter) ?: continue
            return JamoWordQuestion(picked, options)
        }
        if (letter != null) {
            val primaryIds = primary.map { it.sourceId }.toSet()
            val fallback = pool.filter { it.sourceId !in primaryIds }
            if (fallback.isNotEmpty()) {
                val rest = fallback.toMutableList()
                for (i in rest.size - 1 downTo 1) {
                    val j = random.nextInt(i + 1)
                    val tmp = rest[i]; rest[i] = rest[j]; rest[j] = tmp
                }
                for (picked in rest) {
                    val options = buildOptions(picked, random, letter) ?: continue
                    return JamoWordQuestion(picked, options)
                }
            }
        }
        return null
    }

    private fun buildOptions(correct: JamoWord, random: Random, letter: JamoLetter? = null): List<JamoWord>? {
        // Focused lesson teaches the first Hangul syllable. A later matching syllable
        // (for example 쿠키이 ending in 이 because of a Japanese long vowel) must never
        // turn the word into an ㅇ question.
        if (letter != null && !startsWithJamo(correct.hangul, letter)) return null
        val bannedNorm = (correct.aliases + correct.hangul).map { normalizeAnswer(it.trim()) }.toSet()
        val correctNorm = normalizeAnswer(correct.hangul.trim())
        val correctLen = visibleLength(correct.hangul)
        val seen = mutableSetOf(correctNorm)
        val scored = mutableListOf<Pair<JamoWord, Int>>()
        for (w in words) {
            if (w.kind != correct.kind) continue
            if (w.sourceId == correct.sourceId) continue
            if (w.japanese == correct.japanese) continue
            // Same visible length is mandatory for all four choices; never relaxed.
            if (visibleLength(w.hangul) != correctLen) continue
            if (letter != null && startsWithJamo(tidy(w.hangul), letter)) continue
            val norm = normalizeAnswer(tidy(w.hangul))
            if (norm.isBlank()) continue
            if (norm in bannedNorm || norm in seen) continue
            // Reverse alias collision: candidate aliases must not contain the correct answer.
            if (w.aliases.any { normalizeAnswer(it.trim()) == correctNorm }) continue
            seen.add(norm)
            val dist = characterDistance(norm, correctNorm)
            // Category similarity counts only after same-length eligibility above.
            val catPenalty = if (w.category == correct.category) 0 else 1000
            scored.add(w to (catPenalty + dist * 20))
        }
        if (scored.size < 3) return null
        // Prefer short/clear distractors via distance, then stable id order.
        val sorted = scored.sortedWith(compareBy({ it.second }, { it.first.hangul.length }, { it.first.sourceId }))
        val picked: List<JamoWord> = if (letter != null) {
            sorted.take(3).map { it.first }
        } else {
            // Standalone quiz: same-length distractors, diverse rather than
            // near-identical. Greedily require pairwise distance >= 2, then fill.
            val chosen = mutableListOf<JamoWord>()
            val chosenNorms = mutableListOf<String>()
            for ((w, _) in sorted) {
                if (chosen.size >= 3) break
                val norm = normalizeAnswer(tidy(w.hangul))
                if (chosenNorms.all { characterDistance(it, norm) >= 2 }) {
                    chosen.add(w)
                    chosenNorms.add(norm)
                }
            }
            if (chosen.size < 3) {
                for ((w, _) in sorted) {
                    if (chosen.size >= 3) break
                    if (chosen.none { it.sourceId == w.sourceId }) chosen.add(w)
                }
            }
            chosen
        }
        if (picked.size < 3) return null
        val all = (listOf(correct) + picked).toMutableList()
        for (i in all.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = all[i]; all[i] = all[j]; all[j] = tmp
        }
        return all
    }
}

private const val MILK_LETTER_ID = "c_ㄱ"

private val milkFirstComparator = compareBy<JamoWord>(
    { if (it.japanese == "牛乳") 0 else 1 },
    { it.hangul.length },
    { it.japanese.length },
    { it.sourceId }
)

private fun milkNorms(words: List<JamoWord>): Set<String> = words
    .filter { it.japanese == "牛乳" }
    .map { normalizeAnswer(tidy(it.hangul)) }
    .filter { it.isNotBlank() }
    .toSet()

// Canonical allocation order uses the real lists from JamoLearning.kt so specs
// cannot drift; order-independent via rarest-pools-first below.
private val canonicalLetters: List<JamoLetter> get() = basicConsonants + basicVowels

private fun firstSyllable(hangul: String): Char? =
    hangul.firstOrNull { it.code in 0xAC00..0xD7A3 }

// Eligible study examples and focused quiz targets use the same rule:
// consonant = first syllable onset; vowel = first syllable nucleus.
private fun startsWithSpec(hangul: String, initial: Int?, medial: Int?): Boolean {
    val ch = firstSyllable(hangul) ?: return false
    val s = ch.code - 0xAC00
    val cho = s / 588
    val jung = (s % 588) / 28
    if (initial != null) return cho == initial
    if (medial != null) return jung == medial
    return false
}

private fun eligibleFor(letter: JamoLetter, words: List<JamoWord>): List<JamoWord> {
    val ja = words.filter {
        it.kind == JamoWordKind.JAPANESE_PRONUNCIATION && startsWithJamo(it.hangul, letter)
    }
    if (ja.isNotEmpty()) return ja
    return words.filter {
        it.kind == JamoWordKind.KOREAN_MEANING && startsWithJamo(it.hangul, letter)
    }
}

private fun allocateExamples(words: List<JamoWord>): Map<String, List<JamoWord>> {
    // Per-letter pools (JA preferred, KO fallback); milk norms reserved for ㄱ only.
    val milks = milkNorms(words)
    val pools = mutableMapOf<String, List<JamoWord>>()
    for (letter in canonicalLetters) {
        var pool = eligibleFor(letter, words)
        if (letter.id != MILK_LETTER_ID) {
            pool = pool.filter { normalizeAnswer(tidy(it.hangul)) !in milks }
        }
        pools[letter.id] = pool.sortedWith(milkFirstComparator)
            .distinctBy { normalizeAnswer(tidy(it.hangul)) }
    }
    // Rarest pools first so small pools are not starved by common letters.
    val order = canonicalLetters.sortedWith(
        compareBy({ pools[it.id]?.size ?: 0 }, { it.id })
    )
    val usedNorms = mutableSetOf<String>()
    val result = mutableMapOf<String, List<JamoWord>>()
    for (letter in order) {
        val pool = (pools[letter.id] ?: emptyList()).filter {
            normalizeAnswer(tidy(it.hangul)) !in usedNorms
        }
        val chosen = mutableListOf<JamoWord>()
        if (pool.isNotEmpty()) {
            chosen.add(pool[0])
            if (pool.size > 1) {
                val firstChunks = targetChunks(pool[0].hangul, letter)
                // Target-chunk diversity: prefer a second card with a different
                // target chunk when alternatives remain after global reservation.
                val diverse = pool.drop(1).firstOrNull {
                    targetChunks(it.hangul, letter) != firstChunks
                } ?: pool[1]
                chosen.add(diverse)
            }
        }
        chosen.forEach { usedNorms.add(normalizeAnswer(tidy(it.hangul))) }
        result[letter.id] = chosen
    }
    return result
}

private fun targetChunks(hangul: String, letter: JamoLetter): Set<String> {
    val out = mutableSetOf<String>()
    for (ch in hangul) {
        if (containsJamo(ch.toString(), letter)) out.add(ch.toString())
    }
    return out
}

private fun buildWords(items: List<LearningItem>): List<JamoWord> {
    val catalog = PracticeTranslationCatalog(items)
    val out = mutableListOf<JamoWord>()
    val seenKeys = mutableSetOf<String>()
    for (item in items) {
        val gameOk = item.gameTypes.any { it == "rain" || it == "cafe" }
        if (!gameOk) continue
        val kind = when (item.sourceLanguage) {
            "ja" -> JamoWordKind.JAPANESE_PRONUNCIATION
            "ko" -> JamoWordKind.KOREAN_MEANING
            else -> continue
        }
        val requiredMode = if (kind == JamoWordKind.JAPANESE_PRONUNCIATION) "ja_to_hangul_pronunciation" else "ko_same_hangul"
        if (requiredMode !in item.enabledModes) continue
        val resolved = catalog.resolve(item)
        if (!resolved.isComplete) continue
        val japanese = tidy(resolved.japaneseOriginal ?: continue)
        val hangul = if (kind == JamoWordKind.JAPANESE_PRONUNCIATION) {
            tidy(resolved.hangulPronunciation ?: continue)
        } else {
            item.acceptedAnswers.firstOrNull { it.isNotBlank() }?.trim() ?: continue
        }
        // Teaching catalogue keeps words and tiny expressions only (<=5 visible chars).
        if (visibleLength(hangul) > 5) continue
        val meaning = tidy(resolved.koreanMeaning ?: continue)
        if (japanese.isEmpty() || hangul.isEmpty() || meaning.isEmpty()) continue
        val accepted = item.gameAcceptedAnswers().map { tidy(it) }.filter { it.isNotEmpty() }.distinct()
        if (accepted.isEmpty()) continue
        val hangulNorm = normalizeAnswer(hangul)
        if (hangulNorm.isBlank()) continue
        if (accepted.none { normalizeAnswer(it) == hangulNorm }) continue
        val key = kind.name + " " + japanese + " " + hangulNorm
        if (!seenKeys.add(key)) continue
        out.add(
            JamoWord(
                sourceId = item.id,
                japanese = japanese,
                hangul = hangul,
                koreanMeaning = meaning,
                kind = kind,
                category = item.category,
                aliases = accepted
            )
        )
    }
    return out
}

// Shared fixture helper: example eligibility (first-syllable onset/nucleus).
fun startsWithJamo(word: String, letter: JamoLetter): Boolean {
    if (word.isEmpty()) return false
    return startsWithSpec(word, letter.initialIndex, letter.medialIndex)
}

fun containsJamo(word: String, letter: JamoLetter): Boolean {
    if (word.isEmpty()) return false
    val glyph = letter.glyph
    val initial = letter.initialIndex
    val medial = letter.medialIndex
    for (ch in word) {
        if (ch.toString() == glyph) return true
        val code = ch.code
        if (code in 0xAC00..0xD7A3) {
            val s = code - 0xAC00
            val cho = s / 588
            val jung = (s % 588) / 28
            if (initial != null && cho == initial) return true
            if (medial != null && jung == medial) return true
        }
    }
    return false
}

fun jamoJapaneseReading(letter: JamoLetter): String {
    val guide = jamoSoundGuide(letter)
    return "${guide.kana} (${guide.hangul}) ${guide.noteJa}"
}
