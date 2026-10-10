package com.ghtnql.kkkeyboard.sharedcore

/**
 * Kana-to-kanji lexicon backed by a runtime TSV resource.
 *
 * TSV format: `reading TAB surface TAB integer cost NEWLINE`, sorted by reading
 * (max 8 rows per reading, max dictionary reading length 24 chars).
 *
 * Memory: retains the [tsv] String plus an [IntArray] of row start offsets and
 * performs binary search for exact lookup. No per-entry maps or objects are kept.
 *
 * Common-Kotlin only (no JVM APIs) so the class exports to Swift.
 */
class JapaneseKanaLexicon(tsv: String) {
    private val data: String = tsv
    private val rowOffsets: IntArray

    init {
        if (data.isEmpty()) {
            rowOffsets = IntArray(0)
        } else {
            // Count rows first so the offset table is filled directly with no
            // per-entry boxed allocations.
            var count = 1
            var idx = data.indexOf('\n')
            while (idx >= 0) {
                val next = idx + 1
                if (next < data.length) count++
                idx = data.indexOf('\n', next)
            }
            val offsets = IntArray(count)
            offsets[0] = 0
            var filled = 1
            idx = data.indexOf('\n')
            while (idx >= 0) {
                val next = idx + 1
                if (next < data.length) {
                    offsets[filled] = next
                    filled++
                }
                idx = data.indexOf('\n', next)
            }
            rowOffsets = offsets
        }
    }

    private data class TokenOption(val surface: String, val cost: Int, val hasSubstitution: Boolean)

    private fun lineEnd(rowStart: Int): Int {
        val nl = data.indexOf('\n', rowStart)
        return if (nl < 0) data.length else nl
    }

    /** Compares the reading column of the row at [rowStart] with [target]. */
    private fun compareReadingAt(rowStart: Int, target: String): Int {
        val end = lineEnd(rowStart)
        var tab = rowStart
        while (tab < end && data[tab] != '\t') tab++
        val readingLen = tab - rowStart
        val common = minOf(readingLen, target.length)
        for (i in 0 until common) {
            val a = data[rowStart + i]
            val b = target[i]
            if (a != b) return if (a < b) -1 else 1
        }
        return when {
            readingLen < target.length -> -1
            readingLen > target.length -> 1
            else -> 0
        }
    }

    private fun readingEqualsAt(rowStart: Int, target: String): Boolean {
        val end = lineEnd(rowStart)
        var tab = rowStart
        while (tab < end && data[tab] != '\t') tab++
        if (tab - rowStart != target.length) return false
        for (i in target.indices) if (data[rowStart + i] != target[i]) return false
        return true
    }

