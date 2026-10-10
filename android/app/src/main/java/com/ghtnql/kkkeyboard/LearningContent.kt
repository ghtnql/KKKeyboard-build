package com.ghtnql.kkkeyboard

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer

enum class PracticeMode(val persistedValue: String) {
    KOREAN_TYPING("ko_same_hangul"),
    JAPANESE_TO_HANGUL("ja_to_hangul_pronunciation"),
}

data class LearningItem(
    val id: String,
    val category: String,
    val difficulty: Int,
    val sourceLanguage: String,
    val sourceText: String,
    val targetLanguage: String,
    val acceptedAnswers: List<String>,
    val meaningHint: String?,
    val enabledModes: Set<String>,
    val gameTypes: Set<String>,
)

object LearningContent {
    private val pronunciationMatcher = com.ghtnql.kkkeyboard.sharedcore.JapanesePronunciationMatcher()

    fun matchesAnswer(item: LearningItem, mode: PracticeMode, answer: String, allowPronunciation: Boolean = true): Boolean =
        item.acceptedAnswers.any {
            val expected = normalizeAnswer(it)
            expected == answer || (allowPronunciation && mode == PracticeMode.JAPANESE_TO_HANGUL &&
                item.sourceLanguage == "ja" && pronunciationMatcher.matches(answer, expected))
        }

    fun matchesPrefix(item: LearningItem, mode: PracticeMode, input: String): Boolean {
        val prefix = normalizeAnswer(input)
        return item.acceptedAnswers.any {
            val expected = normalizeAnswer(it)
            expected.startsWith(prefix) || (mode == PracticeMode.JAPANESE_TO_HANGUL &&
                item.sourceLanguage == "ja" && pronunciationMatcher.matchesPrefix(prefix, expected))
        }
    }

    fun load(context: Context): List<LearningItem> =
        context.assets.open("learning_items.json").bufferedReader().use { parse(it.readText()) }

    fun parse(json: String): List<LearningItem> {
        val rows = JSONArray(json)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val answers = row.getJSONArray("acceptedAnswers")
            LearningItem(
                id = row.getString("id"),
                category = row.getString("category"),
                difficulty = row.getInt("difficulty"),
                sourceLanguage = row.getString("sourceLanguage"),
                sourceText = row.getString("sourceText"),
                targetLanguage = row.getString("targetLanguage"),
                acceptedAnswers = (0 until answers.length()).map(answers::getString),
                meaningHint = row.optString("meaningHint").takeIf(String::isNotBlank),
                enabledModes = row.getJSONArray("enabledModes").toStringSet(),
                gameTypes = row.getJSONArray("gameTypes").toStringSet(),
            ).also { require(it.acceptedAnswers.isNotEmpty()) { "${it.id} has no accepted answers" } }
        }.also { items -> require(items.map(LearningItem::id).distinct().size == items.size) { "Duplicate learning item id" } }
    }

    fun forMode(items: List<LearningItem>, mode: PracticeMode, gameType: String): List<LearningItem> =
        items.filter { mode.persistedValue in it.enabledModes && gameType in it.gameTypes }

    fun normalizeAnswer(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC)

    private fun JSONArray.toStringSet(): Set<String> =
        (0 until length()).map(::getString).toSet()
}
