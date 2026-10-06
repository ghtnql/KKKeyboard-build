package com.ghtnql.kkkeyboard.sharedui

/**
 * Display strings for a [LearningItem] in practice UI.
 *
 * All fields are nullable: missing translations stay null, never fabricated.
 * Whitespace around real values is trimmed; inner content is retained as-is.
 */
data class PracticeTranslations(
    val koreanMeaning: String?,
    val hangulPronunciation: String?,
    val japaneseOriginal: String?,
) {
    val isComplete: Boolean
        get() = !koreanMeaning.isNullOrBlank() &&
            !hangulPronunciation.isNullOrBlank() &&
            !japaneseOriginal.isNullOrBlank()
}

private const val KOREAN_HINT_PREFIX = "뜻:"
private const val JAPANESE_HINT_PREFIX = "일본어:"

private fun nonBlank(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    return trimmed.takeIf { it.isNotEmpty() }
}

/** Strips [prefix] when present (plus surrounding whitespace); otherwise keeps the trimmed content. */
private fun stripPrefixLenient(hint: String?, prefix: String): String? {
    val trimmed = hint?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith(prefix)) return trimmed
    return trimmed.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
}

private fun firstNonBlank(values: List<String>): String? {
    for (value in values) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) return trimmed
    }
    return null
}

/**
 * Prebuilt index over [items] for display resolution.
 *
 * Japanese lookup is by exact [LearningItem.sourceText] equality only;
 * Korean meanings are never used as a lookup key. When several Japanese
 * entries share one Korean meaning but differ in Japanese, the first
 * entry in [items] order wins.
 */
class PracticeTranslationCatalog(items: List<LearningItem>) {
    private val japaneseBySourceText: Map<String, LearningItem> = buildMap {
        for (item in items) {
            if (item.sourceLanguage != "ja") continue
            val key = item.sourceText.trim()
            if (key.isEmpty() || containsKey(key)) continue
            put(key, item)
        }
    }

    private fun pronunciationOf(entry: LearningItem): String? =
        nonBlank(entry.japaneseHangulPronunciation) ?: firstNonBlank(entry.acceptedAnswers)

    fun resolve(item: LearningItem): PracticeTranslations {
        val isJapanese = item.sourceLanguage == "ja"

        val japaneseOriginal = nonBlank(item.japaneseText)
            ?: if (isJapanese) {
                nonBlank(item.sourceText)
            } else {
                stripPrefixLenient(item.meaningHint, JAPANESE_HINT_PREFIX)
            }

        val koreanMeaning = nonBlank(item.koreanText)
            ?: if (isJapanese) {
                stripPrefixLenient(item.meaningHint, KOREAN_HINT_PREFIX)
            } else {
                nonBlank(item.sourceText)
            }

        val directPronunciation = nonBlank(item.japaneseHangulPronunciation)
            // Hangul aliases are valid phonetics only for Japanese items;
            // a Korean item's own answers must never be reused as phonetics.
            ?: if (isJapanese) firstNonBlank(item.acceptedAnswers) else null
        val hangulPronunciation = directPronunciation
            ?: japaneseOriginal?.let { japaneseBySourceText[it.trim()] }?.let { pronunciationOf(it) }

        return PracticeTranslations(
            koreanMeaning = koreanMeaning,
            hangulPronunciation = hangulPronunciation,
            japaneseOriginal = japaneseOriginal,
        )
    }
}
