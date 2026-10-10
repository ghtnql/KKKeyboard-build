package com.ghtnql.kkkeyboard

/** Local Hangul-pronunciation -> Japanese candidate provider. */
object JapaneseTransliterator {
    private val kanaTransliterator = com.ghtnql.kkkeyboard.sharedcore.HangulKanaTransliterator()

    private val dictionary by lazy { JapaneseDictionary.bundled() }

    private val lexicon by lazy {
        val stream = requireNotNull(JapaneseTransliterator::class.java.getResourceAsStream("/ja_lexicon.tsv"))
        stream.bufferedReader(Charsets.UTF_8).use {
            com.ghtnql.kkkeyboard.sharedcore.JapaneseKanaLexicon(it.readText())
        }
    }

    const val maxInputLength: Int = 64

    fun prepare() { dictionary; lexicon }

    fun candidates(inputHangul: String): List<String> = candidatesExact(inputHangul.trim())

    fun candidatesExact(inputHangul: String): List<String> {
        val input = normalized(inputHangul) ?: return emptyList()
        val exact = dictionary.exact(input)
        if (exact.isNotEmpty()) return exact.take(JapaneseDictionary.MAX_CANDIDATES)
        transliterate(input) ?: return emptyList()
        val whole = if (input.length == 1 || input.length <= 2 && dictionary.completions(input).isNotEmpty()) emptyList()
            else generalConversions(input, exactOnly = true)
        // A known full word can commit directly; sentence guesses remain explicit choices.
        val ordered = if (whole.isNotEmpty()) whole + readings(input)
            else segmented(input) + readings(input) + generalConversions(input)
        return ordered
            .distinct().take(JapaneseDictionary.MAX_CANDIDATES)
    }

    /** Predictions are explicit choices; space/enter still use candidatesExact. */
    fun suggestionsExact(inputHangul: String): List<String> {
        val input = normalized(inputHangul) ?: return emptyList()
        val exact = dictionary.exact(input)
        val segmented = if (exact.isEmpty()) segmented(input) else emptyList()
        val predictions = dictionary.completions(input)
        val literal = readings(input)
        if (input.length == 1 && exact.isEmpty() && predictions.isEmpty())
            return (literal + generalConversions(input)).distinct().take(JapaneseDictionary.MAX_CANDIDATES)
        val general = generalConversions(input)
        if (exact.isEmpty() && predictions.isEmpty() && segmented.isEmpty() && generalConversions(input, exactOnly = true).isEmpty())
            return (literal + general).distinct().take(JapaneseDictionary.MAX_CANDIDATES)
        val preferred = (exact + predictions.take(3) + segmented + general).distinct()
        val tail = literal.filter { it !in preferred }.take(4)
        // Keep the literal reading selectable even when there are many predictions.
        return preferred.take(JapaneseDictionary.MAX_CANDIDATES - tail.size) + tail
    }

    private fun generalConversions(input: String, exactOnly: Boolean = false): List<String> {
        val kana = (kanaTransliterator.transliterateCandidates(input) + listOfNotNull(kanaTransliterator.transliterateTolerant(input))).distinct()
        for (reading in kana) {
            val result = if (exactOnly) lexicon.exactCandidates(reading) else lexicon.candidates(reading)
            if (exactOnly || result.isNotEmpty()) return result
        }
        return emptyList()
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
        (kanaTransliterator.transliterateCandidates(input) +
            listOfNotNull(kanaTransliterator.transliterateTolerant(input)))
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
