package com.ghtnql.kkkeyboard

/** Local Hangul-pronunciation -> Japanese candidate provider. */
object JapaneseTransliterator {
    private val kanaTransliterator = com.ghtnql.kkkeyboard.sharedcore.HangulKanaTransliterator()

    private val dictionary by lazy { JapaneseDictionary.bundled() }

    const val maxInputLength: Int = 64

    fun prepare() { dictionary }

    fun candidates(inputHangul: String): List<String> = candidatesExact(inputHangul.trim())

    fun candidatesExact(inputHangul: String): List<String> {
        val input = normalized(inputHangul) ?: return emptyList()
        val exact = dictionary.exact(input)
        if (exact.isNotEmpty()) return exact.take(JapaneseDictionary.MAX_CANDIDATES)
        transliterate(input) ?: return emptyList()
        return (segmented(input) + readings(input))
            .distinct().take(JapaneseDictionary.MAX_CANDIDATES)
    }

    /** Predictions are explicit choices; space/enter still use candidatesExact. */
    fun suggestionsExact(inputHangul: String): List<String> {
        val input = normalized(inputHangul) ?: return emptyList()
        val exact = dictionary.exact(input)
        val segmented = if (exact.isEmpty()) segmented(input) else emptyList()
        val predictions = dictionary.completions(input)
        val literal = readings(input)
        val preferred = (exact + predictions.take(3) + segmented).distinct()
        val tail = literal.filter { it !in preferred }
        // Keep the literal reading selectable even when there are many predictions.
        return preferred.take(JapaneseDictionary.MAX_CANDIDATES - tail.size) + tail
    }

    private fun normalized(input: String): String? {
        if (input.isEmpty() || input.length > maxInputLength || input.first() == ' ' || input.last() == ' ') return null
        if (!input.all { it == ' ' || it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u314E' }) return null
        return input.replace(" ", "")
    }

    private fun segmented(input: String): List<String> {
        var offset = 0
        var usedDictionary = false
        var paths = listOf("")
        while (offset < input.length) {
            val match = dictionary.longestMatch(input, offset)
            val pieces: List<String>
            if (match != null) {
                pieces = match.surfaces
                offset += match.length
                usedDictionary = true
            } else {
                val kana = transliterate(input[offset].toString()) ?: return emptyList()
                pieces = listOf(kana)
                offset++
            }
            paths = paths.flatMap { prefix -> pieces.map { prefix + it } }
                .distinct().take(3)
        }
        return if (usedDictionary) paths else emptyList()
    }

    private fun readings(input: String): List<String> =
        listOfNotNull(transliterate(input), kanaTransliterator.transliterateTolerant(input))
            .distinct().flatMap { listOf(it, it.toKatakana()) }

    private fun transliterate(input: String): String? = kanaTransliterator.transliterate(input)

    private fun String.toKatakana(): String = buildString(length) {
        this@toKatakana.forEach { character ->
            append(
                if (character in '\u3041'..'\u3096') (character.code + 0x60).toChar()
                else character,
            )
        }
    }
}