    /** Lower-bound binary search: first row whose reading is >= [target]. */
    private fun lowerBound(target: String): Int {
        var lo = 0
        var hi = rowOffsets.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareReadingAt(rowOffsets[mid], target) < 0) lo = mid + 1
            else hi = mid
        }
        return lo
    }

    private fun surfaceAndCostAt(rowStart: Int, reading: String): TokenOption {
        val end = lineEnd(rowStart)
        var tab1 = rowStart
        while (tab1 < end && data[tab1] != '\t') tab1++
        var tab2 = tab1 + 1
        while (tab2 < end && data[tab2] != '\t') tab2++
        val surface = if (tab1 + 1 <= tab2) data.substring(tab1 + 1, tab2) else ""
        var cost = 0
        var i = tab2 + 1
        var negative = false
        if (i < end && (data[i] == '-' || data[i] == '+')) {
            negative = data[i] == '-'
            i++
        }
        while (i < end) {
            val c = data[i]
            if (c < '0' || c > '9') break
            cost = cost * 10 + (c - '0')
            i++
        }
        return TokenOption(surface, if (negative) -cost else cost, surface != reading)
    }

    /**
     * Dictionary options for [reading] in source (cost) order, max 8.
     * Unchanged kana entries (surface == reading, e.g. particles/verb endings
     * in the real Mozc TSV) are retained to support complete known-token paths;
     * callers exclude an unchanged whole reading from emitted output.
     */
    private fun dictionaryOptions(reading: String): List<TokenOption> {
        if (rowOffsets.isEmpty()) return emptyList()
        val result = ArrayList<TokenOption>(8)
        var row = lowerBound(reading)
        while (row < rowOffsets.size && result.size < MAX_PER_READING) {
            val start = rowOffsets[row]
            if (!readingEqualsAt(start, reading)) break
            val option = surfaceAndCostAt(start, reading)
            if (option.surface.isNotEmpty()) result.add(option)
            row++
        }
        return result
    }

    // Hangul pronunciation may spell Japanese long vowels as おお/ょお.
    // Only retry a shortened key when the original has no dictionary rows.
    // Input text and gemination (っ) are never rewritten by this lookup.
    private fun lookupOptions(reading: String): List<TokenOption> {
        val raw = dictionaryOptions(reading)
        if (raw.isNotEmpty()) return raw
        val shortened = StringBuilder(reading.length)
        for (c in reading) {
            val previous = shortened.lastOrNull()
            val vowel = when (previous ?: '\u0000') {
                in "あかがさざただなはばぱまゃやらわぁ" -> 'あ'
                in "いきぎしじちぢにひびぴみりぃ" -> 'い'
                in "うくぐすずつづぬふぶぷむゅゆるぅ" -> 'う'
                in "えけげせぜてでねへべぺめれぇ" -> 'え'
                in "おこごそぞとどのほぼぽもょよろをぉ" -> 'お'
                else -> null
            }
            val extendsVowel = c == vowel || (vowel == 'お' && c == 'う') || (vowel == 'え' && c == 'い')
            if (!extendsVowel) shortened.append(c)
        }
        val key = shortened.toString()
        return if (key == reading || key.isEmpty()) emptyList() else dictionaryOptions(key).map {
            it.copy(hasSubstitution = it.surface != reading)
        }
    }

    fun isValidReading(reading: String): Boolean {
        if (reading.isEmpty() || reading.length > MAX_READING_LENGTH) return false
        for (c in reading) {
            if (!isKanaChar(c)) return false
        }
        return true
    }

    /** Whole dictionary words only, in source order; never segmented guesses. */
    fun exactCandidates(reading: String): List<String> {
        if (!isValidReading(reading)) return emptyList()
        return lookupOptions(reading).filter { it.surface != reading }
            .map { it.surface }.distinct().take(MAX_CANDIDATES)
    }

    /**
     * Up to 8 whole-reading conversion candidates. Exact dictionary words use
     * source cost order. Otherwise a bounded beam composes sentence candidates.
     * Empty, over-128-char, or invalid (non-kana) readings yield an empty list.
     */
    fun candidates(reading: String): List<String> {
        val exact = exactCandidates(reading)
        if (exact.isNotEmpty()) return exact
        if (!isValidReading(reading) || reading.length == 1) return emptyList()
        return segmentedCandidates(reading)
    }

    private data class Path(val text: String, val cost: Long, val hasSubstitution: Boolean)

    /**
     * Bounded dynamic-programming beam over token segmentations of [reading].
     * Dictionary tokens carry their TSV cost plus a small per-token penalty (so a
     * single longer word beats an unrelated split of short nouns); known functional kana
     * tokens carry a smaller cost. Every input character must belong to a known
     * dictionary or functional token; incomplete paths produce no conversion. Only paths containing at least
     * one dictionary substitution and differing from [reading] are emitted.
     */
    private fun segmentedCandidates(reading: String): List<String> {
        val n = reading.length
        val beams = Array(n + 1) { mutableListOf<Path>() }
        beams[0].add(Path("", 0L, false))
        for (i in 0 until n) {
            val current = beams[i]
            if (current.isEmpty()) continue
            val maxLen = minOf(MAX_TOKEN_LENGTH, n - i)
            // Functional spans preserve their kana; unknown spans do not form paths.
            for (len in 1..maxLen) {
                val literal = reading.substring(i, i + len)
                if (!FUNCTION_WORDS.contains(literal)) continue
                val penalty = FUNCTION_WORD_COST
                for (path in current) {
                    addPath(beams[i + len], Path(path.text + literal, path.cost + penalty, path.hasSubstitution))
                }
            }
            // Pronounced topic/object/direction particles from Hangul are わ/お/え.
            // Correct their spelling only after an actual dictionary substitution.
            val pronouncedParticle = when (reading[i]) {
                'わ' -> "は"
                'お' -> "を"
                'え' -> "へ"
                else -> null
            }
            if (pronouncedParticle != null) {
                for (path in current) if (path.hasSubstitution) {
                    addPath(beams[i + 1], Path(path.text + pronouncedParticle, path.cost + PRONOUNCED_PARTICLE_COST, true))
                }
            }
            // Dictionary tokens.
            for (len in 1..maxLen) {
                val key = reading.substring(i, i + len)
                val found = lookupOptions(key)
                if (found.isEmpty()) continue
                // Low-cost unchanged verb stems otherwise consume the finite beam,
                // hiding useful conversions such as 行き in a known sentence.
                val converted = if (key in FUNCTION_WORDS) emptyList() else found.filter { it.hasSubstitution }
                val options = if (converted.isNotEmpty()) converted else found
                for (option in options) {
                    // Single kana homophones otherwise manufacture nouns from unknown text.
                    if (len == 1 && option.hasSubstitution) continue
                    for (path in current) {
                        addPath(
                            beams[i + len],
                            Path(
                                path.text + option.surface,
                                path.cost + option.cost + TOKEN_PENALTY,
                                path.hasSubstitution || option.hasSubstitution
                            )
                        )
                    }
                }
            }
            // Keep only live beams adjacent to the frontier to bound memory.
            if (i > 0) beams[i - 1].clear()
        }
        val finals = beams[n].sortedBy { it.cost }
        val out = ArrayList<String>(MAX_CANDIDATES)
        for (path in finals) {
            if (out.size >= MAX_CANDIDATES) break
            if (!path.hasSubstitution) continue
            if (path.text == reading) continue
            if (!out.contains(path.text)) out.add(path.text)
        }
        return out
    }

    private fun addPath(beam: MutableList<Path>, candidate: Path) {
        for (k in beam.indices) {
            if (beam[k].text == candidate.text) {
                if (candidate.cost < beam[k].cost) beam[k] = candidate
                return
            }
        }
        beam.add(candidate)
        if (beam.size > BEAM_WIDTH) {
            beam.sortBy { it.cost }
            while (beam.size > BEAM_WIDTH) beam.removeAt(beam.size - 1)
        }
    }

    companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_PER_READING = 8
        const val MAX_TOKEN_LENGTH = 24
        const val MAX_READING_LENGTH = 128

        /** Penalize extra lexical tokens to prefer whole words over noun splits. */
        private const val TOKEN_PENALTY = 3_000L

        private const val FUNCTION_WORD_COST = 750L

        // Prefer consuming a dictionary long vowel over inserting a particle.
        private const val PRONOUNCED_PARTICLE_COST = 100L

        private const val BEAM_WIDTH = 8

        private val FUNCTION_WORDS: Set<String> = setOf(
            "は", "が", "を", "に", "へ", "と", "の", "も", "で",
            "です", "ます", "ません", "でした", "ました", "ませんでした",
            "します", "しました", "したい", "たい", "から", "まで", "て", "た"
        )

        fun isKanaChar(c: Char): Boolean =
            (c in 'ぁ'..'ゖ') || c == 'ー' || c == 'ゝ' || c == 'ゞ'
    }
}
