package com.ghtnql.kkkeyboard.sharedcore

/** Maps precomposed Hangul syllables to the app's approximate hiragana reading. */
class HangulKanaTransliterator {
    private data class KanaRow(
        val a: String, val i: String, val u: String, val e: String, val o: String,
        val ya: String, val yu: String, val yo: String,
    )

    private val vowel = KanaRow("あ", "い", "う", "え", "お", "や", "ゆ", "よ")
    private val k = KanaRow("か", "き", "く", "け", "こ", "きゃ", "きゅ", "きょ")
    private val g = KanaRow("が", "ぎ", "ぐ", "げ", "ご", "ぎゃ", "ぎゅ", "ぎょ")
    private val n = KanaRow("な", "に", "ぬ", "ね", "の", "にゃ", "にゅ", "にょ")
    private val d = KanaRow("だ", "でぃ", "どぅ", "で", "ど", "でゃ", "でゅ", "でょ")
    private val t = KanaRow("た", "てぃ", "とぅ", "て", "と", "てゃ", "てゅ", "てょ")
    private val r = KanaRow("ら", "り", "る", "れ", "ろ", "りゃ", "りゅ", "りょ")
    private val m = KanaRow("ま", "み", "む", "め", "も", "みゃ", "みゅ", "みょ")
    private val b = KanaRow("ば", "び", "ぶ", "べ", "ぼ", "びゃ", "びゅ", "びょ")
    private val p = KanaRow("ぱ", "ぴ", "ぷ", "ぺ", "ぽ", "ぴゃ", "ぴゅ", "ぴょ")
    private val s = KanaRow("さ", "し", "す", "せ", "そ", "しゃ", "しゅ", "しょ")
    private val z = KanaRow("ざ", "じ", "ず", "ぜ", "ぞ", "じゃ", "じゅ", "じょ")
    private val j = KanaRow("じゃ", "じ", "じゅ", "じぇ", "じょ", "じゃ", "じゅ", "じょ")
    private val ch = KanaRow("ちゃ", "ち", "ちゅ", "ちぇ", "ちょ", "ちゃ", "ちゅ", "ちょ")
    private val h = KanaRow("は", "ひ", "ふ", "へ", "ほ", "ひゃ", "ひゅ", "ひょ")
    private val v = KanaRow("ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ", "ゔゃ", "ゔゅ", "ゔょ")
    private val onsetRows = arrayOf(g, k, n, d, t, r, m, b, p, s, s, vowel, z, j, ch, k, t, p, h)

    /** Spaces are skipped; any non-space character outside precomposed Hangul rejects the input. */
    fun transliterate(input: String): String? {
        val output = StringBuilder(input.length * 2)
        var hasSyllable = false
        input.forEach { character ->
            if (character == ' ') return@forEach
            if (character !in '\uAC00'..'\uD7A3') return null
            hasSyllable = true
            val offset = character.code - 0xAC00
            val onsetIndex = offset / (21 * 28)
            val vowelIndex = (offset % (21 * 28)) / 28
            output.append(kanaFor(onsetRows[onsetIndex], vowelIndex))
            output.append(finalKana(offset % 28))
        }
        return output.toString().takeIf { hasSyllable && it.isNotEmpty() }
    }

    /**
     * Candidate readings for ambiguous Japanese spellings that Korean pronunciation alone cannot distinguish.
     * Primary literal reading stays first; secondary spellings are explicit selectable candidates only.
     */
    fun transliterateCandidates(input: String): List<String> {
        var paths = listOf("")
        var hasSyllable = false
        input.forEach { character ->
            if (character == ' ') return@forEach
            if (character !in '\uAC00'..'\uD7A3') return emptyList()
            hasSyllable = true
            val offset = character.code - 0xAC00
            val onsetIndex = offset / (21 * 28)
            val vowelIndex = (offset % (21 * 28)) / 28
            val final = finalKana(offset % 28)
            val primary = kanaFor(onsetRows[onsetIndex], vowelIndex) + final
            val pieces = buildList {
                add(primary)
                // ず / づ are phonetic homophones in modern Japanese. Keep ず first.
                if (onsetIndex == 12 && vowelIndex == 18) add("づ$final")
                // Japanese ヴ-series loanword spelling (e.g. エヴァンゲリオン).
                if (onsetIndex == 7) add(kanaFor(v, vowelIndex) + final)
            }.distinct()
            paths = paths.flatMap { prefix -> pieces.map { prefix + it } }.distinct().take(4)
        }
        return if (hasSyllable) paths else emptyList()
    }

    /** Additional pronunciation-equivalent reading; literal transliteration remains first. */
    fun transliterateTolerant(input: String): String? =
        transliterate(JapanesePronunciationMatcher().canonicalize(input))

    private fun kanaFor(row: KanaRow, vowelIndex: Int): String {
        if (row === ch && vowelIndex == 18) return "つ"
        return when (vowelIndex) {
        0 -> row.a
        1 -> row.e
        2 -> row.ya
        3 -> row.i + "ぇ"
        4 -> row.o
        5 -> row.e
        6 -> row.yo
        7 -> row.i + "ぇ"
        8 -> row.o
        9 -> if (row === vowel) "わ" else row.u + "ぁ"
        10, 11, 15 -> row.u + "ぇ"
        12 -> row.yo
        13 -> row.u
        14 -> row.u + "ぉ"
        16 -> row.u + "ぃ"
        17 -> row.yu
        18 -> row.u
        19 -> row.u + "い"
        20 -> row.i
        else -> ""
        }
    }

    private fun finalKana(finalIndex: Int): String = when (finalIndex) {
        0 -> ""
        4, 5, 6, 10, 16, 21 -> "ん"
        8, 9, 11, 12, 13, 14, 15 -> "る"
        else -> "っ"
    }
}
